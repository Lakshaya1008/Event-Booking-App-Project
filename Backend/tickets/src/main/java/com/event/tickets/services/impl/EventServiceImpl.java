package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
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
import com.event.tickets.services.EmailService;
import com.event.tickets.services.EventService;
import com.event.tickets.services.SystemUserProvider;
import java.math.BigDecimal;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * FIXES APPLIED:
 *
 * FIX-E1 — Status transition guard on CREATE and UPDATE.
 *   CREATE: only DRAFT is accepted. Organizers cannot publish or cancel on creation.
 *   UPDATE: enforced state machine:
 *     DRAFT      → PUBLISHED, CANCELLED
 *     PUBLISHED  → CANCELLED, COMPLETED
 *     CANCELLED  → (terminal, no transitions)
 *     COMPLETED  → (terminal, no transitions)
 *
 * FIX-E2 — getSalesDashboard() replaced in-memory ticket iteration with
 *   aggregate DB queries (SUM, COUNT grouped by ticket_type_id).
 *   Prevents loading thousands of Ticket entities into the JPA session.
 *   Requires new methods in TicketRepository (see TicketRepository.java).
 *
 * FIX-E3 — getAttendeesReport() uses a projection query instead of loading
 *   full Ticket + User entities into memory.
 *
 * FIX-E4 — sendCancellationEmails() replaced full collection load with a
 *   lightweight query returning only (email, name) pairs, deduped in SQL.
 *   Emails sent asynchronously via @Async — does not block the HTTP thread.
 *
 * FIX-E5 — COMPLETED auto-transition: a @Scheduled job marks PUBLISHED events
 *   as COMPLETED once event.end has passed.
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
    private final EmailService emailService;

    // ── Valid status transitions ──────────────────────────────────────────────

    private static final Map<EventStatusEnum, Set<EventStatusEnum>> VALID_TRANSITIONS = Map.of(
            EventStatusEnum.DRAFT,      Set.of(EventStatusEnum.PUBLISHED, EventStatusEnum.CANCELLED),
            EventStatusEnum.PUBLISHED,  Set.of(EventStatusEnum.CANCELLED, EventStatusEnum.COMPLETED),
            EventStatusEnum.CANCELLED,  Set.of(),
            EventStatusEnum.COMPLETED,  Set.of()
    );

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Event createEvent(UUID organizerId, CreateEventRequest event) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", organizerId)));

        // FIX-E1: Only DRAFT is allowed on creation.
        // Organizers must explicitly publish after reviewing the event.
        if (event.getStatus() != null && event.getStatus() != EventStatusEnum.DRAFT) {
            throw new InvalidBusinessStateException(
                    "New events must be created in DRAFT status. " +
                            "Use PUT /events/{id} to publish once ready.");
        }

        validateDateOrdering(event.getStart(), event.getEnd(),
                event.getSalesStart(), event.getSalesEnd(), true);

        Event eventToCreate = new Event();

        List<TicketType> ticketTypes = event.getTicketTypes().stream().map(tt -> {
            TicketType t = new TicketType();
            t.setName(tt.getName());
            t.setPrice(tt.getPrice());
            t.setDescription(tt.getDescription());
            t.setTotalAvailable(tt.getTotalAvailable());
            t.setEvent(eventToCreate);
            return t;
        }).toList();

        eventToCreate.setName(event.getName());
        eventToCreate.setStart(event.getStart());
        eventToCreate.setEnd(event.getEnd());
        eventToCreate.setVenue(event.getVenue());
        eventToCreate.setSalesStart(event.getSalesStart());
        eventToCreate.setSalesEnd(event.getSalesEnd());
        eventToCreate.setStatus(EventStatusEnum.DRAFT); // FIX-E1: always DRAFT
        eventToCreate.setMaxCapacity(event.getMaxCapacity());
        eventToCreate.setOrganizer(organizer);
        eventToCreate.setTicketTypes(ticketTypes);

        Event saved = eventRepository.save(eventToCreate);
        emitEventAudit(AuditAction.EVENT_CREATED, organizerId, saved, "eventName=" + saved.getName());
        return saved;
    }

    // ── LIST / GET ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<Event> listEventsForOrganizer(UUID organizerId, Pageable pageable) {
        return eventRepository.findByOrganizerId(organizerId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> getEventForOrganizer(UUID organizerId, UUID id) {
        return eventRepository.findByIdAndOrganizerId(id, organizerId);
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event) {
        if (event.getId() == null) throw new EventUpdateException("Event ID cannot be null");
        if (!id.equals(event.getId())) throw new EventUpdateException("Cannot update the ID of an event");

        authorizationService.requireOrganizerAccess(organizerId, id);

        Event existingEvent = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' does not exist", id)));

        // FIX-E1: Enforce terminal states
        if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Cannot modify a CANCELLED event. Create a new event instead.");
        }
        if (EventStatusEnum.COMPLETED.equals(existingEvent.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Cannot modify a COMPLETED event.");
        }

        // FIX-E1: Enforce status transition machine
        if (event.getStatus() != null &&
                !event.getStatus().equals(existingEvent.getStatus())) {
            Set<EventStatusEnum> allowed = VALID_TRANSITIONS.get(existingEvent.getStatus());
            if (!allowed.contains(event.getStatus())) {
                throw new InvalidBusinessStateException(String.format(
                        "Invalid status transition: %s → %s. Allowed transitions from %s: %s",
                        existingEvent.getStatus(), event.getStatus(),
                        existingEvent.getStatus(), allowed));
            }
        }

        validateDateOrdering(event.getStart(), event.getEnd(),
                event.getSalesStart(), event.getSalesEnd(), false);

        if (event.getSalesEnd() != null
                && event.getSalesEnd().isBefore(LocalDateTime.now())
                && ticketRepository.countActiveTicketsByEventId(id, TicketStatusEnum.CANCELLED) > 0) {
            throw new InvalidBusinessStateException(
                    "Cannot set salesEnd to a past date when active tickets have already been sold.");
        }

        if (event.getMaxCapacity() != null) {
            int activeSold = ticketRepository.countActiveTicketsByEventId(id, TicketStatusEnum.CANCELLED);
            if (event.getMaxCapacity() < activeSold) {
                throw new InvalidBusinessStateException(String.format(
                        "Cannot set maxCapacity to %d — %d non-cancelled ticket(s) already sold.",
                        event.getMaxCapacity(), activeSold));
            }
        }

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

        Set<UUID> requestIds = event.getTicketTypes().stream()
                .map(UpdateTicketTypeRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        existingEvent.getTicketTypes().removeIf(tt -> {
            if (requestIds.contains(tt.getId())) return false;
            long activeSoldForType = tt.getTickets().stream()
                    .filter(t -> !TicketStatusEnum.CANCELLED.equals(t.getStatus()))
                    .count();
            if (activeSoldForType > 0) {
                throw new InvalidBusinessStateException(String.format(
                        "Cannot remove ticket type '%s' — %d active ticket(s) already sold. " +
                                "Set totalAvailable to 0 to stop sales instead.",
                        tt.getName(), activeSoldForType));
            }
            return true;
        });

        Map<UUID, TicketType> existingIndex = existingEvent.getTicketTypes().stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));

        for (UpdateTicketTypeRequest tt : event.getTicketTypes()) {
            if (tt.getId() == null) {
                TicketType newTt = new TicketType();
                newTt.setName(tt.getName());
                newTt.setPrice(tt.getPrice());
                newTt.setDescription(tt.getDescription());
                newTt.setTotalAvailable(tt.getTotalAvailable());
                newTt.setEvent(existingEvent);
                existingEvent.getTicketTypes().add(newTt);
            } else if (existingIndex.containsKey(tt.getId())) {
                TicketType existing = existingIndex.get(tt.getId());
                existing.setName(tt.getName());
                existing.setPrice(tt.getPrice());
                existing.setDescription(tt.getDescription());
                existing.setTotalAvailable(tt.getTotalAvailable());
            } else {
                throw new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' does not exist", tt.getId()));
            }
        }

        Event savedEvent = eventRepository.save(existingEvent);
        emitEventAudit(AuditAction.EVENT_UPDATED, organizerId, savedEvent,
                "eventName=" + savedEvent.getName() + ",status=" + savedEvent.getStatus());

        if (becomingCancelled) {
            int cancelledPurchased = ticketRepository.bulkUpdateStatusByEventId(
                    id, TicketStatusEnum.PURCHASED, TicketStatusEnum.CANCELLED);
            int cancelledValidated = ticketRepository.bulkUpdateStatusByEventId(
                    id, TicketStatusEnum.VALIDATED, TicketStatusEnum.CANCELLED);
            int cancelledCount = cancelledPurchased + cancelledValidated;

            log.info("Event '{}' cancelled — {} ticket(s) bulk-cancelled", id, cancelledCount);
            emitEventCancelledAudit(organizerId, savedEvent, cancelledCount);
            // FIX-E4: async — does not block HTTP response
            sendCancellationEmailsAsync(savedEvent.getId(), savedEvent.getName());
        }

        return savedEvent;
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteEventForOrganizer(UUID organizerId, UUID id) {
        authorizationService.requireOrganizerAccess(organizerId, id);

        int activeTickets = ticketRepository.countActiveTicketsByEventId(id, TicketStatusEnum.CANCELLED);
        if (activeTickets > 0) {
            throw new InvalidBusinessStateException(String.format(
                    "Cannot delete event '%s' — %d active ticket(s) exist. " +
                            "Cancel the event first to bulk-cancel all tickets.", id, activeTickets));
        }

        eventRepository.findById(id).ifPresent(event -> {
            emitEventAudit(AuditAction.EVENT_DELETED, organizerId, event,
                    "eventName=" + event.getName());
            eventRepository.delete(event);
        });
    }

    // ── PUBLIC BROWSE ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<Event> listPublishedEvents(Pageable pageable) {
        return eventRepository.findByStatus(EventStatusEnum.PUBLISHED, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Event> searchPublishedEvents(String query, Pageable pageable) {
        return eventRepository.searchEvents(query, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> getPublishedEvent(UUID id) {
        return eventRepository.findByIdAndStatus(id, EventStatusEnum.PUBLISHED);
    }

    // ── REPORTS ───────────────────────────────────────────────────────────────

    /**
     * FIX-E2: Uses aggregate DB queries instead of loading all tickets into memory.
     * TicketRepository provides SUM/COUNT queries grouped by ticket_type_id.
     * The event and its ticket types are loaded (small), but ticket rows are
     * never materialized into Ticket entities.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getSalesDashboard(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        // Fetch all ticket type stats in ONE query — no ticket entity loading
        List<Object[]> statsRows = ticketRepository.findSalesStatsByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        // Map ticketTypeId → stats row for O(1) lookup
        Map<UUID, Object[]> statsMap = new HashMap<>();
        for (Object[] row : statsRows) {
            UUID ticketTypeId = (UUID) row[0];
            statsMap.put(ticketTypeId, row);
        }

        int totalTicketsSold = 0;
        BigDecimal totalRevenueBeforeDiscount = BigDecimal.ZERO;
        BigDecimal totalDiscountGiven = BigDecimal.ZERO;
        BigDecimal totalRevenueFinal = BigDecimal.ZERO;
        List<Map<String, Object>> ticketTypeStats = new ArrayList<>();

        for (TicketType ticketType : event.getTicketTypes()) {
            Object[] row = statsMap.get(ticketType.getId());

            int soldCount = row != null ? ((Number) row[1]).intValue() : 0;
            BigDecimal revenueBeforeDiscount = row != null && row[2] != null
                    ? (BigDecimal) row[2] : BigDecimal.ZERO;
            BigDecimal discountGiven = row != null && row[3] != null
                    ? (BigDecimal) row[3] : BigDecimal.ZERO;
            BigDecimal revenueFinal = row != null && row[4] != null
                    ? (BigDecimal) row[4] : BigDecimal.ZERO;

            totalTicketsSold += soldCount;
            totalRevenueBeforeDiscount = totalRevenueBeforeDiscount.add(revenueBeforeDiscount);
            totalDiscountGiven = totalDiscountGiven.add(discountGiven);
            totalRevenueFinal = totalRevenueFinal.add(revenueFinal);

            Integer remaining = ticketType.getTotalAvailable() != null
                    ? ticketType.getTotalAvailable() - soldCount : null;

            Map<String, Object> typeStats = new HashMap<>();
            typeStats.put("ticketTypeName", ticketType.getName());
            typeStats.put("basePrice", ticketType.getPrice());
            typeStats.put("totalAvailable", ticketType.getTotalAvailable());
            typeStats.put("sold", soldCount);
            typeStats.put("remaining", remaining);
            typeStats.put("revenueBeforeDiscount", revenueBeforeDiscount);
            typeStats.put("discountGiven", discountGiven);
            typeStats.put("revenueFinal", revenueFinal);
            ticketTypeStats.add(typeStats);
        }

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("eventName", event.getName());
        dashboard.put("totalTicketsSold", totalTicketsSold);
        dashboard.put("totalRevenueBeforeDiscount", totalRevenueBeforeDiscount);
        dashboard.put("totalDiscountGiven", totalDiscountGiven);
        dashboard.put("totalRevenueFinal", totalRevenueFinal);
        dashboard.put("ticketTypeBreakdown", ticketTypeStats);
        return dashboard;
    }

    /**
     * FIX-E3: Uses a projection query returning only the fields needed for the
     * attendees report — no full Ticket or User entities loaded into memory.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAttendeesReport(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        // Single query returning only the attendee-report fields — no entity loading
        List<Object[]> rows = ticketRepository.findAttendeeReportByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        List<Map<String, Object>> attendeesList = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> info = new HashMap<>();
            info.put("attendeeName",      row[0]);
            info.put("attendeeEmail",     row[1]);
            info.put("ticketType",        row[2]);
            info.put("ticketStatus",      row[3] != null ? row[3].toString() : null);
            info.put("purchaseDate",      row[4]);
            info.put("validationCount",   row[5] != null ? ((Number) row[5]).intValue() : 0);
            attendeesList.add(info);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("eventName", event.getName());
        report.put("totalAttendees", attendeesList.size());
        report.put("attendees", attendeesList);
        return report;
    }

    // ── SCHEDULED: auto-complete past events ──────────────────────────────────

    /**
     * FIX-E5: Marks PUBLISHED events as COMPLETED once event.end has passed.
     * Runs every hour. Without this, events stay PUBLISHED forever after their end date.
     * Requires @EnableScheduling on a @Configuration class.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void autoCompleteExpiredEvents() {
        List<Event> toComplete = eventRepository
                .findByStatusAndEndBefore(EventStatusEnum.PUBLISHED, LocalDateTime.now());

        if (toComplete.isEmpty()) return;

        log.info("Auto-completing {} past events", toComplete.size());
        for (Event event : toComplete) {
            event.setStatus(EventStatusEnum.COMPLETED);
            eventRepository.save(event);
            emitEventAudit(AuditAction.EVENT_UPDATED,
                    event.getOrganizer() != null ? event.getOrganizer().getId()
                            : systemUserProvider.getSystemUser().getId(),
                    event,
                    "autoCompleted=true,eventEnd=" + event.getEnd());
            log.info("Event '{}' auto-completed (end was {})", event.getId(), event.getEnd());
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private void validateDateOrdering(LocalDateTime start, LocalDateTime end,
                                      LocalDateTime salesStart, LocalDateTime salesEnd,
                                      boolean isCreate) {
        LocalDateTime now = LocalDateTime.now();

        if (isCreate) {
            if (salesStart != null && salesStart.isBefore(now))
                throw new InvalidBusinessStateException("Sales start date must be in the future.");
            if (start != null && start.isBefore(now))
                throw new InvalidBusinessStateException("Event start date must be in the future.");
            if (end != null && end.isBefore(now))
                throw new InvalidBusinessStateException("Event end date must be in the future.");
        }

        if (start != null && end != null && !end.isAfter(start))
            throw new InvalidBusinessStateException("Event end date must be after start date.");
        if (salesStart != null && salesEnd != null && !salesEnd.isAfter(salesStart))
            throw new InvalidBusinessStateException("Sales end date must be after sales start date.");
    }

    /**
     * FIX-E4: Sends cancellation emails asynchronously using a lightweight
     * query that returns only (email, name) pairs with DISTINCT on purchaser_id.
     * The HTTP thread is not blocked waiting for email delivery.
     * Requires @EnableAsync on a @Configuration class.
     */
    @Async
    public void sendCancellationEmailsAsync(UUID eventId, String eventName) {
        try {
            List<Object[]> purchasers = ticketRepository.findDistinctPurchasersByEventId(
                    eventId, TicketStatusEnum.CANCELLED);
            int count = 0;
            for (Object[] row : purchasers) {
                String email = (String) row[0];
                String name  = (String) row[1];
                try {
                    emailService.sendEventCancellationEmail(email, name, eventName);
                    count++;
                } catch (Exception e) {
                    log.error("Failed to send cancellation email to {}: {}", email, e.getMessage());
                }
            }
            log.info("Cancellation emails sent to {} purchaser(s) for event '{}'", count, eventId);
        } catch (Exception e) {
            log.error("Failed to send cancellation emails for event '{}': {}", eventId, e.getMessage());
        }
    }

    private void emitEventCancelledAudit(UUID organizerId, Event event, int ticketsCancelled) {
        try {
            User actor = userRepository.findById(organizerId)
                    .orElseGet(systemUserProvider::getSystemUser);
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.EVENT_CANCELLED).actor(actor).event(event)
                    .resourceType("EVENT").resourceId(event.getId())
                    .details(String.format("eventName=%s,ticketsBulkCancelled=%d",
                            event.getName(), ticketsCancelled))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit EVENT_CANCELLED audit: {}", e.getMessage());
        }
    }

    private void emitEventAudit(AuditAction action, UUID organizerId, Event event, String details) {
        try {
            User actor = userRepository.findById(organizerId)
                    .orElseGet(systemUserProvider::getSystemUser);
            AuditLog auditLog = AuditLog.builder()
                    .action(action).actor(actor).event(event)
                    .resourceType("EVENT").resourceId(event.getId())
                    .details(details)
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit {} audit for event {}: {}", action, event.getId(), e.getMessage());
        }
    }
}