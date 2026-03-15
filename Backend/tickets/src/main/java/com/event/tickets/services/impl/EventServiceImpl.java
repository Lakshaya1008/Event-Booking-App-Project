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
 * Event Service Implementation
 *
 * FIX #3: Ticket type removal in updateEventForOrganizer now checks for sold
 * tickets before allowing the removal. Previously, omitting a ticket type from
 * the update request silently cascade-deleted all its sold ticket records.
 *
 * FIX #5: getSalesDashboard revenue calculation uses BigDecimal instead of
 * double — prevents floating-point precision errors in financial totals.
 *
 * FIX #8: deleteEventForOrganizer and maxCapacity checks now use
 * countActiveTicketsByEventId (excludes CANCELLED) instead of countByTicketTypeEventId
 * (counts all). Previously blocked deletion even after all tickets were cancelled.
 *
 * FIX #16: updateEventForOrganizer now blocks setting maxCapacity below
 * the currently sold (non-cancelled) ticket count.
 *
 * EMAIL: sends cancellation notice to each unique ticket holder when an event
 * is cancelled. Uses a Set to avoid duplicate emails for multi-ticket buyers.
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
        if (event.getId() == null) throw new EventUpdateException("Event ID cannot be null");
        if (!id.equals(event.getId())) throw new EventUpdateException("Cannot update the ID of an event");

        authorizationService.requireOrganizerAccess(organizerId, id);

        Event existingEvent = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' does not exist", id)));

        if (event.getSalesEnd() != null
                && event.getSalesEnd().isBefore(LocalDateTime.now())
                && ticketRepository.countByTicketTypeEventId(id) > 0) {
            throw new InvalidBusinessStateException(
                    "Cannot set salesEnd to a past date when tickets have already been sold.");
        }

        // FIX #16: maxCapacity cannot go below currently sold (non-cancelled) count
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

        // FIX #3: Guard against removing ticket types that have sold tickets
        existingEvent.getTicketTypes().removeIf(tt -> {
            if (requestIds.contains(tt.getId())) return false;
            if (!tt.getTickets().isEmpty()) {
                throw new InvalidBusinessStateException(String.format(
                        "Cannot remove ticket type '%s' — %d ticket(s) already sold. " +
                                "Set totalAvailable to 0 to stop sales instead.",
                        tt.getName(), tt.getTickets().size()));
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

        // FIX #8: use countActiveTicketsByEventId (excludes CANCELLED) instead of
        // countByTicketTypeEventId (counts all). Previously blocked deletion even
        // after an organizer had correctly cancelled the event (bulk-cancelling tickets).
        int activeTickets = ticketRepository.countActiveTicketsByEventId(id, TicketStatusEnum.CANCELLED);
        if (activeTickets > 0) {
            throw new InvalidBusinessStateException(String.format(
                    "Cannot delete event '%s' — %d active ticket(s) exist. " +
                            "Cancel the event first to bulk-cancel all tickets.", id, activeTickets));
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

    @Override
    public Map<String, Object> getSalesDashboard(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        Map<String, Object> dashboard = new HashMap<>();

        // FIX #5: BigDecimal accumulators replace double — prevents financial precision errors
        int totalTicketsSold = 0;
        BigDecimal totalRevenueBeforeDiscount = BigDecimal.ZERO;
        BigDecimal totalDiscountGiven = BigDecimal.ZERO;
        BigDecimal totalRevenueFinal = BigDecimal.ZERO;

        List<Map<String, Object>> ticketTypeStats = new ArrayList<>();

        for (TicketType ticketType : event.getTicketTypes()) {
            int soldCount = ticketType.getTickets().size();

            BigDecimal revenueBeforeDiscount = BigDecimal.ZERO;
            BigDecimal discountGiven = BigDecimal.ZERO;
            BigDecimal revenueFinal = BigDecimal.ZERO;

            for (Ticket ticket : ticketType.getTickets()) {
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
            for (Ticket ticket : ticketType.getTickets()) {
                if (ticket.getPurchaser() == null) continue;
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
     * Sends cancellation emails to each UNIQUE ticket holder.
     * Uses a Set to avoid sending duplicate emails to buyers with multiple tickets.
     */
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
            log.info("Cancellation emails sent to {} unique ticket holder(s) for event '{}'",
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

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}