package com.aditya.dataautomation.auth.repository;

import com.aditya.dataautomation.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :at WHERE t.id = :id AND t.revokedAt IS NULL")
    int revokeIfActive(@Param("id") Long id, @Param("at") long at);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :at WHERE t.familyId = :family AND t.revokedAt IS NULL")
    int revokeFamily(@Param("family") String family, @Param("at") long at);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :at WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("at") long at);

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") long now);
}
