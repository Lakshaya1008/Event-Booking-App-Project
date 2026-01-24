package com.event.tickets.exceptions;

/**
 * Exception thrown when a business logic state is invalid (e.g., forbidden state transition, duplicate assignment).
 */
public class InvalidBusinessStateException extends EventTicketException {
    public InvalidBusinessStateException(String message) {
        super(message);
    }
    public InvalidBusinessStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
