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
import com.event.tickets.services.EmailService;
import com.event.tickets.services.EventService;
import com.event.tickets.services.SystemUserProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * H-01 FIX: salesEnd guard now uses countActiveTicketsByEventId (excludes CANCELLED).
 * Previously used countByTicketTypeEventId which included CANCELLED tickets —
 * an event where all tickets were cancelled still blocked the salesEnd update.
 *
 * H-05 FIX: getSalesDashboard and getAttendeesReport now skip CANCELLED tickets.
 * Previously getTickets().size() and the revenue loop included CANCELLED tickets,
 * inflating sold counts and revenue totals.
 *
 * M-01 FIX: createEvent and updateEvent now validate date ordering.
 * end must be after start; salesEnd must be after salesStart when both are set.
 *
 * M-02 FIX: removeIf guard now counts only active (non-CANCELLED) tickets.
 * Previously getTickets().isEmpty() included CANCELLED tickets, blocking removal
 * of a ticket type where all sold tickets had been cancelled.
 *
 * M-08 FIX: EVENT_CREATED and EVENT_UPDATED AuditActions are now emitted.
 * These were defined in the enum but never used — organizer event operations
 * left no audit trail except EVENT_CANCELLED.
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

    @Override
    @Transactional
    public Event createEvent(UUID organizerId, CreateEventRequest event) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", organizerId)));

        // M-01 FIX: validate date ordering on create
        validateDateOrdering(event.getStart(), event.getEnd(),
                event.getSalesStart(), event.getSalesEnd());

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
        eventToCreate.setStatus(event.getStatus());
        eventToCreate.setMaxCapacity(event.getMaxCapacity());
        eventToCreate.setOrganizer(organizer);
        eventToCreate.setTicketTypes(ticketTypes);

        Event saved = eventRepository.save(eventToCreate);

        // M-08 FIX: emit EVENT_CREATED audit
        emitEventAudit(AuditAction.EVENT_CREATED, organizerId, saved,
                "eventName=" + saved.getName());

        return saved;
    }

    @Override
    public Page<Event> listEventsForOrganizer(UUID organizerId, Pageable pageable) {
        return eventRepository.findByOrganizerId(organizerId, pageable);
    }

    /**
     * L-26 FIX: Uses findByIdAndOrganizerId() — single DB call instead of two.
     * Previously called requireOrganizerAccess() (which fetches the event) then
     * findById() (which fetches it again). EventRepository already has a
     * findByIdAndOrganizerId query that does ownership + fetch atomically.
     */
    @Override
    public Optional<Event> getEventForOrganizer(UUID organizerId, UUID id) {
        return eventRepository.findByIdAndOrganizerId(id, organizerId);
    }

    @Override
    @Transactional
    public Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event) {
        if (event.getId() == null) throw new EventUpdateException("Event ID cannot be null");
        if (!id.equals(event.getId())) throw new EventUpdateException("Cannot update the ID of an event");

        authorizationService.requireOrganizerAccess(organizerId, id);

        Event existingEvent = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' does not exist", id)));

        // ENHANCED FIX: Block ALL modifications to cancelled events
        if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Cannot modify a cancelled event. " +
                    "All tickets for this event have been permanently cancelled " +
                    "and cannot be restored. " +
                    "To run a new event, please create a new event instead.");
        }

        // M-01 FIX: validate date ordering on update
        validateDateOrdering(event.getStart(), event.getEnd(),
                event.getSalesStart(), event.getSalesEnd());

        // H-01 FIX: use countActiveTicketsByEventId (excludes CANCELLED)
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

        // NEW FIX: Block re-publishing a cancelled event.
        // When an event is cancelled, all tickets are bulk-cancelled. Re-publishing
        // would allow new purchases but those old cancelled tickets would NOT be
        // restored — creating a confusing state where prior attendees can't enter
        // while new buyers can. Organizers should create a new event instead.
        if (EventStatusEnum.CANCELLED.equals(existingEvent.getStatus())
                && !EventStatusEnum.CANCELLED.equals(event.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Cannot change status of a cancelled event. " +
                            "All tickets were cancelled with the event. " +
                            "Please create a new event instead.");
        }

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

        // M-02 FIX: count only active (non-CANCELLED) tickets before blocking removal
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

        // M-08 FIX: emit EVENT_UPDATED audit
        emitEventAudit(AuditAction.EVENT_UPDATED, organizerId, savedEvent,
                "eventName=" + savedEvent.getName() + ",status=" + savedEvent.getStatus());

        if (becomingCancelled) {
            int cancelledCount = ticketRepository.bulkUpdateStatusByEventId(
                    id, TicketStatusEnum.PURCHASED, TicketStatusEnum.CANCELLED);
            log.info("Event '{}' cancelled — {} ticket(s) bulk-cancelled", id, cancelledCount);
            emitEventCancelledAudit(organizerId, savedEvent, cancelledCount);
            sendCancellationEmails(savedEvent);
        }

        return savedEvent;
    }

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

        // M-08 FIX: emit EVENT_DELETED audit before deletion
        eventRepository.findById(id).ifPresent(event -> {
            emitEventAudit(AuditAction.EVENT_DELETED, organizerId, event,
                    "eventName=" + event.getName());
            eventRepository.delete(event);
        });
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

    @Override
    public Map<String, Object> getSalesDashboard(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        Map<String, Object> dashboard = new HashMap<>();

        int totalTicketsSold = 0;
        BigDecimal totalRevenueBeforeDiscount = BigDecimal.ZERO;
        BigDecimal totalDiscountGiven = BigDecimal.ZERO;
        BigDecimal totalRevenueFinal = BigDecimal.ZERO;

        List<Map<String, Object>> ticketTypeStats = new ArrayList<>();

        for (TicketType ticketType : event.getTicketTypes()) {
            // H-05 FIX: only count and sum non-CANCELLED tickets
            List<Ticket> activeTickets = ticketType.getTickets().stream()
                    .filter(t -> !TicketStatusEnum.CANCELLED.equals(t.getStatus()))
                    .toList();

            int soldCount = activeTickets.size();
            BigDecimal revenueBeforeDiscount = BigDecimal.ZERO;
            BigDecimal discountGiven = BigDecimal.ZERO;
            BigDecimal revenueFinal = BigDecimal.ZERO;

            for (Ticket ticket : activeTickets) {
                BigDecimal originalPrice = ticket.getOriginalPrice() != null
                        ? ticket.getOriginalPrice() : ticketType.getPrice();
                BigDecimal discountAmount = ticket.getDiscountApplied() != null
                        ? ticket.getDiscountApplied() : BigDecimal.ZERO;
                BigDecimal pricePaid = ticket.getPricePaid() != null
                        ? ticket.getPricePaid() : ticketType.getPrice();

                revenueBeforeDiscount = revenueBeforeDiscount.add(originalPrice);
                discountGiven = discountGiven.add(discountAmount);
                revenueFinal = revenueFinal.add(pricePaid);
            }

            totalTicketsSold += soldCount;
            totalRevenueBeforeDiscount = totalRevenueBeforeDiscount.add(revenueBeforeDiscount);
            totalDiscountGiven = totalDiscountGiven.add(discountGiven);
            totalRevenueFinal = totalRevenueFinal.add(revenueFinal);

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
                        String.format("Event with ID '%s' not found", eventId)));

        List<Map<String, Object>> attendeesList = new ArrayList<>();
        for (TicketType ticketType : event.getTicketTypes()) {
            // H-05 FIX: skip CANCELLED tickets in attendees report too
            for (Ticket ticket : ticketType.getTickets()) {
                if (ticket.getPurchaser() == null) continue;
                if (TicketStatusEnum.CANCELLED.equals(ticket.getStatus())) continue;
                Map<String, Object> info = new HashMap<>();
                info.put("attendeeName", ticket.getPurchaser().getName());
                info.put("attendeeEmail", ticket.getPurchaser().getEmail());
                info.put("ticketType", ticketType.getName());
                info.put("ticketStatus", ticket.getStatus().toString());
                info.put("purchaseDate", ticket.getCreatedAt());
                info.put("validationCount", ticket.getValidations().size());
                attendeesList.add(info);
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("eventName", event.getName());
        report.put("totalAttendees", attendeesList.size());
        report.put("attendees", attendeesList);
        return report;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * M-01 FIX: Validates that start < end and salesStart < salesEnd when provided.
     * Previously createEvent and updateEvent accepted impossible date ranges silently.
     */
    private void validateDateOrdering(LocalDateTime start, LocalDateTime end,
                                      LocalDateTime salesStart, LocalDateTime salesEnd) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new InvalidBusinessStateException(
                    "Event end date must be after start date.");
        }
        if (salesStart != null && salesEnd != null && !salesEnd.isAfter(salesStart)) {
            throw new InvalidBusinessStateException(
                    "Sales end date must be after sales start date.");
        }
    }

    private void sendCancellationEmails(Event event) {
        try {
            Set<UUID> notified = new HashSet<>();
            for (TicketType tt : event.getTicketTypes()) {
                for (Ticket ticket : tt.getTickets()) {
                    if (ticket.getPurchaser() == null) continue;
                    UUID purchaserId = ticket.getPurchaser().getId();
                    if (notified.contains(purchaserId)) continue;
                    emailService.sendEventCancellationEmail(
                            ticket.getPurchaser().getEmail(),
                            ticket.getPurchaser().getName(),
                            event.getName());
                    notified.add(purchaserId);
                }
            }
            log.info("Cancellation emails sent to {} unique holder(s) for event '{}'",
                    notified.size(), event.getName());
        } catch (Exception e) {
            log.error("Failed to send cancellation emails for event '{}': {}",
                    event.getId(), e.getMessage());
        }
    }

    private void emitEventCancelledAudit(UUID organizerId, Event event, int ticketsCancelled) {
        try {
            User actor = userRepository.findById(organizerId)
                    .orElseGet(systemUserProvider::getSystemUser);
            HttpServletRequest request = getCurrentRequest();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.EVENT_CANCELLED).actor(actor).event(event)
                    .resourceType("EVENT").resourceId(event.getId())
                    .details(String.format("eventName=%s,ticketsBulkCancelled=%d",
                            event.getName(), ticketsCancelled))
                    .ipAddress(extractClientIpSafely(request))
                    .userAgent(extractUserAgentSafely(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit EVENT_CANCELLED audit: {}", e.getMessage());
        }
    }

    /** M-08 FIX: Generic audit emitter for EVENT_CREATED, EVENT_UPDATED, EVENT_DELETED. */
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

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    // NEW: Safe extraction for audit logging - never returns null
    private String extractClientIpSafely(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        return extractClientIp(request);
    }

    private String extractUserAgentSafely(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        return extractUserAgent(request);
    }
}