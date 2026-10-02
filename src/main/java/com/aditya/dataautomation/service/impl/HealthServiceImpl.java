package com.aditya.dataautomation.service.impl;

import com.aditya.dataautomation.dto.HealthResponse;
import com.aditya.dataautomation.service.HealthService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class HealthServiceImpl implements HealthService {

    private static final String SERVICE_NAME = "data-automation-agent";

    @Override
    public HealthResponse getHealthStatus() {
        return HealthResponse.builder()
                .status("UP")
                .service(SERVICE_NAME)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
