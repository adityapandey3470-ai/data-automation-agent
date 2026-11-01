package com.aditya.dataautomation.service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "working_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkingSessionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_id", length = 64, nullable = false)
    private String fileId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "workbook_data_json", nullable = false, columnDefinition = "TEXT")
    private String workbookDataJson;

    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson;

    @Builder.Default
    @Column(name = "current_index")
    private Integer currentIndex = -1;

    @Column(name = "last_accessed", nullable = false)
    private LocalDateTime lastAccessed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastAccessed == null) {
            lastAccessed = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (currentIndex == null) {
            currentIndex = -1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastAccessed = LocalDateTime.now();
    }
}
