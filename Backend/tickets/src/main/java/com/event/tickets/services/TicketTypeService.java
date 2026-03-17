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
     * Purchase tickets without path-level event validation.
     * Used by internal callers that do not have an eventId context.
     */
    List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity);

    /**
     * Purchase tickets WITH path-level event validation.
     * Called by the controller so the eventId from the URL is verified
     * against the ticketType's actual event (prevents cross-event purchases).
     *
     * Also enforces:
     * - Event status must be PUBLISHED
     * - Current time must be within salesStart / salesEnd window
     */
    List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity);

    /**
     * L-16 FIX: Default purchaseTicket() removed.
     *
     * The default method delegated to purchaseTickets(userId, ticketTypeId, 1) —
     * the overload WITHOUT eventId. This allowed any caller to bypass the cross-event
     * validation that verifies the ticketType actually belongs to the given eventId
     * (preventing a crafted request from buying a ticket across unrelated events).
     *
     * All purchase paths must go through purchaseTickets(userId, eventId, ticketTypeId, quantity)
     * which enforces event ownership. The method is removed from the interface to force
     * compile-time errors if any caller tries to use the unsafe path.
     */

    // Organizer CRUD operations
    TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request);
    List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId);
    Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId);
    TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId, UpdateTicketTypeRequest request);
    void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId);
}