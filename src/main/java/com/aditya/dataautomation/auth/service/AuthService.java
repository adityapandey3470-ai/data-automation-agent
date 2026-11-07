package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.auth.dto.*;
import com.aditya.dataautomation.auth.entity.EmailVerificationToken;
import com.aditya.dataautomation.auth.entity.PasswordResetToken;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.EmailVerificationTokenRepository;
import com.aditya.dataautomation.auth.repository.PasswordResetTokenRepository;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.config.properties.AuthProperties;
import com.aditya.dataautomation.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$w8k2rZ0/J3XlEwY6hVqOu.8kS6U6l2Zk2h8X0lEwY6hVqOu8kS6U6";

    private static class FailedLoginTracker {
        int attempts;
        long lockedUntil;
        long firstAttemptTime;
    }

    private final Map<String, FailedLoginTracker> failedLogins = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L;
    private static final int MAX_TRACKED_EMAILS = 50_000;

    private void recordFailedLogin(String email) {
        long now = System.currentTimeMillis();
        if (failedLogins.size() >= MAX_TRACKED_EMAILS && !failedLogins.containsKey(email)) {
            return; // memory guard against floods of random emails
        }
        failedLogins.compute(email, (k, v) -> {
            if (v == null || (now - v.firstAttemptTime > LOCKOUT_DURATION_MS)) {
                FailedLoginTracker t = new FailedLoginTracker();
                t.attempts = 1;
                t.firstAttemptTime = now;
                return t;
            }
            v.attempts++;
            if (v.attempts >= MAX_FAILED_ATTEMPTS) {
                v.lockedUntil = now + LOCKOUT_DURATION_MS;
                log.warn("Account temporarily locked for email '{}' due to {} failed attempts", email, v.attempts);
                if (emailService.isSmtpConfigured()) {
                    emailService.sendAccountLockoutAlert(email);
                }
            }
            return v;
        });
    }

    /** Removes expired lockout entries so the map cannot grow forever. */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300_000)
    public void cleanupFailedLogins() {
        long now = System.currentTimeMillis();
        failedLogins.entrySet().removeIf(e ->
                e.getValue().lockedUntil < now && now - e.getValue().firstAttemptTime > LOCKOUT_DURATION_MS);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // same CPU cost as the "new account" path, so response time does not reveal registered emails
            passwordEncoder.matches(request.getPassword() != null ? request.getPassword() : "x", DUMMY_BCRYPT_HASH);
            Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
            if (existing.isPresent() && emailService.isSmtpConfigured()) {
                emailService.sendAccountAlreadyExistsEmail(email, existing.get().getName());
            }
            return AuthResponse.builder()
                    .token(null)
                    .tokenType("Bearer")
                    .user(null)
                    .emailVerificationRequired(true)
                    .message("We sent a verification link to " + email + ". Please check your inbox.")
                    .smtpConfigured(emailService.isSmtpConfigured())
                    .build();
        }

        if (request.getPassword() == null || request.getPassword().length() < 8 || !request.getPassword().matches(".*[A-Za-z].*") || !request.getPassword().matches(".*[0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new InvalidRequestException("Password must be at least 8 characters long and contain both letters and numbers/special characters");
        }

        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("Passwords do not match");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName().trim())
                .role("ROLE_USER")
                .enabled(true)
                .emailVerified(false)
                .build();

        user = userRepository.save(user);
        log.info("New user registered (pending email verification): id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .verified(false)
                .build();
        emailVerificationTokenRepository.save(verificationToken);

        String verificationLink = authProperties.getFrontendUrl() + "/?verifyToken=" + rawToken;
        boolean smtpConfigured = emailService.isSmtpConfigured();

        if (smtpConfigured) {
            emailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationLink);
        } else {
            log.info("SMTP host not configured (app.mail.host). Email verification token generated for {}", user.getEmail());
        }

        return AuthResponse.builder()
                .token(null)
                .tokenType("Bearer")
                .user(toDto(user))
                .emailVerificationRequired(true)
                .message("We sent a verification link to " + user.getEmail() + ". Please click the link to activate your account.")
                .smtpConfigured(smtpConfigured)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        FailedLoginTracker tracker = failedLogins.get(email);
        long now = System.currentTimeMillis();
        if (tracker != null && tracker.lockedUntil > now) {
            long remainingMinutes = Math.max(1, (tracker.lockedUntil - now) / 60000);
            throw new InvalidRequestException("Account is temporarily locked due to too many failed login attempts. Please try again in " + remainingMinutes + " minute(s) or reset your password.");
        }

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
            recordFailedLogin(email);
            throw new InvalidRequestException("Invalid email or password");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(email);
            throw new InvalidRequestException("Invalid email or password");
        }

        failedLogins.remove(email);

        if (!user.isEnabled()) {
            throw new InvalidRequestException("Account has been disabled by administrator. Please contact support.");
        }

        if (!user.isEmailVerified()) {
            throw new InvalidRequestException("Please verify your email address before logging in. Check your inbox for the activation link or click 'Resend Verification'.");
        }

        log.info("User logged in: id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(toDto(user))
                .emailVerificationRequired(false)
                .build();
    }

    @Transactional
    public AuthResponse verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRequestException("Verification token is required");
        }

        String tokenHash = hashToken(rawToken.trim());
        int updated = emailVerificationTokenRepository.markVerificationTokenUsedAtomic(tokenHash, LocalDateTime.now());
        if (updated != 1) {
            throw new InvalidRequestException("This verification link is invalid, expired, or has already been used.");
        }

        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired verification link. Please request a new verification email."));

        User user = token.getUser();
        user.setEmailVerified(true);
        user.setEnabled(true);
        userRepository.save(user);

        log.info("Email verified successfully: user_id={}, email={}", user.getId(), user.getEmail());

        return AuthResponse.builder()
                .token(null)
                .tokenType("Bearer")
                .user(toDto(user))
                .emailVerificationRequired(false)
                .message("Email verified successfully. Please log in with your credentials.")
                .build();
    }

    @Transactional
    public AuthResponse resendVerification(String emailStr) {
        String email = emailStr.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);
        boolean smtpConfigured = emailService.isSmtpConfigured();

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.isEmailVerified()) {
                return AuthResponse.builder()
                        .emailVerificationRequired(false)
                        .message("This email address is already verified. You can log in directly.")
                        .smtpConfigured(smtpConfigured)
                        .build();
            }

            emailVerificationTokenRepository.deleteByUserId(user.getId());

            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            String tokenHash = hashToken(rawToken);

            EmailVerificationToken token = EmailVerificationToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusHours(24))
                    .verified(false)
                    .build();
            emailVerificationTokenRepository.save(token);

            String verificationLink = authProperties.getFrontendUrl() + "/?verifyToken=" + rawToken;
            if (smtpConfigured) {
                emailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationLink);
            } else {
                log.info("SMTP host not configured (app.mail.host). Verification link for {}: {}",
                        user.getEmail(), verificationLink);
            }
        }

        return AuthResponse.builder()
                .emailVerificationRequired(true)
                .message("If an unverified account exists with that email, a new verification link has been sent.")
                .smtpConfigured(smtpConfigured)
                .build();
    }

    @Transactional
    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        boolean smtpConfigured = emailService.isSmtpConfigured();
        String devResetToken = null;

        if (userOpt.isPresent() && userOpt.get().isEnabled()) {
            User user = userOpt.get();

            passwordResetTokenRepository.deleteByUserId(user.getId());

            byte[] randomBytes = new byte[32];
            secureRandom.nextBytes(randomBytes);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            String tokenHash = hashToken(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(LocalDateTime.now().plusMinutes(15))
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(resetToken);

            String resetLink = authProperties.getFrontendUrl() + "/?resetToken=" + rawToken;

            if (smtpConfigured) {
                emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
            } else if (authProperties.isExposeDevToken()) {

                devResetToken = rawToken;
            }
        }

        return ForgotPasswordResponse.builder()
                .message("If an account exists with that email, a password reset link has been sent.")
                .smtpConfigured(smtpConfigured)
                .emailDeliveryStatus(smtpConfigured ? "SENT" : "SMTP_NOT_CONFIGURED")
                .smtpInstructions(smtpConfigured ? null : "SMTP is not configured in this environment. Set SMTP_HOST and SMTP_PORT to deliver reset emails.")
                .devResetToken(devResetToken)
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8 || !request.getNewPassword().matches(".*[A-Za-z].*") || !request.getNewPassword().matches(".*[0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new InvalidRequestException("Password must be at least 8 characters long and contain both letters and numbers/special characters");
        }

        if (request.getConfirmPassword() != null && !request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("Passwords do not match");
        }

        String tokenHash = hashToken(request.getToken().trim());
        int updated = passwordResetTokenRepository.markTokenUsedAtomic(tokenHash, LocalDateTime.now());
        if (updated != 1) {
            throw new InvalidRequestException("Invalid or expired password reset token");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidRequestException("Invalid or expired password reset token"));

        User user = resetToken.getUser();
        if (!user.isEnabled()) {
            throw new InvalidRequestException("Account is disabled. Please contact support.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTokensValidAfter(java.time.Instant.now().getEpochSecond()); // invalidates every earlier JWT
        userRepository.save(user);

        failedLogins.remove(user.getEmail().toLowerCase());

        log.info("Password successfully reset and all prior sessions revoked for user id={}", user.getId());
    }

    @Transactional
    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        if (token == null || token.isBlank()) {
            return;
        }
        Long userId = jwtService.extractUserId(token);
        if (userId != null) {
            // logs the user out on every device: all tokens issued up to now stop working
            userRepository.updateTokensValidAfter(userId, java.time.Instant.now().getEpochSecond());
        }
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("User not found"));
        return toDto(user);
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .subscriptionTier(user.getSubscriptionTier() != null ? user.getSubscriptionTier() : "FREE")
                .aiQueriesUsed(user.getAiQueriesUsed() != null ? user.getAiQueriesUsed() : 0)
                .aiQueriesLimit(user.getAiQueriesLimit() != null ? user.getAiQueriesLimit() : 10)
                .totalTokensUsed(user.getTotalTokensUsed() != null ? user.getTotalTokensUsed() : 0L)
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
