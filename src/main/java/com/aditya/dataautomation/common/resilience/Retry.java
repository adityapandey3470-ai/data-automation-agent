package com.aditya.dataautomation.common.resilience;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Tiny retry helper with exponential backoff + jitter (1x, 3x, 9x of the base delay). */
public final class Retry {

    @FunctionalInterface
    public interface Attempt<T> {
        T run() throws Exception;
    }

    private Retry() {
    }

    public static <T> T withBackoff(int maxAttempts, long baseDelayMs, Predicate<Exception> retryable, Attempt<T> attempt)
            throws Exception {
        long delay = baseDelayMs;
        for (int i = 1; ; i++) {
            try {
                return attempt.run();
            } catch (Exception e) {
                if (i >= maxAttempts || !retryable.test(e)) {
                    throw e;
                }
                long jitter = ThreadLocalRandom.current().nextLong(delay / 4 + 1);
                try {
                    Thread.sleep(delay + jitter);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                delay *= 3;
            }
        }
    }
}
