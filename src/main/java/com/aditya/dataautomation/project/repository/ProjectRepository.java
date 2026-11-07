package com.aditya.dataautomation.project.repository;

import com.aditya.dataautomation.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    List<Project> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Project> findByIdAndUserId(String id, Long userId);

    boolean existsByIdAndUserId(String id, Long userId);

    long countByUserId(Long userId);

    @Query("SELECT p.id AS id, p.name AS name, p.fileName AS fileName, p.fileSizeBytes AS fileSizeBytes, p.rowCount AS rowCount, p.columnCount AS columnCount, p.createdAt AS createdAt, p.updatedAt AS updatedAt, p.user AS user FROM Project p ORDER BY p.updatedAt DESC")
    List<ProjectMetadataView> findAllProjectMetadata();
}
