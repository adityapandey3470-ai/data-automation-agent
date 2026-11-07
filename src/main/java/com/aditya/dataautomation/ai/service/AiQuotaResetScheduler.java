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

    private final UserRepository userRepository;

    /** 00:05 on the 1st of every month (server time). Safe on several instances: it only sets the counter to 0. */
    @Scheduled(cron = "0 5 0 1 * *")
    @Transactional
    public void resetMonthlyQuotas() {
        int updated = userRepository.resetAllMonthlyAiQuotas();
        log.info("Monthly AI quota reset for {} users", updated);
    }
}
