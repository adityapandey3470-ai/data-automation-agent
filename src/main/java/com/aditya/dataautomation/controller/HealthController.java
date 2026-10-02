package com.aditya.dataautomation.controller;

import com.aditya.dataautomation.dto.HealthResponse;
import com.aditya.dataautomation.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Application health and status endpoints")
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    @Operation(summary = "Check application health", description = "Returns the current health status of the service")
    public ResponseEntity<HealthResponse> checkHealth() {
        return ResponseEntity.ok(healthService.getHealthStatus());
    }
}
