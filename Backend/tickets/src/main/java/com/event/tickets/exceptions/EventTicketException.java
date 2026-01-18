package com.event.tickets.exceptions;

/**
 * Base exception for event ticketing business logic errors.
 *
 * All domain-specific exceptions should extend this class.
 */
public class EventTicketException extends RuntimeException {

  public EventTicketException() {
    super();
  }

  public EventTicketException(String message) {
    super(message);
  }

  public EventTicketException(String message, Throwable cause) {
    super(message, cause);
  }

  public EventTicketException(Throwable cause) {
    super(cause);
  }

  public EventTicketException(String message, Throwable cause,
      boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
