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
import com.aditya.dataautomation.exception.AuthenticationFailedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.CompletableFuture;

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
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$w8k2rZ0/J3XlEwY6hVqOu.8kS6U6l2Zk2h8X0lEwY6hVqOu8kS6U6";

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        // validate BEFORE the existence check so the error does not reveal whether the email is registered
        validatePasswordPolicy(request.getPassword());
        if (request.getConfirmPassword() != null && !request.getPassword().equals(request.getConfirmPassword())) {
            throw new InvalidRequestException("Passwords do not match");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            // same CPU cost as the "new account" path, so response time does not reveal registered emails
            passwordEncoder.matches(request.getPassword() != null ? request.getPassword() : "x", DUMMY_BCRYPT_HASH);
            Optional<User> existing = userRepository.findByEmailIgnoreCase(email);
            if (existing.isPresent() && emailService.isSmtpConfigured()) {
                final String existingName = existing.get().getName();
                afterCommitAsync(() -> emailService.sendAccountAlreadyExistsEmail(email, existingName));
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
            final String mailTo = user.getEmail();
            final String mailName = user.getName();
            afterCommitAsync(() -> emailService.sendVerificationEmail(mailTo, mailName, verificationLink));
        } else {
            log.info("SMTP host not configured (app.mail.host). Email verification token generated for {}", user.getEmail());
        }

        return AuthResponse.builder()
                .token(null)
                .tokenType("Bearer")
                .user(null)
                .emailVerificationRequired(true)
                .message("We sent a verification link to " + email + ". Please check your inbox.")
                .smtpConfigured(smtpConfigured)
                .build();
    }

    /** Same response as the "email already registered" branch; used when a concurrent signup hits the unique index. */
    public AuthResponse genericSignupResponse(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        return AuthResponse.builder()
                .token(null)
                .tokenType("Bearer")
                .user(null)
                .emailVerificationRequired(true)
                .message("We sent a verification link to " + email + ". Please check your inbox.")
                .smtpConfigured(emailService.isSmtpConfigured())
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        return login(request, "127.0.0.1");
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        String email = request.getEmail().trim().toLowerCase();
        loginAttemptService.checkLockout(email, clientIp);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(email);

        if (userOpt.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), DUMMY_BCRYPT_HASH);
            boolean locked = loginAttemptService.recordFailure(email, clientIp);
            if (locked && emailService.isSmtpConfigured()) {
                emailService.sendAccountLockoutAlert(email);
            }
            throw new AuthenticationFailedException("Invalid email or password");
        }

        User user = userOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            boolean locked = loginAttemptService.recordFailure(email, clientIp);
            if (locked && emailService.isSmtpConfigured()) {
                emailService.sendAccountLockoutAlert(email);
            }
            throw new AuthenticationFailedException("Invalid email or password");
        }

        loginAttemptService.recordSuccess(email, clientIp);

        if (!user.isEnabled()) {
            throw new InvalidRequestException("Account has been disabled by administrator. Please contact support.");
        }

        if (!user.isEmailVerified()) {
            throw new InvalidRequestException("Please verify your email address before logging in. Check your inbox for the activation link or click 'Resend Verification'.");
        }

        log.info("User logged in: id={}, email={}, role={}", user.getId(), user.getEmail(), user.getRole());
        return buildSessionResponse(user);
    }

    public AuthResponse buildSessionResponse(User user) {
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
        // do NOT touch "enabled": an admin-disabled account must stay disabled
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
                afterCommitAsync(() -> emailService.sendVerificationEmail(user.getEmail(), user.getName(), verificationLink));
            } else if (authProperties.isExposeDevToken()) {
                log.info("SMTP host not configured (app.mail.host). Verification link for {}: {}",
                        user.getEmail(), verificationLink);
            } else {
                log.info("SMTP host not configured (app.mail.host). Verification token generated for {}", user.getEmail());
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
                afterCommitAsync(() -> emailService.sendPasswordResetEmail(user.getEmail(), resetLink));
            } else if (authProperties.isExposeDevToken()) {

                devResetToken = rawToken;
            }
        }

        return ForgotPasswordResponse.builder()
                .message("If an account exists with that email, a password reset link has been sent.")
                // outside dev mode always report "configured" so the UI never shows the dev-only SMTP notice and no infra detail leaks
                .smtpConfigured(authProperties.isExposeDevToken() ? smtpConfigured : true)
                .emailDeliveryStatus("ACCEPTED")
                .smtpInstructions(authProperties.isExposeDevToken() && !smtpConfigured
                        ? "SMTP is not configured in this environment. Set SMTP_HOST and SMTP_PORT to deliver reset emails."
                        : null)
                .devResetToken(devResetToken)
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        validatePasswordPolicy(request.getNewPassword());

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

        loginAttemptService.clearAllForEmail(user.getEmail());
        refreshTokenService.revokeAllForUser(user.getId());

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
            refreshTokenService.revokeAllForUser(userId);
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

    private void validatePasswordPolicy(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*[0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            throw new InvalidRequestException("Password must be at least 8 characters long and contain both letters and numbers/special characters");
        }
        // BCrypt only uses the first 72 bytes; reject longer input instead of silently truncating
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("Password must be at most 72 bytes long");
        }
    }

    /** Runs the task after the surrounding transaction commits (or immediately if there is none), off the request thread. */
    private void afterCommitAsync(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Exception e) {
                log.warn("Background email task failed: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(safe);
                }
            });
        } else {
            CompletableFuture.runAsync(safe);
        }
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
