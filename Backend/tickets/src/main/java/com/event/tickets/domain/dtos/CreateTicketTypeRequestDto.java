package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX-TT5 (BUG 4-4) — totalAvailable made optional (nullable allowed).
 *
 *   BEFORE: @NotNull @Min(1) on totalAvailable meant free events or events
 *   with unlimited ticket capacity could not be created via this DTO.
 *   The TicketType entity has totalAvailable as a nullable column, meaning
 *   the domain model supports unlimited capacity — but the DTO blocked it.
 *
 *   AFTER: totalAvailable is optional. Null = unlimited (no cap enforced
 *   at the ticket-type level). If provided, must be >= 1.
 *   The @Min(1) constraint is preserved but @NotNull is removed.
 *
 *   Service behaviour when totalAvailable is null:
 *   - purchaseTickets() skips the per-type capacity check (no cap = no check)
 *   - The event-level maxCapacity still applies if set
 *   - getSalesDashboard() shows remaining=null (displayed as "Unlimited")
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTicketTypeRequestDto {

    @NotBlank(message = "Ticket type name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be zero or greater")
    private BigDecimal price;

    private String description;

    /**
     * FIX-TT5: No longer @NotNull. Null means unlimited capacity.
     * If provided, must be at least 1.
     */
    @Min(value = 1, message = "Total available must be at least 1 if provided")
    private Integer totalAvailable;
}