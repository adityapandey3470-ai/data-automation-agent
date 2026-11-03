package com.aditya.dataautomation.auth.repository;

import com.aditya.dataautomation.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(String role);

    long countByRole(String role);

    long countBySubscriptionTier(String subscriptionTier);

    @Query("SELECT COALESCE(SUM(u.aiQueriesUsed), 0) FROM User u")
    long sumAiQueriesUsed();

    @Query("SELECT COALESCE(SUM(u.totalTokensUsed), 0) FROM User u")
    long sumTotalTokensUsed();
}
