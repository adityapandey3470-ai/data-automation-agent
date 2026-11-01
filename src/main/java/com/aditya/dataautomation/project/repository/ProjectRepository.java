package com.aditya.dataautomation.project.repository;

import com.aditya.dataautomation.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    List<Project> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<Project> findByIdAndUserId(String id, Long userId);

    boolean existsByIdAndUserId(String id, Long userId);

    long countByUserId(Long userId);
}
