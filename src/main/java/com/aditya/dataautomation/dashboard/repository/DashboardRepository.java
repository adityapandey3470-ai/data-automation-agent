package com.aditya.dataautomation.dashboard.repository;

import com.aditya.dataautomation.dashboard.entity.DashboardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DashboardRepository extends JpaRepository<DashboardEntity, String> {

    Optional<DashboardEntity> findByDashboardId(String dashboardId);

    List<DashboardEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByDashboardIdAndUserId(String dashboardId, Long userId);

    void deleteByUserId(Long userId);
}
