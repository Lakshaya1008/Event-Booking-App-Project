package com.event.tickets.exceptions;

/**
 * Exception thrown when input to a business method is invalid (e.g., null, out of range, malformed).
 */
public class InvalidInputException extends EventTicketException {
    public InvalidInputException(String message) {
        super(message);
    }
    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
