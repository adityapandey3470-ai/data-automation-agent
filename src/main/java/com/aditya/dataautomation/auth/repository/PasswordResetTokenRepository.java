package com.aditya.dataautomation.auth.repository;

import com.aditya.dataautomation.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.tokenHash = :tokenHash AND t.used = false AND t.expiresAt > :now")
    int markTokenUsedAtomic(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
