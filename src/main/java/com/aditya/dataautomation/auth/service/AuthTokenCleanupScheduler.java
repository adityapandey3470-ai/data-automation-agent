package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.auth.repository.EmailVerificationTokenRepository;
import com.aditya.dataautomation.auth.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Deletes expired password-reset and email-verification tokens (idempotent, safe on multiple instances). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthTokenCleanupScheduler {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(1);
        int resets = passwordResetTokenRepository.deleteExpired(cutoff);
        int verifications = emailVerificationTokenRepository.deleteExpired(cutoff);
        if (resets > 0 || verifications > 0) {
            log.info("Auth token cleanup: removed {} reset tokens, {} verification tokens", resets, verifications);
        }
    }
}
