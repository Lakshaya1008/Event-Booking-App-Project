package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.EventStatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX-E1-DTO1 — status field removed from create request.
 *   BEFORE: Organizer could POST with status=PUBLISHED and skip the DRAFT step entirely.
 *   AFTER: New events are always created as DRAFT in the service layer.
 *   The status field is not accepted — clients that send it will have it ignored.
 *
 * FIX-E8-DTO2 — start and end are now @NotNull.
 *   An event with no start/end date is meaningless to attendees and organizers.
 *   salesStart and salesEnd remain optional (some events may not have a formal
 *   sales window separate from the event window).
 *
 * FIX-E8-DTO3 — @AssertTrue cross-field ordering validation at DTO level.
 *   The service still validates too, but catching it at Bean Validation layer
 *   gives the client a 400 with a clear message instead of a 422 from the service.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateEventRequestDto {

    @NotBlank(message = "Event name is required")
    @Size(max = 200, message = "Event name must not exceed 200 characters")
    private String name;

    /**
     * FIX-E8-DTO2: Now required. An event must have a start date.
     */
    @NotNull(message = "Event start date is required")
    private LocalDateTime start;

    /**
     * FIX-E8-DTO2: Now required. An event must have an end date.
     */
    @NotNull(message = "Event end date is required")
    private LocalDateTime end;

    @NotBlank(message = "Venue information is required")
    @Size(max = 500, message = "Venue must not exceed 500 characters")
    private String venue;

    /** Optional — if null, tickets are purchasable from event creation. */
    private LocalDateTime salesStart;

    /** Optional — if null, tickets are purchasable until the event starts. */
    private LocalDateTime salesEnd;

    /**
     * FIX-E1-DTO1: Status intentionally removed.
     * All new events start as DRAFT. Use PUT /events/{id} to publish.
     * Field kept as comment to explain the absence to future developers.
     *
     * private EventStatusEnum status; // ← intentionally absent
     */

    @Min(value = 1, message = "maxCapacity must be at least 1 if provided")
    private Integer maxCapacity;

    @NotEmpty(message = "At least one ticket type is required")
    @Valid
    private List<CreateTicketTypeRequestDto> ticketTypes;

    /**
     * FIX-E8-DTO3: Cross-field ordering validation at DTO layer.
     * Returns false (triggering constraint violation) if end is not after start.
     */
    @AssertTrue(message = "Event end date must be after start date")
    public boolean isEndAfterStart() {
        if (start == null || end == null) return true; // individual @NotNull handles nulls
        return end.isAfter(start);
    }

    /**
     * FIX-E8-DTO3: Cross-field validation for sales window ordering.
     */
    @AssertTrue(message = "Sales end date must be after sales start date")
    public boolean isSalesEndAfterSalesStart() {
        if (salesStart == null || salesEnd == null) return true;
        return salesEnd.isAfter(salesStart);
    }
}