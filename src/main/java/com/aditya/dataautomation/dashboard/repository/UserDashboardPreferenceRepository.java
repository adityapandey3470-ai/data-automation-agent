package com.aditya.dataautomation.dashboard.repository;

import com.aditya.dataautomation.dashboard.entity.UserDashboardPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserDashboardPreferenceRepository extends JpaRepository<UserDashboardPreferenceEntity, Long> {

    Optional<UserDashboardPreferenceEntity> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
