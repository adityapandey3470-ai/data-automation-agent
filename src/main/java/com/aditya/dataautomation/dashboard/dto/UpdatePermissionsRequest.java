package com.aditya.dataautomation.dashboard.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePermissionsRequest {
    private boolean canCustomize;
    private boolean canInvite;
}
