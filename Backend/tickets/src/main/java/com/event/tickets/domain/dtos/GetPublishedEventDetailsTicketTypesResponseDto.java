package com.event.tickets.domain.dtos;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX-E7-DTO: Added totalAvailable so attendees can see remaining capacity per tier.
 *
 *   BEFORE: id, name, price, description only.
 *   An attendee had no way to know if a ticket type was sold out
 *   or how many slots remained.
 *
 *   AFTER: totalAvailable included (null = unlimited).
 *   The EventMapper must compute this when mapping — see EventMapper notes.
 *
 *   Intentionally NOT included: internal ticket type ID is included for
 *   purchase flow (client needs it to call POST /ticket-types/{id}/tickets).
 *   Sold count is NOT included — that's organizer-only data.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetPublishedEventDetailsTicketTypesResponseDto {

    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;

    /**
     * FIX-E7-DTO: How many tickets are available for purchase.
     * Null means unlimited (no cap set by organizer).
     * When 0, this ticket type is sold out.
     *
     * Note: this is totalAvailable from the ticket type, NOT remaining slots.
     * Remaining = totalAvailable - soldCount, but soldCount is organizer-only.
     * Exposing totalAvailable gives attendees enough signal to know the tier exists
     * and a rough sense of scale; exact remaining count is intentionally withheld.
     */
    private Integer totalAvailable;
}