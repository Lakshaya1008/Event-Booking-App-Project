package com.event.tickets.exceptions;

/**
 * Exception thrown when attempting to change approval status from an invalid state.
 * For example, trying to approve a user who is already approved or rejected.
 */
public class InvalidApprovalStateException extends RuntimeException {
  public InvalidApprovalStateException(String message) {
    super(message);
  }

  public InvalidApprovalStateException(String message, Throwable cause) {
    super(message, cause);
  }
}
