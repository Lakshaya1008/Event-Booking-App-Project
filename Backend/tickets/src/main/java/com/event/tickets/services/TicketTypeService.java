package com.event.tickets.services;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeService {
  // Updated method to support quantity
  List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity);

  // Keep old method for backward compatibility (delegates to new method)
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
