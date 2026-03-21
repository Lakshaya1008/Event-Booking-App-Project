package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a discount.
 *
 * FIX D-1: @FutureOrPresent removed from validFrom.
 *
 *   BEFORE: validFrom had @FutureOrPresent, which Spring validates before the controller
 *   method executes. This DTO is shared between createDiscount() and updateDiscount().
 *   For an update, the existing discount's validFrom is almost always in the past (the
 *   discount period started hours or days ago). Every PUT request for a live discount
 *   was rejected with HTTP 400 before reaching the service — making updateDiscount()
 *   effectively uncallable for any discount that had already started.
 *
 *   FIX: @FutureOrPresent removed. The service already enforces this contextually:
 *   - createDiscount():   validateDiscountRequest(request, enforceValidFromFuture=true)
 *                         → throws InvalidInputException if validFrom is in the past
 *   - updateDiscount():   validateDiscountRequest(request, enforceValidFromFuture=false)
 *                         → allows past validFrom because the period may have started
 *
 *   The @Future constraint on validTo is kept — validTo must always be in the future
 *   for both create and update (you cannot set a discount to expire in the past).
 *
 *   The @NotNull on validFrom is kept — callers must always provide it.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDiscountRequestDto {

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be positive")
    private BigDecimal value;

    /**
     * FIX D-1: @FutureOrPresent removed.
     *
     * On CREATE the service enforces future-only via validateDiscountRequest().
     * On UPDATE a past validFrom is valid (the discount period may have started).
     * Removing the annotation here prevents Spring from rejecting update requests
     * for live discounts before they reach the service layer.
     */
    @NotNull(message = "Valid from date is required")
    private LocalDateTime validFrom;

    /**
     * validTo must always be in the future for both create and update.
     * A discount that has already expired cannot be re-saved with the same past validTo.
     */
    @NotNull(message = "Valid to date is required")
    @Future(message = "Valid to date must be in the future")
    private LocalDateTime validTo;

    private Boolean active;

    private String description;
}