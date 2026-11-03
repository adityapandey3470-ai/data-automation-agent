package com.aditya.dataautomation.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsDto {
    private long totalUsers;
    private long activeUsers;
    private long disabledUsers;
    private long totalProjects;
    private long adminUsers;
    private long freeUsers;
    private long proUsers;
    private long enterpriseUsers;
    private long totalAiQueriesUsed;
    private long totalTokensTracked;
}
