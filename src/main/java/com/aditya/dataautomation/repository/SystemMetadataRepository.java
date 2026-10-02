package com.aditya.dataautomation.repository;

import com.aditya.dataautomation.entity.SystemMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemMetadataRepository extends JpaRepository<SystemMetadata, Long> {

    Optional<SystemMetadata> findByMetaKey(String metaKey);
}
