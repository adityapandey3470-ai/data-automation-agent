package com.aditya.dataautomation.cleaning.exception;


public class CleaningException extends RuntimeException {

    public CleaningException(String message) {
        super(message);
    }

    public CleaningException(String message, Throwable cause) {
        super(message, cause);
    }
}
