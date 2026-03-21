package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.EventStatusEnum;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FIXES APPLIED:
 *
 * FIX-E7-DTO — Added salesStart, salesEnd, status, and ticketTypes with
 *   availability information so attendees can tell whether tickets are on sale.
 *
 *   BEFORE: Response had only id, name, start, end, venue, ticketTypes.
 *   An attendee browsing GET /published-events/{id} had no way to know:
 *     - Whether ticket sales are currently open
 *     - When sales open (salesStart) or close (salesEnd)
 *     - How many tickets remain per type (totalAvailable)
 *
 *   AFTER: salesStart, salesEnd added. status added (always PUBLISHED for this
 *   endpoint, but useful for client-side display). ticketTypes now uses
 *   GetPublishedEventDetailsTicketTypesResponseDto which includes totalAvailable
 *   and available (computed as totalAvailable - sold, or null if unlimited).
 *
 *   Note: organizer name/id intentionally omitted — public endpoint should not
 *   expose internal user IDs.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetPublishedEventDetailsResponseDto {

    private UUID id;
    private String name;
    private LocalDateTime start;
    private LocalDateTime end;
    private String venue;

    /** FIX-E7-DTO: When ticket sales open. Null = open from now. */
    private LocalDateTime salesStart;

    /** FIX-E7-DTO: When ticket sales close. Null = open until event starts. */
    private LocalDateTime salesEnd;

    /** FIX-E7-DTO: Always PUBLISHED for this endpoint — included for client display. */
    private EventStatusEnum status;

    /** FIX-E7-DTO: Whether sales are currently open (computed server-side for client convenience). */
    private boolean salesOpen;

    private List<GetPublishedEventDetailsTicketTypesResponseDto> ticketTypes = new ArrayList<>();
}