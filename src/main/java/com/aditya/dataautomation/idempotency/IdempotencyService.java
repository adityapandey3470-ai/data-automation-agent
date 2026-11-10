package com.aditya.dataautomation.idempotency;

import com.aditya.dataautomation.exception.InvalidRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Makes retried POSTs safe. The client sends "Idempotency-Key: <random uuid>"; the first request runs the action and
 * stores the JSON response, a retry with the same key (same user + scope) gets the stored response instead of a second
 * execution. Without the header the action simply runs (fully backward compatible).
 *
 * Returns the original object on first execution and a JsonNode (same JSON) on replay, so callers should return
 * ResponseEntity&lt;?&gt;.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{8,80}$");
    private static final long STALE_IN_PROGRESS_MINUTES = 5;

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public Object execute(Long userId, String scope, String key, Supplier<Object> action) {
        if (key == null || key.isBlank() || userId == null) {
            return action.get();
        }
        String idemKey = key.trim();
        if (!KEY_PATTERN.matcher(idemKey).matches()) {
            throw new InvalidRequestException("Invalid Idempotency-Key (8-80 chars: letters, digits, . _ : -)");
        }

        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long recordId;
        try {
            recordId = requiresNew.execute(status -> {
                IdempotencyRecord r = new IdempotencyRecord();
                r.setUserId(userId);
                r.setScope(scope);
                r.setIdemKey(idemKey);
                r.setStatus("IN_PROGRESS");
                r.setCreatedAt(LocalDateTime.now());
                return repository.saveAndFlush(r).getId();
            });
        } catch (DataIntegrityViolationException duplicate) {
            return replayOrConflict(userId, scope, idemKey, requiresNew, action);
        }

        return runAndStore(recordId, requiresNew, action);
    }

    private Object replayOrConflict(Long userId, String scope, String idemKey, TransactionTemplate requiresNew, Supplier<Object> action) {
        IdempotencyRecord existing = repository.findByUserIdAndScopeAndIdemKey(userId, scope, idemKey).orElse(null);
        if (existing == null) {
            // the other request failed and removed its record between our insert and our read: just run normally
            return action.get();
        }
        if ("COMPLETED".equals(existing.getStatus())) {
            try {
                return existing.getResponseJson() != null ? objectMapper.readTree(existing.getResponseJson()) : null;
            } catch (Exception e) {
                throw new IllegalStateException("Stored idempotent response is unreadable", e);
            }
        }
        if (existing.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(STALE_IN_PROGRESS_MINUTES))) {
            // the original request died (crash / restart) without finishing: take the slot over once
            repository.deleteByRecordId(existing.getId());
            Long newId;
            try {
                newId = requiresNew.execute(status -> {
                    IdempotencyRecord r = new IdempotencyRecord();
                    r.setUserId(userId);
                    r.setScope(scope);
                    r.setIdemKey(idemKey);
                    r.setStatus("IN_PROGRESS");
                    r.setCreatedAt(LocalDateTime.now());
                    return repository.saveAndFlush(r).getId();
                });
            } catch (DataIntegrityViolationException raced) {
                throw new DuplicateKeyException("A request with this Idempotency-Key is still being processed");
            }
            return runAndStore(newId, requiresNew, action);
        }
        throw new DuplicateKeyException("A request with this Idempotency-Key is still being processed");
    }

    private Object runAndStore(Long recordId, TransactionTemplate requiresNew, Supplier<Object> action) {
        Object result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            // failed requests must be retryable with the same key
            try {
                repository.deleteByRecordId(recordId);
            } catch (Exception cleanup) {
                log.warn("Could not release idempotency key {}: {}", recordId, cleanup.getMessage());
            }
            throw e;
        }
        try {
            final String json = result != null ? objectMapper.writeValueAsString(result) : null;
            requiresNew.executeWithoutResult(status -> repository.findById(recordId).ifPresent(r -> {
                r.setStatus("COMPLETED");
                r.setResponseJson(json);
                repository.save(r);
            }));
        } catch (Exception e) {
            log.warn("Could not store idempotent response for record {}: {}", recordId, e.getMessage());
            try {
                repository.deleteByRecordId(recordId);
            } catch (Exception ignored) {
                // best effort
            }
        }
        return result;
    }

    @Scheduled(cron = "0 15 * * * *")
    public void cleanup() {
        int removed = repository.deleteOlderThan(LocalDateTime.now().minusHours(24));
        if (removed > 0) {
            log.debug("Removed {} expired idempotency keys", removed);
        }
    }
}
