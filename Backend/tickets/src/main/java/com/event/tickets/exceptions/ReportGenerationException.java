package com.event.tickets.exceptions;

/**
 * Exception thrown when report generation (e.g., Excel export) fails.
 */
public class ReportGenerationException extends EventTicketException {
    public ReportGenerationException(String message) {
        super(message);
    }
    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
