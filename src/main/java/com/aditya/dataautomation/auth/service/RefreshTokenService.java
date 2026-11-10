package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.auth.entity.RefreshToken;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.RefreshTokenRepository;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import com.aditya.dataautomation.exception.AuthenticationFailedException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    public static final String COOKIE_NAME = "tabufinery_rt";
    private static final long HARD_REVOKED = 1L;
    private static final int MAX_ACTIVE_TOKENS_PER_USER = 10;
    /** Two tabs may refresh at almost the same moment with the same cookie; do not treat that as theft. */
    private static final long REUSE_GRACE_MS = 10_000L;

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.auth.refresh-token-days:30}")
    private int refreshDays;

    public record Issued(String rawToken, long maxAgeSeconds) {
    }

    /** newRawToken == null means "another tab just rotated; keep the cookie the browser already has". */
    public record Rotation(User user, String newRawToken, long maxAgeSeconds) {
    }

    @Transactional
    public Issued issueForUser(Long userId) {
        // cap active sessions per user so repeated logins cannot grow the table unbounded
        List<RefreshToken> active = repository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(userId);
        for (int i = 0; i <= active.size() - MAX_ACTIVE_TOKENS_PER_USER; i++) {
            repository.revokeIfActive(active.get(i).getId(), HARD_REVOKED);
        }
        return issue(userId, UUID.randomUUID().toString().replace("-", ""));
    }

    @Transactional(noRollbackFor = InvalidRequestException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AuthenticationFailedException("Session expired. Please sign in again.");
        }
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new AuthenticationFailedException("Session expired. Please sign in again."));
        long now = System.currentTimeMillis();

        if (token.getRevokedAt() != null) {
            if (now - token.getRevokedAt() <= REUSE_GRACE_MS) {
                return new Rotation(requireActiveUser(token.getUserId()), null, 0);
            }
            repository.revokeFamily(token.getFamilyId(), HARD_REVOKED);
            log.warn("Refresh token replay detected; token family revoked for user id={}", token.getUserId());
            throw new AuthenticationFailedException("Session expired. Please sign in again.");
        }
        if (token.getExpiresAt() < now) {
            throw new AuthenticationFailedException("Session expired. Please sign in again.");
        }

        User user = requireActiveUser(token.getUserId());
        if (repository.revokeIfActive(token.getId(), now) != 1) {
            return new Rotation(user, null, 0); // lost a race with another tab: it already rotated
        }
        Issued next = issue(user.getId(), token.getFamilyId());
        return new Rotation(user, next.rawToken(), next.maxAgeSeconds());
    }

    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> {
                    repository.revokeFamily(t.getFamilyId(), HARD_REVOKED);
                    // logout = every device: revoke all refresh tokens and invalidate issued access tokens
                    repository.revokeAllForUser(t.getUserId(), HARD_REVOKED);
                    userRepository.updateTokensValidAfter(t.getUserId(), System.currentTimeMillis() / 1000);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, HARD_REVOKED);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupExpired() {
        repository.deleteExpired(System.currentTimeMillis());
    }

    private Issued issue(Long userId, String familyId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = System.currentTimeMillis();
        long maxAgeSeconds = refreshDays * 86_400L;

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(raw));
        entity.setFamilyId(familyId);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now + maxAgeSeconds * 1000L);
        repository.save(entity);
        return new Issued(raw, maxAgeSeconds);
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("Session expired. Please sign in again."));
        if (!user.isEnabled() || !user.isEmailVerified()) {
            throw new AuthenticationFailedException("Session expired. Please sign in again.");
        }
        return user;
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
