package com.aditya.dataautomation.job.repository;

import com.aditya.dataautomation.job.entity.AsyncJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AsyncJobRepository extends JpaRepository<AsyncJobEntity, String> {

    List<AsyncJobEntity> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndStatusIn(Long userId, java.util.Collection<String> statuses);

    boolean existsByJobIdAndStatus(String jobId, String status);

    // ---- state machine: only PENDING/RUNNING jobs may change; terminal states (COMPLETED/FAILED/CANCELLED) are final ----

    @Modifying(flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE AsyncJobEntity j SET j.status = 'RUNNING', j.statusMessage = :msg, j.updatedAt = :now WHERE j.jobId = :id AND j.status IN ('PENDING', 'RUNNING')")
    int markRunning(@Param("id") String id, @Param("msg") String msg, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE AsyncJobEntity j SET j.status = 'RUNNING', j.progress = :progress, j.statusMessage = :msg, j.updatedAt = :now WHERE j.jobId = :id AND j.status IN ('PENDING', 'RUNNING')")
    int updateProgressIfActive(@Param("id") String id, @Param("progress") int progress, @Param("msg") String msg, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE AsyncJobEntity j SET j.status = 'COMPLETED', j.progress = 100, j.statusMessage = :msg, j.resultJson = :result, j.updatedAt = :now, j.completedAt = :now WHERE j.jobId = :id AND j.status IN ('PENDING', 'RUNNING')")
    int completeIfActive(@Param("id") String id, @Param("msg") String msg, @Param("result") String result, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE AsyncJobEntity j SET j.status = 'FAILED', j.errorMessage = :err, j.statusMessage = :msg, j.updatedAt = :now, j.completedAt = :now WHERE j.jobId = :id AND j.status IN ('PENDING', 'RUNNING')")
    int failIfActive(@Param("id") String id, @Param("err") String err, @Param("msg") String msg, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE AsyncJobEntity j SET j.status = 'CANCELLED', j.statusMessage = 'Cancelled by user request', j.updatedAt = :now, j.completedAt = :now WHERE j.jobId = :id AND j.status IN ('PENDING', 'RUNNING')")
    int cancelIfActive(@Param("id") String id, @Param("now") LocalDateTime now);

    /** Jobs whose worker died (restart/crash) and never reported progress again. */
    @Modifying(flushAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE AsyncJobEntity j SET j.status = 'FAILED', j.errorMessage = 'Interrupted by a server restart', j.statusMessage = 'Failed: interrupted by a server restart', j.updatedAt = :now, j.completedAt = :now WHERE j.status IN ('PENDING', 'RUNNING') AND j.updatedAt < :cutoff")
    int failStaleActive(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM AsyncJobEntity j WHERE j.userId = :userId")
    int deleteAllByOwner(@Param("userId") Long userId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM AsyncJobEntity j WHERE j.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND j.createdAt < :cutoff")
    int deleteOldJobs(@Param("cutoff") LocalDateTime cutoff);
}
