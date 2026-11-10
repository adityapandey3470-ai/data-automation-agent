package com.aditya.dataautomation.exception;

/** Login / refresh failure. Mapped to HTTP 401 by GlobalExceptionHandler. */
public class AuthenticationFailedException extends InvalidRequestException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
