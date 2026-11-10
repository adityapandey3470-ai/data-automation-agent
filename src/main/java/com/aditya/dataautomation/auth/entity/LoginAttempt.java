package com.aditya.dataautomation.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @Column(name = "attempt_key", length = 64)
    private String attemptKey;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "first_attempt_at", nullable = false)
    private long firstAttemptAt;

    @Column(name = "locked_until", nullable = false)
    private long lockedUntil;
}
