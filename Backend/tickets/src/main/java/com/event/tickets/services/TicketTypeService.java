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

    // Legacy single-ticket convenience method (delegates to quantity-aware version)
    default Ticket purchaseTicket(UUID userId, UUID ticketTypeId) {
        List<Ticket> tickets = purchaseTickets(userId, ticketTypeId, 1);
        return tickets.get(0);
    }

    // Organizer CRUD operations
    TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request);
    List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId);
    Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId);
    TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId, UpdateTicketTypeRequest request);
    void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId);
}
