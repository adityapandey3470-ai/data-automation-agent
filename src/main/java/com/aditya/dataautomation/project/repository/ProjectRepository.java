package com.aditya.dataautomation.project.repository;

import com.aditya.dataautomation.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    interface ProjectMetadataView {
        String getId();
        String getName();
        String getFileName();
        Long getFileSizeBytes();
        Integer getRowCount();
        Integer getColumnCount();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
        UserMetadataView getUser();

        interface UserMetadataView {
            Long getId();
            String getName();
            String getEmail();
        }
    }

    /** Lightweight projection: never loads workbook_data_json / cleaning_history_json. */
    interface ProjectSummaryView {
        String getId();
        String getName();
        String getDescription();
        String getFileName();
        Long getFileSizeBytes();
        Integer getRowCount();
        Integer getColumnCount();
        Integer getSheetCount();
        String getActiveSheetName();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
    }

    @Query("SELECT p.id AS id, p.name AS name, p.description AS description, p.fileName AS fileName, p.fileSizeBytes AS fileSizeBytes, p.rowCount AS rowCount, p.columnCount AS columnCount, p.sheetCount AS sheetCount, p.activeSheetName AS activeSheetName, p.createdAt AS createdAt, p.updatedAt AS updatedAt FROM Project p WHERE p.user.id = :userId ORDER BY p.updatedAt DESC")
    List<ProjectSummaryView> findSummariesByUserId(@Param("userId") Long userId);

    @Query("SELECT p.id AS id, p.name AS name, p.description AS description, p.fileName AS fileName, p.fileSizeBytes AS fileSizeBytes, p.rowCount AS rowCount, p.columnCount AS columnCount, p.sheetCount AS sheetCount, p.activeSheetName AS activeSheetName, p.createdAt AS createdAt, p.updatedAt AS updatedAt FROM Project p WHERE p.id = :id AND p.user.id = :userId")
    Optional<ProjectSummaryView> findSummaryByIdAndUserId(@Param("id") String id, @Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Project p SET p.name = :name, p.updatedAt = :now WHERE p.id = :id AND p.user.id = :userId")
    int renameByIdAndUserId(@Param("id") String id, @Param("userId") Long userId, @Param("name") String name, @Param("now") LocalDateTime now);

    List<Project> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Project> findByIdAndUserId(String id, Long userId);

    boolean existsByIdAndUserId(String id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT p.user.id, COUNT(p) FROM Project p WHERE p.user.id IN :ids GROUP BY p.user.id")
    List<Object[]> countByUserIds(@Param("ids") java.util.Collection<Long> ids);

    @Query("SELECT p.id AS id, p.name AS name, p.fileName AS fileName, p.fileSizeBytes AS fileSizeBytes, p.rowCount AS rowCount, p.columnCount AS columnCount, p.createdAt AS createdAt, p.updatedAt AS updatedAt, p.user AS user FROM Project p ORDER BY p.updatedAt DESC")
    List<ProjectMetadataView> findAllProjectMetadata();
}
