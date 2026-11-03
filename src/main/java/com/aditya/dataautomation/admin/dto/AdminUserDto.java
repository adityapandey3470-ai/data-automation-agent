package com.aditya.dataautomation.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean enabled;
    private String subscriptionTier;
    private int aiQueriesUsed;
    private int aiQueriesLimit;
    private long totalTokensUsed;
    private int projectCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
