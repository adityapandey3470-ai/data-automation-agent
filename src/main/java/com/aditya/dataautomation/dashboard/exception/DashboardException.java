package com.aditya.dataautomation.dashboard.exception;

public class DashboardException extends RuntimeException {

    public DashboardException(String message) {
        super(message);
    }

    public DashboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
