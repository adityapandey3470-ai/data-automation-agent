package com.aditya.dataautomation.ai.service;

import com.aditya.dataautomation.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuotaResetScheduler {

    private static final String LAST_RESET_KEY = "ai_quota_last_reset_month";

    private final UserRepository userRepository;
    private final com.aditya.dataautomation.system.repository.SystemMetadataRepository systemMetadataRepository;

    /** 00:05 on the 1st of every month (server time). */
    @Scheduled(cron = "0 5 0 1 * *")
    @Transactional
    public void resetMonthlyQuotas() {
        String current = java.time.YearMonth.now().toString();
        if (!markResetDone(current)) {
            return; // another instance already did this month's reset
        }
        int updated = userRepository.resetAllMonthlyAiQuotas();
        log.info("Monthly AI quota reset for {} users", updated);
    }

    /** If the app was down at 00:05 on the 1st, the reset is performed on the next startup. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void catchUpMissedReset() {
        String current = java.time.YearMonth.now().toString();
        var existing = systemMetadataRepository.findByMetaKey(LAST_RESET_KEY);
        if (existing.isEmpty()) {
            markResetDone(current); // first deployment: just record the month, do not wipe mid-month usage
            return;
        }
        if (!current.equals(existing.get().getMetaValue()) && markResetDone(current)) {
            int updated = userRepository.resetAllMonthlyAiQuotas();
            log.info("Catch-up AI quota reset for {} users (missed scheduled run)", updated);
        }
    }

    /** @return true if THIS call recorded the month (i.e. the caller should perform the reset). */
    private boolean markResetDone(String month) {
        try {
            var opt = systemMetadataRepository.findByMetaKey(LAST_RESET_KEY);
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (opt.isPresent()) {
                if (month.equals(opt.get().getMetaValue())) {
                    return false;
                }
                opt.get().setMetaValue(month);
                opt.get().setUpdatedAt(now);
                systemMetadataRepository.saveAndFlush(opt.get());
            } else {
                var meta = new com.aditya.dataautomation.system.entity.SystemMetadata(null, LAST_RESET_KEY, month, now, now);
                systemMetadataRepository.saveAndFlush(meta);
            }
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException raced) {
            return false;
        }
    }
}
