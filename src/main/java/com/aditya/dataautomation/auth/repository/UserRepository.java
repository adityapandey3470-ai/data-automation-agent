package com.aditya.dataautomation.auth.repository;

import com.aditya.dataautomation.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Takes a row lock on the user for the rest of the current transaction (cluster-wide per-user mutex). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** Locks every enabled admin row so two concurrent demote/disable/delete calls cannot both pass the "last admin" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.role = 'ROLE_ADMIN' AND u.enabled = true ORDER BY u.id")
    java.util.List<User> lockEnabledAdmins();

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(String role);

    long countByRole(String role);

    long countByEnabled(boolean enabled);

    long countBySubscriptionTier(String subscriptionTier);

    @Query("SELECT COALESCE(SUM(u.aiQueriesUsed), 0) FROM User u")
    long sumAiQueriesUsed();

    @Query("SELECT COALESCE(SUM(u.totalTokensUsed), 0) FROM User u")
    long sumTotalTokensUsed();

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.aiQueriesUsed = COALESCE(u.aiQueriesUsed, 0) + 1, u.totalTokensUsed = COALESCE(u.totalTokensUsed, 0) + :tokens WHERE u.id = :userId")
    int incrementAiUsageAtomic(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("tokens") long tokens);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.aiQueriesUsed = COALESCE(u.aiQueriesUsed, 0) + 1 WHERE u.id = :userId AND (u.role = 'ROLE_ADMIN' OR u.subscriptionTier = 'ENTERPRISE' OR COALESCE(u.aiQueriesUsed, 0) < COALESCE(u.aiQueriesLimit, 10))")
    int reserveAiQueryQuota(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.aiQueriesUsed = CASE WHEN COALESCE(u.aiQueriesUsed, 0) > 0 THEN u.aiQueriesUsed - 1 ELSE 0 END WHERE u.id = :userId")
    int refundAiQueryQuota(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.totalTokensUsed = COALESCE(u.totalTokensUsed, 0) + :tokens WHERE u.id = :userId")
    int addAiTokens(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("tokens") long tokens);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.aiQueriesUsed = 0")
    int resetAllMonthlyAiQuotas();

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE User u SET u.tokensValidAfter = :ts WHERE u.id = :userId")
    int updateTokensValidAfter(@org.springframework.data.repository.query.Param("userId") Long userId, @org.springframework.data.repository.query.Param("ts") long ts);
}
