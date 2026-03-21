package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.EventStatusEnum;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX-E7-DTO: Added salesStart, salesEnd, salesOpen flag to the list view.
 *
 *   BEFORE: id, name, start, end, venue only — no ticket or sale info at all.
 *   A list of events with no sale status is nearly useless for an attendee
 *   trying to decide which events have tickets available.
 *
 *   AFTER: salesStart, salesEnd, salesOpen added so the attendee's event browser
 *   can show "On sale now", "Sale starts in 2 days", "Sale ended" labels
 *   without needing a separate detail request per event.
 *
 *   ticketTypes intentionally NOT included in the list view to keep
 *   responses lightweight. Use GET /published-events/{id} for ticket detail.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListPublishedEventResponseDto {

    private UUID id;
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private String venue;

    /** FIX-E7-DTO: When ticket sales open. Null = open from now. */
    private LocalDateTime salesStart;

    /** FIX-E7-DTO: When ticket sales close. Null = open until event starts. */
    private LocalDateTime salesEnd;

    /** FIX-E7-DTO: True if sales are currently open (server-computed for client convenience). */
    private boolean salesOpen;
}