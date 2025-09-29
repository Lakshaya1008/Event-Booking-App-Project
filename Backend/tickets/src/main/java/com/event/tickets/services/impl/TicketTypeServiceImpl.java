package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.TicketsSoldOutException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketTypeRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.QrCodeService;
import com.event.tickets.services.TicketTypeService;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

  private final UserRepository userRepository;
  private final EventRepository eventRepository;
  private final TicketTypeRepository ticketTypeRepository;
  private final TicketRepository ticketRepository;
  private final QrCodeService qrCodeService;

  @Override
  @Transactional
  public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(
        String.format("User with ID %s was not found", userId)
    ));

    TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
        .orElseThrow(() -> new TicketTypeNotFoundException(
            String.format("Ticket type with ID %s was not found", ticketTypeId)
        ));

    int purchasedTickets = ticketRepository.countByTicketTypeId(ticketType.getId());
    Integer totalAvailable = ticketType.getTotalAvailable();

    // Updated availability check for quantity
    if(purchasedTickets + quantity > totalAvailable) {
      throw new TicketsSoldOutException();
    }

    // Create multiple tickets
    List<Ticket> createdTickets = new ArrayList<>();
    for (int i = 0; i < quantity; i++) {
      Ticket ticket = new Ticket();
      ticket.setStatus(TicketStatusEnum.PURCHASED);
      ticket.setTicketType(ticketType);
      ticket.setPurchaser(user);

      Ticket savedTicket = ticketRepository.save(ticket);
      qrCodeService.generateQrCode(savedTicket);
      createdTickets.add(ticketRepository.save(savedTicket));
    }

    return createdTickets;
  }

  // Organizer CRUD operations
  @Override
  @Transactional
  public TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request) {
    // Verify organizer owns the event
    Event event = eventRepository.findByIdAndOrganizerId(eventId, organizerId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found for organizer '%s'", eventId, organizerId)
        ));

    TicketType ticketType = new TicketType();
    ticketType.setName(request.getName());
    ticketType.setPrice(request.getPrice());
    ticketType.setDescription(request.getDescription());
    ticketType.setTotalAvailable(request.getTotalAvailable());
    ticketType.setEvent(event);

    return ticketTypeRepository.save(ticketType);
  }

  @Override
  public List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId) {
    // Verify organizer owns the event
    Event event = eventRepository.findByIdAndOrganizerId(eventId, organizerId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found for organizer '%s'", eventId, organizerId)
        ));

    return event.getTicketTypes();
  }

  @Override
  public Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
    // Verify organizer owns the event
    eventRepository.findByIdAndOrganizerId(eventId, organizerId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found for organizer '%s'", eventId, organizerId)
        ));

    return ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId);
  }

  @Override
  @Transactional
  public TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId, UpdateTicketTypeRequest request) {
    // Verify organizer owns the event
    eventRepository.findByIdAndOrganizerId(eventId, organizerId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found for organizer '%s'", eventId, organizerId)
        ));

    TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
        .orElseThrow(() -> new TicketTypeNotFoundException(
            String.format("Ticket type with ID '%s' not found for event '%s'", ticketTypeId, eventId)
        ));

    ticketType.setName(request.getName());
    ticketType.setPrice(request.getPrice());
    ticketType.setDescription(request.getDescription());
    ticketType.setTotalAvailable(request.getTotalAvailable());

    return ticketTypeRepository.save(ticketType);
  }

  @Override
  @Transactional
  public void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
    // Verify organizer owns the event
    eventRepository.findByIdAndOrganizerId(eventId, organizerId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found for organizer '%s'", eventId, organizerId)
        ));

    TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
        .orElseThrow(() -> new TicketTypeNotFoundException(
            String.format("Ticket type with ID '%s' not found for event '%s'", ticketTypeId, eventId)
        ));

    // Check if there are any sold tickets - prevent deletion if tickets are sold
    if (!ticketType.getTickets().isEmpty()) {
      throw new IllegalStateException("Cannot delete ticket type with sold tickets");
    }

    ticketTypeRepository.delete(ticketType);
  }
}
