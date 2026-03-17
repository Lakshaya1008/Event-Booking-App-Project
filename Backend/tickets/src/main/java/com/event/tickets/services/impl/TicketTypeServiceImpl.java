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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * H-06 FIX: purchaseTickets() per-type availability check now uses
 * countActiveByTicketTypeId() (excludes CANCELLED) instead of countByTicketTypeId().
 * Previously, cancelled tickets permanently consumed slots — 100 available,
 * 10 sold, 3 cancelled still showed 10 used, blocking the 8th slot when only 7 remain.
 *
 * H-07 FIX: updateTicketType() sold-guard now uses countActiveByTicketTypeId().
 * Previously organizers could not raise totalAvailable back above the
 * CANCELLED-inclusive count even when real demand justified it.
 *
 * M-08 FIX: TICKET_PURCHASED AuditAction is now emitted after every purchase.
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

    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
        // FIX #3: Add audit logging for failed operations
        HttpServletRequest request = getCurrentRequest();
        String clientIp = extractClientIp(request);
        String userAgent = extractUserAgent(request);

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
                    ? "EVENT_CANCELLED"
                    : "EVENT_NOT_PUBLISHED";
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

        // H-06 FIX: use countActiveByTicketTypeId — CANCELLED slots are freed back up
        int activeForType = ticketRepository.countActiveByTicketTypeId(
                ticketType.getId(), TicketStatusEnum.CANCELLED);
        // NEW FIX: null totalAvailable means unlimited — treat as no cap
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

        // M-08 FIX: emit TICKET_PURCHASED audit
        emitTicketPurchasedAudit(user, event, ticketType, quantity);

        emailService.sendTicketConfirmationEmail(
                user.getEmail(), user.getName(),
                event.getName(), ticketType.getName(),
                quantity, createdTickets.get(0).getId());

        return createdTickets;
    }

    @Override
    @Transactional
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

    @Override
    @Transactional
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
    public List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        return event.getTicketTypes();
    }

    @Override
    public Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        return ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId);
    }

    @Override
    @Transactional
    public TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                       UpdateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' not found for event '%s'", ticketTypeId, eventId)));

        // H-07 FIX: use countActiveByTicketTypeId — CANCELLED tickets do not block raising totalAvailable
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
    @Transactional
    public void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' not found for event '%s'", ticketTypeId, eventId)));
        if (!ticketType.getTickets().isEmpty()) {
            throw new TicketTypeDeleteNotAllowedException("Cannot delete ticket type with sold tickets");
        }
        ticketTypeRepository.delete(ticketType);
    }

    private void emitOrganizerSelfPurchaseAudit(User organizer, Event event, int quantity) {
        try {
            HttpServletRequest request = getCurrentRequest();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ORGANIZER_SELF_PURCHASE)
                    .actor(organizer).event(event)
                    .resourceType("TICKET").resourceId(event.getId())
                    .details(String.format("quantity=%d,eventName=%s", quantity, event.getName()))
                    .ipAddress(extractClientIpSafely(request))
                    .userAgent(extractUserAgentSafely(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit ORGANIZER_SELF_PURCHASE audit: {}", e.getMessage());
        }
    }

    private void emitTicketPurchasedAudit(User buyer, Event event, TicketType ticketType, int quantity) {
        try {
            HttpServletRequest request = getCurrentRequest();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASED)
                    .actor(buyer).targetUser(buyer).event(event)
                    .resourceType("TICKET").resourceId(event.getId())
                    .details(String.format("ticketType=%s,quantity=%d", ticketType.getName(), quantity))
                    .ipAddress(extractClientIpSafely(request))
                    .userAgent(extractUserAgentSafely(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit TICKET_PURCHASED audit: {}", e.getMessage());
        }
    }

    // NEW: Helper to audit purchase failures
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