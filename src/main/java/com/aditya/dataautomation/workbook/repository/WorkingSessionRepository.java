package com.aditya.dataautomation.workbook.repository;

import com.aditya.dataautomation.workbook.entity.WorkingSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkingSessionRepository extends JpaRepository<WorkingSessionEntity, String> {

    interface SessionMetadataView {
        String getFileId();
        String getFileName();
        Long getFileSize();
        Boolean getIsActive();
        LocalDateTime getCreatedAt();
        LocalDateTime getLastAccessed();
        Integer getCurrentIndex();
        Long getVersion();
        Long getUserId();
    }

    @Query("SELECT s.fileId AS fileId, s.fileName AS fileName, s.fileSize AS fileSize, s.isActive AS isActive, s.createdAt AS createdAt, s.lastAccessed AS lastAccessed, s.currentIndex AS currentIndex, s.version AS version, s.userId AS userId FROM WorkingSessionEntity s ORDER BY s.lastAccessed DESC")
    List<SessionMetadataView> findAllMetadataOrderByLastAccessedDesc();

    @Query("SELECT s.fileId AS fileId, s.fileName AS fileName, s.fileSize AS fileSize, s.isActive AS isActive, s.createdAt AS createdAt, s.lastAccessed AS lastAccessed, s.currentIndex AS currentIndex, s.version AS version FROM WorkingSessionEntity s WHERE s.userId = :userId ORDER BY s.createdAt ASC")
    List<SessionMetadataView> findMetadataByUserIdOrderByCreatedAtAsc(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE WorkingSessionEntity s SET s.isActive = CASE WHEN s.fileId = :fileId THEN true ELSE false END, s.lastAccessed = CASE WHEN s.fileId = :fileId THEN :now ELSE s.lastAccessed END WHERE s.userId = :userId")
    int switchActiveSession(@Param("userId") Long userId, @Param("fileId") String fileId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE WorkingSessionEntity s SET s.isActive = false WHERE s.userId = :userId AND s.isActive = true")
    int deactivateAllForUser(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE WorkingSessionEntity s SET s.isActive = true, s.lastAccessed = :now WHERE s.userId = :userId AND s.fileId = :fileId")
    int activateSessionForUser(@Param("userId") Long userId, @Param("fileId") String fileId, @Param("now") LocalDateTime now);

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
