package com.aditya.dataautomation.dashboard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareDashboardRequest {

    @NotBlank(message = "Dashboard ID is required")
    private String dashboardId;

    @NotBlank(message = "Manager email is required")
    @Email(message = "Valid email address is required")
    private String managerEmail;

    @NotBlank(message = "Dashboard name is required")
    private String dashboardName;

    private boolean canCustomize;

    private boolean canInvite;
}
