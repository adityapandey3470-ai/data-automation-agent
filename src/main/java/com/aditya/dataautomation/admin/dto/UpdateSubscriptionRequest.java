package com.aditya.dataautomation.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubscriptionRequest {

    @NotBlank(message = "Tier is required")
    @Pattern(regexp = "^(?i)(FREE|PRO|ENTERPRISE)$", message = "Subscription tier must be FREE, PRO, or ENTERPRISE")
    private String tier;
}
