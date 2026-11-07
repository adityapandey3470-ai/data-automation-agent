package com.aditya.dataautomation.dashboard.repository;

import com.aditya.dataautomation.dashboard.entity.DashboardCollaboratorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DashboardCollaboratorRepository extends JpaRepository<DashboardCollaboratorEntity, String> {

    List<DashboardCollaboratorEntity> findBySharedWithEmailIgnoreCase(String sharedWithEmail);

    List<DashboardCollaboratorEntity> findBySharedWithUserIdOrderByCreatedAtDesc(Long sharedWithUserId);

    List<DashboardCollaboratorEntity> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<DashboardCollaboratorEntity> findByDashboardIdAndSharedWithEmailIgnoreCase(String dashboardId, String sharedWithEmail);

    List<DashboardCollaboratorEntity> findByDashboardIdOrderByCreatedAtDesc(String dashboardId);

    boolean existsByDashboardIdAndOwnerId(String dashboardId, Long ownerId);

    boolean existsByDashboardIdAndSharedWithUserId(String dashboardId, Long sharedWithUserId);

    Optional<DashboardCollaboratorEntity> findByDashboardIdAndSharedWithUserId(String dashboardId, Long sharedWithUserId);

    void deleteByDashboardId(String dashboardId);

    void deleteByOwnerIdOrSharedWithUserId(Long ownerId, Long sharedWithUserId);

    @Query("SELECT c FROM DashboardCollaboratorEntity c WHERE c.sharedWithUserId = :userId OR LOWER(c.sharedWithEmail) = LOWER(:email) ORDER BY c.createdAt DESC")
    List<DashboardCollaboratorEntity> findForUser(@Param("userId") Long userId, @Param("email") String email);
}
