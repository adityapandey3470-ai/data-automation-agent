package com.aditya.dataautomation.exception;

/** Too many failed attempts / rate limited. Mapped to HTTP 429 by GlobalExceptionHandler. */
public class TooManyAttemptsException extends InvalidRequestException {
    private final long retryAfterSeconds;

    public TooManyAttemptsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
