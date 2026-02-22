package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.EventUpdateException;
import com.event.tickets.exceptions.InvalidBusinessStateException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.EventService;
import com.event.tickets.services.SystemUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Event Service Implementation
 *
 * Handles event CRUD operations and reporting.
 * All authorization is delegated to AuthorizationService.
 *
 * Business Logic Applied:
 * 1. Event cancellation cascades to bulk-cancel all PURCHASED tickets + emits EVENT_CANCELLED audit
 * 2. maxCapacity field enforced at purchase time (see TicketTypeServiceImpl)
 * 3. salesEnd cannot be set to a past date if tickets have already been sold
 * 4. Delete is blocked if any tickets have been sold
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventServiceImpl implements EventService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final AuthorizationService authorizationService;
    private final TicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;

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
        eventToCreate.setMaxCapacity(event.getMaxCapacity());
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

        authorizationService.requireOrganizerAccess(organizerId, id);

        Event existingEvent = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' does not exist", id))
                );

        // ── Business Rule: salesEnd past-date guard ───────────────────────────────
        // If the organizer is setting salesEnd to a past timestamp AND tickets have
        // already been sold, block it — silent past-dating would make existing
        // ticket holders unable to understand what happened.
        // Before any sales, the organizer can freely adjust dates (e.g. closing early).
        if (event.getSalesEnd() != null
                && event.getSalesEnd().isBefore(LocalDateTime.now())
                && ticketRepository.countByTicketTypeEventId(id) > 0) {
            throw new InvalidBusinessStateException(
                    "Cannot set salesEnd to a past date when tickets have already been sold. "
                            + "To stop sales immediately, set status to CANCELLED instead."
            );
        }

        // ── Business Rule: Event cancellation cascade ─────────────────────────────
        // When an event transitions to CANCELLED, bulk-cancel all PURCHASED tickets
        // and emit an EVENT_CANCELLED audit event so there is a full trail.
        boolean becomingCancelled = EventStatusEnum.CANCELLED.equals(event.getStatus())
                && !EventStatusEnum.CANCELLED.equals(existingEvent.getStatus());

        existingEvent.setName(event.getName());
        existingEvent.setStart(event.getStart());
        existingEvent.setEnd(event.getEnd());
        existingEvent.setVenue(event.getVenue());
        existingEvent.setSalesStart(event.getSalesStart());
        existingEvent.setSalesEnd(event.getSalesEnd());
        existingEvent.setStatus(event.getStatus());
        existingEvent.setMaxCapacity(event.getMaxCapacity());

        // Ticket type reconciliation (existing logic — untouched)
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
                TicketType ticketTypeToCreate = new TicketType();
                ticketTypeToCreate.setName(ticketType.getName());
                ticketTypeToCreate.setPrice(ticketType.getPrice());
                ticketTypeToCreate.setDescription(ticketType.getDescription());
                ticketTypeToCreate.setTotalAvailable(ticketType.getTotalAvailable());
                ticketTypeToCreate.setEvent(existingEvent);
                existingEvent.getTicketTypes().add(ticketTypeToCreate);

            } else if (existingTicketTypesIndex.containsKey(ticketType.getId())) {
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

        Event savedEvent = eventRepository.save(existingEvent);

        // Cascade cancellation AFTER the event is persisted
        if (becomingCancelled) {
            int cancelledCount = ticketRepository.bulkUpdateStatusByEventId(
                    id, TicketStatusEnum.PURCHASED, TicketStatusEnum.CANCELLED
            );
            log.info("Event '{}' cancelled — {} ticket(s) bulk-cancelled", id, cancelledCount);

            emitEventCancelledAudit(organizerId, savedEvent, cancelledCount);
        }

        return savedEvent;
    }

    @Override
    @Transactional
    public void deleteEventForOrganizer(UUID organizerId, UUID id) {
        authorizationService.requireOrganizerAccess(organizerId, id);

        // Block deletion if any tickets have been sold — those attendees hold valid records
        int soldTicketCount = ticketRepository.countByTicketTypeEventId(id);
        if (soldTicketCount > 0) {
            throw new InvalidBusinessStateException(
                    String.format(
                            "Cannot delete event '%s' because %d ticket(s) have already been sold. "
                                    + "Cancel the event instead by setting status to CANCELLED.",
                            id, soldTicketCount
                    )
            );
        }

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

    // ── Sales dashboard & reports ─────────────────────────────────────────────

    @Override
    public Map<String, Object> getSalesDashboard(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)
                ));

        Map<String, Object> dashboard = new HashMap<>();

        int totalTicketsSold = 0;
        double totalRevenueBeforeDiscount = 0.0;
        double totalDiscountGiven = 0.0;
        double totalRevenueFinal = 0.0;

        List<Map<String, Object>> ticketTypeStats = new ArrayList<>();

        for (TicketType ticketType : event.getTicketTypes()) {
            int soldCount = ticketType.getTickets().size();

            double revenueBeforeDiscount = 0.0;
            double discountGiven = 0.0;
            double revenueFinal = 0.0;

            for (Ticket ticket : ticketType.getTickets()) {
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
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)
                ));

        Map<String, Object> report = new HashMap<>();
        List<Map<String, Object>> attendeesList = new ArrayList<>();

        for (TicketType ticketType : event.getTicketTypes()) {
            for (Ticket ticket : ticketType.getTickets()) {
                if (ticket.getPurchaser() == null) continue; // null-safe guard
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

    // ── Private helpers ───────────────────────────────────────────────────────

    private void emitEventCancelledAudit(UUID organizerId, Event event, int ticketsCancelled) {
        try {
            User actor = userRepository.findById(organizerId)
                    .orElseGet(systemUserProvider::getSystemUser);

            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.EVENT_CANCELLED)
                    .actor(actor)
                    .event(event)
                    .resourceType("EVENT")
                    .resourceId(event.getId())
                    .details(String.format("eventName=%s,ticketsBulkCancelled=%d",
                            event.getName(), ticketsCancelled))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();

            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            // Audit failure must never break the cancellation itself
            log.error("Failed to emit EVENT_CANCELLED audit for event '{}': {}", event.getId(), e.getMessage());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
