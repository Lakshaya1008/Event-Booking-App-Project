package com.event.tickets.exceptions;

/**
 * Thrown when a requested discount is not found.
 */
public class DiscountNotFoundException extends EventTicketException {

  public DiscountNotFoundException(String message) {
    super(message);
  }

  public DiscountNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
