package com.aditya.dataautomation.dashboard.exception;

/**
 * Thrown when a dashboard operation fails due to invalid configuration,
 * missing sheet/column, or incompatible data types.
 */
public class DashboardException extends RuntimeException {

    public DashboardException(String message) {
        super(message);
    }

    public DashboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
