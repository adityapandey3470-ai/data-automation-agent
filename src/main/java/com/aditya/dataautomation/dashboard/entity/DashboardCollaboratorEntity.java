package com.aditya.dataautomation.dashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_collaborators")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardCollaboratorEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "dashboard_id", nullable = false, length = 64)
    private String dashboardId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "shared_with_email", nullable = false)
    private String sharedWithEmail;

    @Column(name = "shared_with_user_id")
    private Long sharedWithUserId;

    @Column(name = "dashboard_name", nullable = false)
    private String dashboardName;

    @Column(name = "can_customize", nullable = false)
    @Builder.Default
    private boolean canCustomize = false;

    @Column(name = "can_invite", nullable = false)
    @Builder.Default
    private boolean canInvite = false;

    @Column(name = "invitation_status", nullable = false, length = 32)
    @Builder.Default
    private String invitationStatus = "ACCEPTED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
