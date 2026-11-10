package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.auth.entity.LoginAttempt;
import com.aditya.dataautomation.auth.repository.LoginAttemptRepository;
import com.aditya.dataautomation.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.aditya.dataautomation.exception.TooManyAttemptsException;

@Slf4j
@Service
public class LoginAttemptService {

    static final int MAX_PER_EMAIL_AND_IP = 5;
    static final int MAX_PER_EMAIL_ANY_IP = 25;
    static final long WINDOW_MS = 15 * 60 * 1000L;
    static final long LOCK_MS = 15 * 60 * 1000L;

    private final LoginAttemptRepository repository;
    private final TransactionTemplate requiresNew;

    public LoginAttemptService(LoginAttemptRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Milliseconds this caller is still locked out for (0 = not locked). */
    public long remainingLockMillis(String email, String ip) {
        long until = Math.max(lockedUntil(key(email, ip)), lockedUntil(key(email, null)));
        return Math.max(0, until - System.currentTimeMillis());
    }

    public void checkLockout(String email, String ip) {
        long remaining = remainingLockMillis(email, ip);
        if (remaining > 0) {
            long remainingMinutes = Math.max(1, remaining / 60000);
            throw new TooManyAttemptsException("Account is temporarily locked due to too many failed login attempts. Please try again in " + remainingMinutes + " minute(s) or reset your password.", remaining / 1000);
        }
    }

    /** Records a failed login. Returns true when this failure locked the whole account. */
    public boolean recordFailure(String email, String ip) {
        bump(key(email, ip), MAX_PER_EMAIL_AND_IP);
        return bump(key(email, null), MAX_PER_EMAIL_ANY_IP);
    }

    public void recordSuccess(String email, String ip) {
        deleteKeys(key(email, ip), key(email, null));
    }

    public void clearForEmail(String email) {
        deleteKeys(key(email, null));
    }

    public void clearAllForEmail(String email) {
        clearForEmail(email);
    }

    @Scheduled(fixedRate = 600_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        repository.deleteExpired(now, now - WINDOW_MS);
    }

    private long lockedUntil(String key) {
        return repository.findById(key).map(LoginAttempt::getLockedUntil).orElse(0L);
    }

    private void deleteKeys(String... keys) {
        requiresNew.executeWithoutResult(status -> {
            for (String k : keys) {
                repository.findById(k).ifPresent(repository::delete);
            }
        });
    }

    private boolean bump(String key, int max) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Boolean locked = requiresNew.execute(status -> doBump(key, max));
                return Boolean.TRUE.equals(locked);
            } catch (DataIntegrityViolationException e) {
                // two first failures for the same key raced; try again, the row exists now
            }
        }
        return false;
    }

    private boolean doBump(String key, int max) {
        long now = System.currentTimeMillis();
        LoginAttempt a = repository.findForUpdate(key).orElse(null);
        if (a == null) {
            a = new LoginAttempt();
            a.setAttemptKey(key);
            a.setAttempts(1);
            a.setFirstAttemptAt(now);
            a.setLockedUntil(0);
            repository.saveAndFlush(a);
            return false;
        }
        if (now - a.getFirstAttemptAt() > WINDOW_MS && a.getLockedUntil() < now) {
            a.setAttempts(1);
            a.setFirstAttemptAt(now);
            a.setLockedUntil(0);
            repository.save(a);
            return false;
        }
        a.setAttempts(a.getAttempts() + 1);
        boolean justLocked = false;
        if (a.getAttempts() >= max && a.getLockedUntil() < now) {
            a.setLockedUntil(now + LOCK_MS);
            justLocked = true;
            log.warn("Login lockout triggered (limit {})", max);
        }
        repository.save(a);
        return justLocked;
    }

    private static String key(String email, String ip) {
        String raw = (email == null ? "" : email.trim().toLowerCase()) + (ip == null ? "" : "|" + ip);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
