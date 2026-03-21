package com.event.tickets.services;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FIX-TT3 (BUG 5-1): The 3-arg purchaseTickets(userId, ticketTypeId, quantity) overload
 * has been REMOVED from this interface.
 *
 * WHY IT WAS DANGEROUS:
 * The 3-arg overload had no eventId parameter. Any internal caller using it bypassed the
 * cross-event ownership check that verifies the ticket type actually belongs to the event
 * in the URL. A crafted request could buy a ticket for Event A by calling Event B's endpoint
 * if the 3-arg overload was used.
 *
 * WHAT TO USE INSTEAD:
 * All purchase flows must go through purchaseTickets(userId, eventId, ticketTypeId, quantity).
 * This validates the ticket type belongs to the given event before proceeding.
 *
 * There is no "internal caller without eventId context" — that justification was incorrect.
 * Any caller that has a ticketTypeId can get its eventId from ticketType.getEvent().getId().
 */
public interface TicketTypeService {

    /**
     * Purchase tickets with mandatory event ownership validation.
     *
     * Enforces:
     * - ticketTypeId belongs to eventId (cross-event purchase prevention)
     * - Event status must be PUBLISHED
     * - Current time within salesStart/salesEnd window
     * - Per-type capacity not exceeded (active tickets only)
     * - Event maxCapacity not exceeded (active tickets only)
     * - Per-user limit not exceeded (max 10 per user per type, including cancelled)
     *
     * Uses PESSIMISTIC_WRITE lock on the ticket type row to prevent oversell
     * on concurrent purchase requests.
     */
    List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity);

    // ── Organizer CRUD operations ─────────────────────────────────────────────

    TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request);

    List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId);

    Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId);

    TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                UpdateTicketTypeRequest request);

    void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId);
}