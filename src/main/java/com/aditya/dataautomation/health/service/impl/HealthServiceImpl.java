package com.aditya.dataautomation.health.service.impl;

import com.aditya.dataautomation.health.dto.HealthResponse;
import com.aditya.dataautomation.health.service.HealthService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HealthServiceImpl implements HealthService {

    private static final String SERVICE_NAME = "data-automation-agent";

    @Override
    public HealthResponse getHealthStatus() {
        return HealthResponse.builder()
                .status("UP")
                .service(SERVICE_NAME)
                .timestamp(Instant.now().toString())
                .build();
    }
}
