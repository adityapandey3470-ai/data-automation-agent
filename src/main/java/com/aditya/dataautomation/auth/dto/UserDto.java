package com.aditya.dataautomation.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String email;
    private String name;
    private String role;
    private boolean enabled;
    private boolean emailVerified;
    private String subscriptionTier;
    private Integer aiQueriesUsed;
    private Integer aiQueriesLimit;
    private Long totalTokensUsed;
    private LocalDateTime createdAt;
}
