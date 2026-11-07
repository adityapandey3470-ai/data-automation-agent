package com.aditya.dataautomation.dashboard.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaboratorResponse {
    private String id;
    private String dashboardId;
    private Long ownerId;
    private String ownerEmail;
    private String sharedWithEmail;
    private Long sharedWithUserId;
    private String dashboardName;
    private boolean canCustomize;
    private boolean canInvite;
    private String invitationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
