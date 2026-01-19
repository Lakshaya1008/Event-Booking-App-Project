package com.event.tickets.domain.entities;

/**
 * Type of discount that can be applied to a ticket.
 *
 * <p>PERCENTAGE: Discount as a percentage of base price (0-100)
 * <br>Example: value=20 means 20% off
 *
 * <p>FIXED_AMOUNT: Discount as a fixed currency amount
 * <br>Example: value=10.00 means $10 off
 */
public enum DiscountType {
  /**
   * Percentage-based discount (0-100).
   * Final price = basePrice * (1 - value/100)
   */
  PERCENTAGE,

  /**
   * Fixed amount discount in currency.
   * Final price = basePrice - value
   */
  FIXED_AMOUNT
}
