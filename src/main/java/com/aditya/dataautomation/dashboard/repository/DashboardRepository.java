package com.aditya.dataautomation.dashboard.repository;

import com.aditya.dataautomation.dashboard.entity.DashboardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface DashboardRepository extends JpaRepository<DashboardEntity, String> {

    Optional<DashboardEntity> findByDashboardId(String dashboardId);

    /** Row lock: serializes read-modify-write of widgets across threads AND instances. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DashboardEntity d WHERE d.dashboardId = :id")
    Optional<DashboardEntity> findByDashboardIdForUpdate(@Param("id") String id);

    long countByUserId(Long userId);

    List<DashboardEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByDashboardIdAndUserId(String dashboardId, Long userId);

    void deleteByUserId(Long userId);
}
