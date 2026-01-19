package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.EventUpdateException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.EventService;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Event Service Implementation
 *
 * Handles event CRUD operations and reporting.
 * All authorization is delegated to AuthorizationService.
 */
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

  private final UserRepository userRepository;
  private final EventRepository eventRepository;
  private final AuthorizationService authorizationService;

  @Override
  @Transactional
  public Event createEvent(UUID organizerId, CreateEventRequest event) {
    User organizer = userRepository.findById(organizerId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", organizerId))
        );

    Event eventToCreate = new Event();

    List<TicketType> ticketTypesToCreate = event.getTicketTypes().stream().map(
        ticketType -> {
          TicketType ticketTypeToCreate = new TicketType();
          ticketTypeToCreate.setName(ticketType.getName());
          ticketTypeToCreate.setPrice(ticketType.getPrice());
          ticketTypeToCreate.setDescription(ticketType.getDescription());
          ticketTypeToCreate.setTotalAvailable(ticketType.getTotalAvailable());
          ticketTypeToCreate.setEvent(eventToCreate);
          return ticketTypeToCreate;
        }).toList();

    eventToCreate.setName(event.getName());
    eventToCreate.setStart(event.getStart());
    eventToCreate.setEnd(event.getEnd());
    eventToCreate.setVenue(event.getVenue());
    eventToCreate.setSalesStart(event.getSalesStart());
    eventToCreate.setSalesEnd(event.getSalesEnd());
    eventToCreate.setStatus(event.getStatus());
    eventToCreate.setOrganizer(organizer);
    eventToCreate.setTicketTypes(ticketTypesToCreate);

    return eventRepository.save(eventToCreate);
  }

  @Override
  public Page<Event> listEventsForOrganizer(UUID organizerId, Pageable pageable) {
    return eventRepository.findByOrganizerId(organizerId, pageable);
  }

  @Override
  public Optional<Event> getEventForOrganizer(UUID organizerId, UUID id) {
    // Centralized authorization: verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, id);

    return eventRepository.findById(id);
  }

  @Override
  @Transactional
  public Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event) {
    if (null == event.getId()) {
      throw new EventUpdateException("Event ID cannot be null");
    }

    if (!id.equals(event.getId())) {
      throw new EventUpdateException("Cannot update the ID of an event");
    }

    // Centralized authorization: verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, id);

    Event existingEvent = eventRepository.findById(id)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' does not exist", id))
        );

    existingEvent.setName(event.getName());
    existingEvent.setStart(event.getStart());
    existingEvent.setEnd(event.getEnd());
    existingEvent.setVenue(event.getVenue());
    existingEvent.setSalesStart(event.getSalesStart());
    existingEvent.setSalesEnd(event.getSalesEnd());
    existingEvent.setStatus(event.getStatus());

    Set<UUID> requestTicketTypeIds = event.getTicketTypes()
        .stream()
        .map(UpdateTicketTypeRequest::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    existingEvent.getTicketTypes().removeIf(existingTicketType ->
        !requestTicketTypeIds.contains(existingTicketType.getId())
    );

    Map<UUID, TicketType> existingTicketTypesIndex = existingEvent.getTicketTypes().stream()
        .collect(Collectors.toMap(TicketType::getId, Function.identity()));

    for (UpdateTicketTypeRequest ticketType : event.getTicketTypes()) {
      if (null == ticketType.getId()) {
        // Create
        TicketType ticketTypeToCreate = new TicketType();
        ticketTypeToCreate.setName(ticketType.getName());
        ticketTypeToCreate.setPrice(ticketType.getPrice());
        ticketTypeToCreate.setDescription(ticketType.getDescription());
        ticketTypeToCreate.setTotalAvailable(ticketType.getTotalAvailable());
        ticketTypeToCreate.setEvent(existingEvent);
        existingEvent.getTicketTypes().add(ticketTypeToCreate);

      } else if (existingTicketTypesIndex.containsKey(ticketType.getId())) {
        // Update
        TicketType existingTicketType = existingTicketTypesIndex.get(ticketType.getId());
        existingTicketType.setName(ticketType.getName());
        existingTicketType.setPrice(ticketType.getPrice());
        existingTicketType.setDescription(ticketType.getDescription());
        existingTicketType.setTotalAvailable(ticketType.getTotalAvailable());
      } else {
        throw new TicketTypeNotFoundException(String.format(
            "Ticket type with ID '%s' does not exist", ticketType.getId()
        ));
      }
    }

    return eventRepository.save(existingEvent);
  }

  @Override
  @Transactional
  public void deleteEventForOrganizer(UUID organizerId, UUID id) {
    // Centralized authorization: verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, id);

    eventRepository.findById(id).ifPresent(eventRepository::delete);
  }

  @Override
  public Page<Event> listPublishedEvents(Pageable pageable) {
    return eventRepository.findByStatus(EventStatusEnum.PUBLISHED, pageable);
  }

  @Override
  public Page<Event> searchPublishedEvents(String query, Pageable pageable) {
    return eventRepository.searchEvents(query, pageable);
  }

  @Override
  public Optional<Event> getPublishedEvent(UUID id) {
    return eventRepository.findByIdAndStatus(id, EventStatusEnum.PUBLISHED);
  }

  // Sales dashboard operations
  @Override
  public Map<String, Object> getSalesDashboard(UUID organizerId, UUID eventId) {
    // Centralized authorization: verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    Map<String, Object> dashboard = new HashMap<>();

    // Calculate totals with discount awareness
    int totalTicketsSold = 0;
    double totalRevenueBeforeDiscount = 0.0;
    double totalDiscountGiven = 0.0;
    double totalRevenueFinal = 0.0;

    List<Map<String, Object>> ticketTypeStats = new ArrayList<>();

    for (TicketType ticketType : event.getTicketTypes()) {
      int soldCount = ticketType.getTickets().size();

      // Aggregate pricing from actual tickets
      double revenueBeforeDiscount = 0.0;
      double discountGiven = 0.0;
      double revenueFinal = 0.0;

      for (Ticket ticket : ticketType.getTickets()) {
        // Use originalPrice if available, otherwise fall back to ticket type price
        double originalPrice = ticket.getOriginalPrice() != null
            ? ticket.getOriginalPrice().doubleValue()
            : ticketType.getPrice();

        double discountAmount = ticket.getDiscountApplied() != null
            ? ticket.getDiscountApplied().doubleValue()
            : 0.0;

        double pricePaid = ticket.getPricePaid().doubleValue();

        revenueBeforeDiscount += originalPrice;
        discountGiven += discountAmount;
        revenueFinal += pricePaid;
      }

      totalTicketsSold += soldCount;
      totalRevenueBeforeDiscount += revenueBeforeDiscount;
      totalDiscountGiven += discountGiven;
      totalRevenueFinal += revenueFinal;

      Map<String, Object> typeStats = new HashMap<>();
      typeStats.put("ticketTypeName", ticketType.getName());
      typeStats.put("basePrice", ticketType.getPrice());
      typeStats.put("totalAvailable", ticketType.getTotalAvailable());
      typeStats.put("sold", soldCount);
      typeStats.put("remaining", ticketType.getTotalAvailable() - soldCount);
      typeStats.put("revenueBeforeDiscount", revenueBeforeDiscount);
      typeStats.put("discountGiven", discountGiven);
      typeStats.put("revenueFinal", revenueFinal);

      ticketTypeStats.add(typeStats);
    }

    dashboard.put("eventName", event.getName());
    dashboard.put("totalTicketsSold", totalTicketsSold);
    dashboard.put("totalRevenueBeforeDiscount", totalRevenueBeforeDiscount);
    dashboard.put("totalDiscountGiven", totalDiscountGiven);
    dashboard.put("totalRevenueFinal", totalRevenueFinal);
    dashboard.put("ticketTypeBreakdown", ticketTypeStats);

    return dashboard;
  }

  @Override
  public Map<String, Object> getAttendeesReport(UUID organizerId, UUID eventId) {
    // Centralized authorization: verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    Map<String, Object> report = new HashMap<>();
    List<Map<String, Object>> attendeesList = new ArrayList<>();

    for (TicketType ticketType : event.getTicketTypes()) {
      for (Ticket ticket : ticketType.getTickets()) {
        Map<String, Object> attendeeInfo = new HashMap<>();
        attendeeInfo.put("attendeeName", ticket.getPurchaser().getName());
        attendeeInfo.put("attendeeEmail", ticket.getPurchaser().getEmail());
        attendeeInfo.put("ticketType", ticketType.getName());
        attendeeInfo.put("ticketStatus", ticket.getStatus().toString());
        attendeeInfo.put("purchaseDate", ticket.getCreatedAt());
        attendeeInfo.put("validationCount", ticket.getValidations().size());

        attendeesList.add(attendeeInfo);
      }
    }

    report.put("eventName", event.getName());
    report.put("totalAttendees", attendeesList.size());
    report.put("attendees", attendeesList);

    return report;
  }

}
