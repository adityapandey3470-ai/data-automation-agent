package com.aditya.dataautomation.auth.repository;

import com.aditya.dataautomation.auth.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.verified = true WHERE t.tokenHash = :tokenHash AND t.verified = false AND t.expiresAt > :now")
    int markVerificationTokenUsedAtomic(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
