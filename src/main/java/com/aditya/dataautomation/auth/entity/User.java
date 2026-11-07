package com.aditya.dataautomation.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Builder.Default
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Builder.Default
    @Column(name = "subscription_tier", nullable = false)
    private String subscriptionTier = "FREE";

    @Builder.Default
    @Column(name = "ai_queries_used", nullable = false)
    private Integer aiQueriesUsed = 0;

    @Builder.Default
    @Column(name = "ai_queries_limit", nullable = false)
    private Integer aiQueriesLimit = 10;

    @Builder.Default
    @Column(name = "total_tokens_used", nullable = false)
    private Long totalTokensUsed = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    /** Tokens issued at or before this epoch-second are no longer valid (set on logout / password reset). */
    @Column(name = "tokens_valid_after")
    private Long tokensValidAfter;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
