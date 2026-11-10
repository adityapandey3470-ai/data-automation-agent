package com.aditya.dataautomation.auth.repository;

import com.aditya.dataautomation.auth.entity.LoginAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LoginAttempt a WHERE a.attemptKey = :key")
    Optional<LoginAttempt> findForUpdate(@Param("key") String key);

    @Transactional
    @Modifying
    @Query("DELETE FROM LoginAttempt a WHERE a.lockedUntil < :now AND a.firstAttemptAt < :cutoff")
    int deleteExpired(@Param("now") long now, @Param("cutoff") long cutoff);
}
