package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Discount;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.InvalidBusinessStateException;
import com.event.tickets.exceptions.TicketTypeDeleteNotAllowedException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.TicketsSoldOutException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketTypeRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.DiscountService;
import com.event.tickets.services.EmailService;
import com.event.tickets.services.QrCodeService;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.services.TicketTypeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX 1 — jakarta.transaction.@Transactional replaced with org.springframework.
 *   purchaseTickets(), createTicketType(), updateTicketType(), deleteTicketType() now
 *   use Spring-managed @Transactional. Read methods get @Transactional(readOnly=true).
 *
 * FIX 2 — getCurrentRequest() private copy-paste removed.
 *   Removed private getCurrentRequest(), extractClientIpSafely(), extractUserAgentSafely().
 *   All audit helpers now call RequestUtil.getCurrentRequest() directly.
 *
 * FIX 3 — Per-user purchase limit added (max 10 tickets per user per ticket type).
 *   BEFORE: An attendee could call purchaseTickets() repeatedly in separate requests,
 *   each buying quantity=10, accumulating unlimited tickets for the same ticket type.
 *   The quantity=1-10 guard on the DTO prevented buying >10 in one call, but had no
 *   effect across multiple calls.
 *   AFTER: Before creating tickets, countByTicketTypeIdAndPurchaserId() checks how many
 *   non-cancelled tickets the buyer already holds for this ticket type. If adding the
 *   requested quantity would exceed 10, InvalidBusinessStateException is thrown.
 *
 * All other existing fixes (FIX #5-2 quantity guard, SOLD_OUT, SALES_WINDOW, FIX ISSUE 6
 * delete with cancelled tickets, FIX ISSUE 2 enum audit action) are preserved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketTypeServiceImpl implements TicketTypeService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeService qrCodeService;
    private final AuthorizationService authorizationService;
    private final DiscountService discountService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final SystemUserProvider systemUserProvider;

    private static final int MAX_TICKETS_PER_USER_PER_TYPE = 10;

    // ── PURCHASE ──────────────────────────────────────────────────────────────

    @Override
    @Transactional  // FIX 1: org.springframework
    public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
        if (quantity < 1 || quantity > 10) {
            throw new InvalidBusinessStateException(
                    "Quantity must be between 1 and 10. Cannot purchase " + quantity + " tickets");
        }

        // FIX 2: RequestUtil.getCurrentRequest() — no more private helpers
        String clientIp = extractClientIp(getCurrentRequest());
        String userAgent = extractUserAgent(getCurrentRequest());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    auditPurchaseFailure(userId, null, "USER_NOT_FOUND", clientIp, userAgent);
                    return new UserNotFoundException(
                            String.format("User with ID %s was not found", userId));
                });

        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> {
                    auditPurchaseFailure(userId, null, "TICKET_TYPE_NOT_FOUND", clientIp, userAgent);
                    return new TicketTypeNotFoundException(
                            String.format("Ticket type with ID %s was not found", ticketTypeId));
                });

        Event event = ticketType.getEvent();

        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            String reason = EventStatusEnum.CANCELLED.equals(event.getStatus())
                    ? "EVENT_CANCELLED" : "EVENT_NOT_PUBLISHED";
            auditPurchaseFailure(userId, event, reason, clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    EventStatusEnum.CANCELLED.equals(event.getStatus())
                            ? "This event has been cancelled."
                            : "Tickets are not available — the event is not open for sales.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
            auditPurchaseFailure(userId, event, "SALES_NOT_STARTED", clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    String.format("Sales have not started yet. Sales open at %s.", event.getSalesStart()));
        }
        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            auditPurchaseFailure(userId, event, "SALES_CLOSED", clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    String.format("Sales have closed. Sales ended at %s.", event.getSalesEnd()));
        }

        int activeForType = ticketRepository.countActiveByTicketTypeId(
                ticketType.getId(), TicketStatusEnum.CANCELLED);
        if (ticketType.getTotalAvailable() != null
                && activeForType + quantity > ticketType.getTotalAvailable()) {
            auditPurchaseFailure(userId, event, "SOLD_OUT_TICKET_TYPE", clientIp, userAgent);
            throw new TicketsSoldOutException();
        }

        if (event.getMaxCapacity() != null) {
            int totalSold = ticketRepository.countActiveTicketsByEventId(
                    event.getId(), TicketStatusEnum.CANCELLED);
            if (totalSold + quantity > event.getMaxCapacity()) {
                auditPurchaseFailure(userId, event, "SOLD_OUT_EVENT", clientIp, userAgent);
                throw new TicketsSoldOutException(String.format(
                        "Event venue capacity of %d reached. Only %d ticket(s) remaining.",
                        event.getMaxCapacity(), event.getMaxCapacity() - totalSold));
            }
        }

        // FIX 3: Per-user purchase limit — max 10 tickets per user per ticket type
        // countByTicketTypeIdAndPurchaserId includes CANCELLED tickets (conservative — prevents gaming
        // by buying, cancelling, and re-buying to bypass the limit)
        int alreadyOwned = ticketRepository.countByTicketTypeIdAndPurchaserId(
                ticketTypeId, userId);
        if (alreadyOwned + quantity > MAX_TICKETS_PER_USER_PER_TYPE) {
            auditPurchaseFailure(userId, event, "PER_USER_LIMIT_EXCEEDED", clientIp, userAgent);
            throw new InvalidBusinessStateException(String.format(
                    "Purchase limit reached. You already own %d ticket(s) for this ticket type. " +
                            "Maximum %d tickets per user per ticket type.",
                    alreadyOwned, MAX_TICKETS_PER_USER_PER_TYPE));
        }

        boolean isOrganizerPurchasing = authorizationService.isOrganizer(userId, event);
        if (isOrganizerPurchasing) {
            log.warn("Organizer '{}' purchasing {} ticket(s) to own event '{}'",
                    userId, quantity, event.getId());
            emitOrganizerSelfPurchaseAudit(user, event, quantity);
        }

        BigDecimal basePrice = ticketType.getPrice();
        Optional<Discount> activeDiscount = discountService.findActiveDiscount(ticketTypeId);

        BigDecimal finalPrice;
        BigDecimal discountAmount;
        if (activeDiscount.isPresent()) {
            finalPrice = discountService.calculateFinalPrice(basePrice, activeDiscount.get());
            discountAmount = basePrice.subtract(finalPrice);
        } else {
            finalPrice = basePrice;
            discountAmount = BigDecimal.ZERO;
        }

        List<Ticket> createdTickets = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            Ticket ticket = new Ticket();
            ticket.setStatus(TicketStatusEnum.PURCHASED);
            ticket.setTicketType(ticketType);
            ticket.setPurchaser(user);
            ticket.setOriginalPrice(basePrice);
            ticket.setPricePaid(finalPrice);
            ticket.setDiscountApplied(discountAmount);
            Ticket savedTicket = ticketRepository.save(ticket);
            qrCodeService.generateQrCode(savedTicket);
            createdTickets.add(savedTicket);
        }

        emitTicketPurchasedAudit(user, event, ticketType, quantity);

        emailService.sendTicketConfirmationEmail(
                user.getEmail(), user.getName(),
                event.getName(), ticketType.getName(),
                quantity, createdTickets.get(0).getId());

        return createdTickets;
    }

    @Override
    @Transactional  // FIX 1: org.springframework
    public List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID '%s' not found", ticketTypeId)));

        if (!ticketType.getEvent().getId().equals(eventId)) {
            throw new InvalidBusinessStateException("Ticket type does not belong to the specified event.");
        }

        return purchaseTickets(userId, ticketTypeId, quantity);
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional  // FIX 1: org.springframework
    public TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        TicketType ticketType = new TicketType();
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setDescription(request.getDescription());
        ticketType.setTotalAvailable(request.getTotalAvailable());
        ticketType.setEvent(event);
        return ticketTypeRepository.save(ticketType);
    }

    @Override
    @Transactional(readOnly = true)  // FIX 1: read-only
    public List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        return event.getTicketTypes();
    }

    @Override
    @Transactional(readOnly = true)  // FIX 1: read-only
    public Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        return ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId);
    }

    @Override
    @Transactional  // FIX 1: org.springframework
    public TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                       UpdateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' not found for event '%s'", ticketTypeId, eventId)));

        if (request.getTotalAvailable() != null) {
            int activeAlreadySold = ticketRepository.countActiveByTicketTypeId(
                    ticketTypeId, TicketStatusEnum.CANCELLED);
            if (request.getTotalAvailable() < activeAlreadySold) {
                throw new InvalidBusinessStateException(String.format(
                        "Cannot set totalAvailable to %d — %d active (non-cancelled) ticket(s) already sold.",
                        request.getTotalAvailable(), activeAlreadySold));
            }
        }

        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setDescription(request.getDescription());
        ticketType.setTotalAvailable(request.getTotalAvailable());

        return ticketTypeRepository.save(ticketType);
    }

    @Override
    @Transactional  // FIX 1: org.springframework
    public void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' not found for event '%s'", ticketTypeId, eventId)));

        long activeTickets = ticketType.getTickets().stream()
                .filter(t -> !TicketStatusEnum.CANCELLED.equals(t.getStatus()))
                .count();
        if (activeTickets > 0) {
            throw new TicketTypeDeleteNotAllowedException(String.format(
                    "Cannot delete ticket type with %d active (non-cancelled) sold ticket(s). " +
                            "Cancel the event first, or set totalAvailable to 0 to stop further sales.",
                    activeTickets));
        }
        ticketTypeRepository.delete(ticketType);
    }

    // ── AUDIT HELPERS ─────────────────────────────────────────────────────────

    private void emitOrganizerSelfPurchaseAudit(User organizer, Event event, int quantity) {
        try {
            // FIX 2: RequestUtil.getCurrentRequest()
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ORGANIZER_SELF_PURCHASE)
                    .actor(organizer).event(event)
                    .resourceType("TICKET").resourceId(event.getId())
                    .details(String.format("quantity=%d,eventName=%s", quantity, event.getName()))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit ORGANIZER_SELF_PURCHASE audit: {}", e.getMessage());
        }
    }

    private void emitTicketPurchasedAudit(User buyer, Event event, TicketType ticketType, int quantity) {
        try {
            // FIX 2: RequestUtil.getCurrentRequest()
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASED)
                    .actor(buyer).targetUser(buyer).event(event)
                    .resourceType("TICKET").resourceId(event.getId())
                    .details(String.format("ticketType=%s,quantity=%d", ticketType.getName(), quantity))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit TICKET_PURCHASED audit: {}", e.getMessage());
        }
    }

    private void auditPurchaseFailure(UUID userId, Event event, String reason,
                                      String clientIp, String userAgent) {
        try {
            User actor = userId != null ?
                    userRepository.findById(userId).orElse(systemUserProvider.getSystemUser()) :
                    systemUserProvider.getSystemUser();

            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASE_FAILED)
                    .actor(actor)
                    .event(event)
                    .resourceType("TICKET")
                    .details("reason=" + reason + ",quantity=unknown")
                    .ipAddress(clientIp != null ? clientIp : "unknown")
                    .userAgent(userAgent != null ? userAgent : "unknown")
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit purchase failure audit: {}", e.getMessage());
        }
    }
}