package com.event.tickets.exceptions;

/**
 * Exception thrown when the SYSTEM user is not found in the database (should only occur if DB initialization failed).
 */
public class SystemUserNotFoundException extends EventTicketException {
    public SystemUserNotFoundException(String message) {
        super(message);
    }
    public SystemUserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
