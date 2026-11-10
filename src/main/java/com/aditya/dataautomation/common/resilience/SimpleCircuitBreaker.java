package com.aditya.dataautomation.common.resilience;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * After {@code failureThreshold} failures in a row the breaker opens for {@code openMillis}:
 * allowRequest() is false, so callers skip the slow/broken dependency and use their fallback at once.
 * When the time is over one trial request is let through; one more failure re-opens it, a success closes it.
 */
public class SimpleCircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile long openUntil = 0L;

    public SimpleCircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
    }

    public boolean allowRequest() {
        return System.currentTimeMillis() >= openUntil;
    }

    public void recordSuccess() {
        failures.set(0);
        openUntil = 0L;
    }

    public void recordFailure() {
        if (failures.incrementAndGet() >= failureThreshold) {
            openUntil = System.currentTimeMillis() + openMillis;
            failures.set(failureThreshold - 1); // a single failure after the trial re-opens it
        }
    }

    public boolean isOpen() {
        return !allowRequest();
    }
}
