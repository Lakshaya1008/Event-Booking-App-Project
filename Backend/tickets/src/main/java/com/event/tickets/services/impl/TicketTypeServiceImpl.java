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
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

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

    /** Events in which ticket sales and ticket type modifications are meaningful. */
    private static final Set<EventStatusEnum> SALES_ACTIVE_STATUSES =
            Set.of(EventStatusEnum.DRAFT, EventStatusEnum.PUBLISHED);

    private static final int MAX_TICKETS_PER_USER_PER_TYPE = 10;

    // ── PUBLIC PURCHASE ────────────────────────────────────────────────────────

    /**
     * The ONLY public purchase entry point.
     * Verifies the ticket type belongs to the given event, then delegates to doPurchase().
     */
    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity) {
        if (quantity < 1 || quantity > 10) {
            throw new InvalidBusinessStateException(
                    "Quantity must be between 1 and 10. Cannot purchase " + quantity + " tickets");
        }

        String clientIp  = extractClientIp(getCurrentRequest());
        String userAgent = extractUserAgent(getCurrentRequest());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    auditPurchaseFailure(userId, null, "USER_NOT_FOUND", clientIp, userAgent);
                    return new UserNotFoundException(
                            String.format("User with ID %s was not found", userId));
                });

        Event event = eventRepository.findByIdWithLock(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> {
                    auditPurchaseFailure(userId, null, "TICKET_TYPE_NOT_FOUND", clientIp, userAgent);
                    return new TicketTypeNotFoundException(
                            String.format("Ticket type with ID %s was not found", ticketTypeId));
                });

        if (!ticketType.getEvent().getId().equals(eventId)) {
            auditPurchaseFailure(userId, event, "CROSS_EVENT_PURCHASE_ATTEMPT", clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    "Ticket type does not belong to the specified event.");
        }

        return doPurchase(user, event, ticketType, quantity, clientIp, userAgent);
    }

    // ── PRIVATE PURCHASE CORE ─────────────────────────────────────────────────

    private List<Ticket> doPurchase(User user, Event event, TicketType ticketType,
                                    int quantity, String clientIp, String userAgent) {
        UUID userId       = user.getId();
        UUID ticketTypeId = ticketType.getId();

        // Event status check
        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            String reason = EventStatusEnum.CANCELLED.equals(event.getStatus())
                    ? "EVENT_CANCELLED" : "EVENT_NOT_PUBLISHED";
            auditPurchaseFailure(userId, event, reason, clientIp, userAgent);
            throw new InvalidBusinessStateException(
                    EventStatusEnum.CANCELLED.equals(event.getStatus())
                            ? "This event has been cancelled."
                            : "Tickets are not available — the event is not open for sales.");
        }

        // Sales window check
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

        // Per-type capacity check (COUNT query — no entity loading)
        int activeForType = ticketRepository.countActiveByTicketTypeId(
                ticketTypeId, TicketStatusEnum.CANCELLED);
        if (ticketType.getTotalAvailable() != null
                && activeForType + quantity > ticketType.getTotalAvailable()) {
            auditPurchaseFailure(userId, event, "SOLD_OUT_TICKET_TYPE", clientIp, userAgent);
            throw new TicketsSoldOutException();
        }

        // Event-level capacity check (COUNT query — no entity loading).
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

        // Per-user limit check includes cancelled tickets to prevent buy-cancel-rebuy gaming.
        // Edge case of organiser-forced cancellation is handled via support.
        int alreadyOwned = ticketRepository.countByTicketTypeIdAndPurchaserId(ticketTypeId, userId);
        if (alreadyOwned + quantity > MAX_TICKETS_PER_USER_PER_TYPE) {
            auditPurchaseFailure(userId, event, "PER_USER_LIMIT_EXCEEDED", clientIp, userAgent);
            throw new InvalidBusinessStateException(String.format(
                    "Purchase limit reached. You already own %d ticket(s) for this ticket type " +
                            "(including any cancelled tickets). Maximum %d per user per ticket type.",
                    alreadyOwned, MAX_TICKETS_PER_USER_PER_TYPE));
        }

        if (authorizationService.isOrganizer(userId, event)) {
            log.warn("Organizer '{}' purchasing {} ticket(s) to own event '{}'",
                    userId, quantity, event.getId());
            emitOrganizerSelfPurchaseAudit(user, event, quantity);
        }

        // Pricing.
        BigDecimal basePrice = ticketType.getPrice();
        Optional<Discount> activeDiscount = discountService.findActiveDiscount(ticketTypeId);
        BigDecimal finalPrice;
        BigDecimal discountAmount;
        if (activeDiscount.isPresent()) {
            finalPrice     = discountService.calculateFinalPrice(basePrice, activeDiscount.get());
            discountAmount = basePrice.subtract(finalPrice);
        } else {
            finalPrice     = basePrice;
            discountAmount = BigDecimal.ZERO;
        }

        // Create tickets + QR codes.
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

        final String capturedUserEmail      = user.getEmail();
        final String capturedUserName       = user.getName();
        final String capturedEventName      = event.getName();
        final String capturedTicketTypeName = ticketType.getName();
        final int    capturedQuantity       = quantity;
        final UUID   capturedFirstTicketId  = createdTickets.get(0).getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailService.sendTicketConfirmationEmail(
                            capturedUserEmail, capturedUserName,
                            capturedEventName, capturedTicketTypeName,
                            capturedQuantity, capturedFirstTicketId);
                } catch (Exception e) {
                    log.error("Ticket confirmation email failed after commit (purchase committed). " +
                                    "userId={}, firstTicketId={}: {}",
                            user.getId(), capturedFirstTicketId, e.getMessage());
                }
            }
        });

        return createdTickets;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidBusinessStateException(
                    "Ticket type price must be provided and cannot be negative.");
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        if (!SALES_ACTIVE_STATUSES.contains(event.getStatus())) {
            throw new InvalidBusinessStateException(String.format(
                    "Cannot add ticket types to a %s event. " +
                            "Only DRAFT or PUBLISHED events can have ticket types added.",
                    event.getStatus()));
        }

        TicketType ticketType = new TicketType();
        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setDescription(request.getDescription());
        ticketType.setTotalAvailable(request.getTotalAvailable());
        ticketType.setEvent(event);
        return ticketTypeRepository.save(ticketType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketType> listTicketTypesForEvent(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        // Return a snapshot instead of the live Hibernate collection.
        return new ArrayList<>(event.getTicketTypes());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TicketType> getTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        return ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId);
    }

    @Override
    @Transactional
    public TicketType updateTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                       UpdateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        if (!SALES_ACTIVE_STATUSES.contains(event.getStatus())) {
            throw new InvalidBusinessStateException(String.format(
                    "Cannot update ticket types on a %s event. " +
                            "Only DRAFT or PUBLISHED events can have ticket types modified.",
                    event.getStatus()));
        }

        try {
            TicketType ticketType = ticketTypeRepository.findByIdAndEventIdWithLock(ticketTypeId, eventId)
                    .orElseThrow(() -> new TicketTypeNotFoundException(
                            String.format("Ticket type '%s' not found for event '%s'",
                                    ticketTypeId, eventId)));

            if (request.getTotalAvailable() != null) {
                int activeAlreadySold = ticketRepository.countActiveByTicketTypeId(
                        ticketTypeId, TicketStatusEnum.CANCELLED);
                if (request.getTotalAvailable() < activeAlreadySold) {
                    throw new InvalidBusinessStateException(String.format(
                            "Cannot set totalAvailable to %d — %d active (non-cancelled) " +
                                    "ticket(s) already sold.",
                            request.getTotalAvailable(), activeAlreadySold));
                }
            }

            ticketType.setName(request.getName());
            ticketType.setPrice(request.getPrice());
            ticketType.setDescription(request.getDescription());
            ticketType.setTotalAvailable(request.getTotalAvailable());
            return ticketTypeRepository.save(ticketType);

        } catch (ObjectOptimisticLockingFailureException e) {
            throw new InvalidBusinessStateException(
                    "This ticket type was concurrently modified. Please reload and retry your update.");
        }
    }

    @Override
    @Transactional
    public void deleteTicketType(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        TicketType ticketType = ticketTypeRepository.findByIdAndEventIdWithLock(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type '%s' not found for event '%s'",
                                ticketTypeId, eventId)));

        int activeTickets = ticketRepository.countActiveByTicketTypeId(
                ticketTypeId, TicketStatusEnum.CANCELLED);
        if (activeTickets > 0) {
            throw new TicketTypeDeleteNotAllowedException(String.format(
                    "Cannot delete ticket type with %d active (non-cancelled) sold ticket(s). " +
                            "Cancel the event first, or set totalAvailable to 0 to stop further sales.",
                    activeTickets));
        }

        ticketTypeRepository.delete(ticketType);
    }

    private void emitOrganizerSelfPurchaseAudit(User organizer, Event event, int quantity) {
        try {
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
            User actor = userId != null
                    ? userRepository.findById(userId).orElse(systemUserProvider.getSystemUser())
                    : systemUserProvider.getSystemUser();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_PURCHASE_FAILED)
                    .actor(actor).event(event)
                    .resourceType("TICKET")
                    .details("reason=" + reason)
                    .ipAddress(clientIp != null ? clientIp : "unknown")
                    .userAgent(userAgent != null ? userAgent : "unknown")
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit purchase failure audit: {}", e.getMessage());
        }
    }
}