package com.aditya.dataautomation.dashboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardLiveNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastDashboardUpdate(String dashboardId, String action, String triggeredBy) {
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
}
