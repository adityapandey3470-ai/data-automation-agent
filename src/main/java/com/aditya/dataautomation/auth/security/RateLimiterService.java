package com.aditya.dataautomation.auth.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, List<Long>> requestWindows = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    /** While Redis is down, skip it for 30 s instead of paying its timeout on every single request. */
    private final com.aditya.dataautomation.common.resilience.SimpleCircuitBreaker redisBreaker =
            new com.aditya.dataautomation.common.resilience.SimpleCircuitBreaker(3, 30_000);

    @Value("${app.rate-limit.redis-enabled:false}")
    private boolean redisEnabled;

    private static final String LUA_SLIDING_WINDOW =
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local windowMillis = tonumber(ARGV[2])\n" +
            "local maxRequests = tonumber(ARGV[3])\n" +
            "local clearBefore = now - windowMillis\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)\n" +
            "local currentCount = redis.call('ZCARD', key)\n" +
            "if currentCount < maxRequests then\n" +
            "    redis.call('ZADD', key, now, ARGV[4])\n" +
            "    redis.call('PEXPIRE', key, windowMillis + 1000)\n" +
            "    return {1, 0}\n" +
            "else\n" +
            "    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')\n" +
            "    local oldestScore = tonumber(oldest[2])\n" +
            "    local retryAfterSec = math.max(1, math.ceil(((oldestScore + windowMillis) - now) / 1000))\n" +
            "    return {0, retryAfterSec}\n" +
            "end";

    private final RedisScript<List> redisScript = new DefaultRedisScript<>(LUA_SLIDING_WINDOW, List.class);

    public RateLimiterService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
        public static RateLimitResult permit() {
            return new RateLimitResult(true, 0);
        }
        public static RateLimitResult reject(long retryAfterSeconds) {
            return new RateLimitResult(false, Math.max(1, retryAfterSeconds));
        }
    }

    public RateLimitResult tryAcquireWithResult(String key, int maxRequests, int windowSeconds) {
        if (key == null || key.isBlank()) {
            return RateLimitResult.permit();
        }

        if (redisEnabled && redisTemplate != null && redisBreaker.allowRequest()) {
            try {
                RateLimitResult viaRedis = acquireViaRedis(key, maxRequests, windowSeconds);
                redisBreaker.recordSuccess();
                return viaRedis;
            } catch (Exception e) {
                redisBreaker.recordFailure();
                log.warn("Redis rate limiter unavailable, falling back to in-memory: {}", e.getMessage());
            }
        }

        return acquireInMemory(key, maxRequests, windowSeconds);
    }

    private RateLimitResult acquireViaRedis(String key, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = (long) windowSeconds * 1000L;
        List<?> result = redisTemplate.execute(
                redisScript,
                Collections.singletonList("rl:" + key),
                String.valueOf(now),
                String.valueOf(windowMillis),
                String.valueOf(maxRequests),
                java.util.UUID.randomUUID().toString()
        );

        if (result != null && result.size() >= 2) {
            long allowedFlag = ((Number) result.get(0)).longValue();
            long retryAfter = ((Number) result.get(1)).longValue();
            return allowedFlag == 1L ? RateLimitResult.permit() : RateLimitResult.reject(retryAfter);
        }

        return acquireInMemory(key, maxRequests, windowSeconds);
    }

    private RateLimitResult acquireInMemory(String key, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = (long) windowSeconds * 1000L;
        long windowStart = now - windowMillis;

        final RateLimitResult[] result = new RateLimitResult[1];

        requestWindows.compute(key, (k, timestamps) -> {
            if (timestamps == null) {
                timestamps = new ArrayList<>();
            }
            synchronized (timestamps) {
                timestamps.removeIf(t -> t < windowStart);

                if (timestamps.size() >= maxRequests) {
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

    @Scheduled(fixedRate = 60000)
    public void scheduledEviction() {
        long fiveMinutesAgo = System.currentTimeMillis() - 300_000L;
        pruneExpiredWindows(fiveMinutesAgo);
    }

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
