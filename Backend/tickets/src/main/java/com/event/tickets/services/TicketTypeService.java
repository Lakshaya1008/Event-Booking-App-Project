package com.event.tickets.services;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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