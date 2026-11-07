package com.aditya.dataautomation.dashboard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dashboards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardEntity {

    @Id
    @Column(name = "dashboard_id", length = 64)
    private String dashboardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_id", length = 64)
    private String fileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sheet_name")
    private String sheetName;

    @Column(name = "widgets_json", columnDefinition = "TEXT")
    private String widgetsJson;

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
