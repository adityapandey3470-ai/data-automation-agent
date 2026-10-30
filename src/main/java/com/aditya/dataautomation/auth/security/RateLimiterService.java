package com.aditya.dataautomation.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe sliding window rate limiter with automatic memory cleanup.
 */
@Slf4j
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, List<Long>> requestWindows = new ConcurrentHashMap<>();
    private static final int MAX_TRACKED_KEYS = 50000;

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
        public static RateLimitResult permit() {
            return new RateLimitResult(true, 0);
        }
        public static RateLimitResult reject(long retryAfterSeconds) {
            return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
        }
    }

    /**
     * Attempts to acquire an execution permit and returns detailed rate limit results
     * including accurate Retry-After seconds calculated against the sliding window.
     */
    public RateLimitResult tryAcquireWithResult(String key, int maxRequests, int windowSeconds) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.permit();
        }

        long now = System.currentTimeMillis();
        long windowMillis = (long) windowSeconds * 1000L;
        long windowStart = now - windowMillis;

        // Bounded map safeguard against memory exhaustion
        if (requestWindows.size() > MAX_TRACKED_KEYS) {
            pruneExpiredWindows(windowStart);
        }

        final RateLimitResult[] result = new RateLimitResult[1];

        requestWindows.compute(key, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayList<>();
            }
            synchronized (timestamps) {
                timestamps.removeIf(t -> t < windowStart);

                if (timestamps.size() >= maxRequests) {
                    // Determine the oldest active request that must roll out of the sliding window
                    long oldestTimestamp = timestamps.get(0);
                    long expiryTime = oldestTimestamp + windowMillis;
                    long remainingMillis = Math.max(0, expiryTime - now);
                    long retryAfterSec = Math.max(1, (remainingMillis + 999L) / 1000L);
                    result[0] = RateLimitResult.reject(retryAfterSec);
                } else {
                    timestamps.add(now);
                    result[0] = RateLimitResult.permit();
                }
                return timestamps;
            }
        });

        return result[0];
    }

    /**
     * Automatic scheduled background maintenance to evict expired rate-limit buckets.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void scheduledEviction() {
        long fiveMinutesAgo = System.currentTimeMillis() - 300_000L;
        pruneExpiredWindows(fiveMinutesAgo);
    }

    /**
     * Attempts to acquire an execution permit.
     *
     * @param key           Identifier (e.g., client IP or User ID + action)
     * @param maxRequests   Allowed requests within the window
     * @param windowSeconds Window length in seconds
     * @return true if allowed; false if rate limit exceeded
     */
    public boolean tryAcquire(String key, int maxRequests, int windowSeconds) {
        return tryAcquireWithResult(key, maxRequests, windowSeconds).allowed();
    }

    public void reset() {
        requestWindows.clear();
    }

    private void pruneExpiredWindows(long cutoffTime) {
        requestWindows.entrySet().removeIf(entry -> {
            List<Long> list = entry.getValue();
            synchronized (list) {
                list.removeIf(t -> t < cutoffTime);
                return list.isEmpty();
            }
        });
    }
}
