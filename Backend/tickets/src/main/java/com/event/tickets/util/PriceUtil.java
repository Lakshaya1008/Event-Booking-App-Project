package com.event.tickets.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for consistent price handling across the application.
 *
 * FIX #3: Centralized price operations ensure:
 * - Consistent rounding mode (HALF_UP)
 * - Consistent scale (2 decimal places)
 * - Prevent floating-point precision errors
 *
 * All monetary calculations should use these methods instead of
 * raw BigDecimal operations.
 */
public class PriceUtil {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Round a price to 2 decimal places using HALF_UP mode.
     * Standard for financial calculations in most jurisdictions.
     *
     * Examples:
     * - 10.125 → 10.13
     * - 10.124 → 10.12
     *
     * @param value The price to round
     * @return Rounded price, or null if input is null
     */
    public static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Add two prices with proper rounding.
     *
     * @param a First price
     * @param b Second price
     * @return Sum rounded to 2 decimal places
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return round(a.add(b));
    }

    /**
     * Subtract two prices with proper rounding.
     *
     * @param minuend The price to subtract from
     * @param subtrahend The price to subtract
     * @return Difference rounded to 2 decimal places
     */
    public static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
        if (minuend == null) minuend = BigDecimal.ZERO;
        if (subtrahend == null) subtrahend = BigDecimal.ZERO;
        return round(minuend.subtract(subtrahend));
    }

    /**
     * Multiply a price by a percentage.
     * Example: multiply(100.00, 0.15) = 15.00 (15% of $100)
     *
     * @param basePrice The base price
     * @param multiplier The multiplier (0.15 for 15%, etc.)
     * @return Product rounded to 2 decimal places
     */
    public static BigDecimal multiply(BigDecimal basePrice, BigDecimal multiplier) {
        if (basePrice == null || multiplier == null) {
            return BigDecimal.ZERO;
        }
        return round(basePrice.multiply(multiplier));
    }

    /**
     * Divide one price by another.
     *
     * @param dividend The price to divide
     * @param divisor The divisor
     * @return Quotient rounded to 2 decimal places
     */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null || divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Invalid division: dividend=" + dividend + ", divisor=" + divisor);
        }
        return dividend.divide(divisor, SCALE, ROUNDING_MODE);
    }

    /**
     * Validate that a price is positive.
     *
     * @param price The price to validate
     * @param fieldName The field name for error messages
     * @throws IllegalArgumentException if price is null or <= 0
     */
    public static void validatePositive(BigDecimal price, String fieldName) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, got: " + price);
        }
    }

    /**
     * Validate that a price is non-negative (zero or positive).
     *
     * @param price The price to validate
     * @param fieldName The field name for error messages
     * @throws IllegalArgumentException if price is null or < 0
     */
    public static void validateNonNegative(BigDecimal price, String fieldName) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative, got: " + price);
        }
    }

    /**
     * Validate that a price is within the database column limits (precision 10,2).
     * Max value: 99,999,999.99
     *
     * @param price The price to validate
     * @param fieldName The field name for error messages
     * @throws IllegalArgumentException if price exceeds limits
     */
    public static void validateWithinLimits(BigDecimal price, String fieldName) {
        validateNonNegative(price, fieldName);
        if (price.compareTo(BigDecimal.valueOf(99999999.99)) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot exceed 99,999,999.99, got: " + price);
        }
    }
}

