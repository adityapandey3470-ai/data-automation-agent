package com.aditya.dataautomation.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardLiveNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /** Sends after the surrounding transaction commits, so clients never refetch state that is later rolled back. */
    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    public void broadcastDashboardUpdate(String dashboardId, String action, String triggeredBy) {
        afterCommit(() -> doBroadcast(dashboardId, action));
    }

    private void doBroadcast(String dashboardId, String action) {
        try {
            Map<String, Object> payload = Map.of(
                    "dashboardId", dashboardId,
                    "action", action,
                    "timestamp", System.currentTimeMillis()
            );
            messagingTemplate.convertAndSend("/topic/dashboard/" + dashboardId, payload);
        } catch (Exception e) {
            log.warn("Failed to broadcast dashboard WebSocket event: {}", e.getMessage());
        }
    }

    public void notifyUser(Long userId, String destination, Object payload) {
        afterCommit(() -> doNotifyUser(userId, destination, payload));
    }

    private void doNotifyUser(Long userId, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), destination, payload);
        } catch (Exception e) {
            log.warn("Failed to send user notification to userId={}: {}", userId, e.getMessage());
        }
    }
}
