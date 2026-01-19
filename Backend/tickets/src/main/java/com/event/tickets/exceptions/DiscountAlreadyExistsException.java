package com.event.tickets.exceptions;

/**
 * Thrown when attempting to create or activate a discount when one already exists
 * for the same ticket type.
 *
 * <p>Business Rule: Only ONE active discount per ticket type at a time.
 */
public class DiscountAlreadyExistsException extends EventTicketException {

  public DiscountAlreadyExistsException(String message) {
    super(message);
  }

  public DiscountAlreadyExistsException(String message, Throwable cause) {
    super(message, cause);
  }
}
