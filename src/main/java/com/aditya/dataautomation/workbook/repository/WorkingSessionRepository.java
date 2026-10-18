package com.aditya.dataautomation.workbook.repository;

import com.aditya.dataautomation.workbook.entity.WorkingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkingSessionRepository extends JpaRepository<WorkingSessionEntity, String> {

    Optional<WorkingSessionEntity> findByUserIdAndIsActiveTrue(Long userId);

    Optional<WorkingSessionEntity> findByUserIdAndFileId(Long userId, String fileId);

    List<WorkingSessionEntity> findByUserId(Long userId);

    List<WorkingSessionEntity> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByUserIdAndFileId(Long userId, String fileId);

    @Modifying
    @Transactional
    void deleteByLastAccessedBefore(LocalDateTime cutoff);

    @Modifying
    @Transactional
    void deleteByUserIdAndFileId(Long userId, String fileId);

    @Modifying
    @Transactional
    void deleteAllByUserId(Long userId);
}
