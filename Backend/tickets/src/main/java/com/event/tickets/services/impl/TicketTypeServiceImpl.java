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
import com.event.tickets.services.QrCodeService;
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
 * Ticket Type Service Implementation
 *
 * Handles ticket type CRUD operations and ticket purchases.
 * All authorization is delegated to AuthorizationService.
 *
 * Business Logic Applied:
 * 1. Purchase blocked if event is not PUBLISHED (covers CANCELLED, DRAFT, COMPLETED)
 * 2. Purchase blocked if outside the salesStart–salesEnd window
 * 3. Event-level maxCapacity enforced across all ticket types (if set)
 * 4. Organizer self-purchase flagged with ORGANIZER_SELF_PURCHASE audit event (not blocked)
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

    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID %s was not found", userId)
                ));

        // Pessimistic lock — prevents concurrent overselling
        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID %s was not found", ticketTypeId)
                ));

        Event event = ticketType.getEvent();

        // ── Business Rule 1: Event must be PUBLISHED ──────────────────────────────
        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            String reason = EventStatusEnum.CANCELLED.equals(event.getStatus())
                    ? "This event has been cancelled. No further tickets can be purchased."
                    : "Tickets are not available for purchase — the event is not open for sales.";
            throw new InvalidBusinessStateException(reason);
        }

        // ── Business Rule 2: Sales window enforcement ─────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
            throw new InvalidBusinessStateException(
                    String.format("Ticket sales have not started yet. Sales open at %s.", event.getSalesStart())
            );
        }
        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            throw new InvalidBusinessStateException(
                    String.format("Ticket sales have closed. Sales ended at %s.", event.getSalesEnd())
            );
        }

        // ── Business Rule 3: Per-ticket-type sold-out check ──────────────────────
        int purchasedForType = ticketRepository.countByTicketTypeId(ticketType.getId());
        if (purchasedForType + quantity > ticketType.getTotalAvailable()) {
            throw new TicketsSoldOutException();
        }

        // ── Business Rule 4: Event-level capacity cap (optional field) ────────────
        if (event.getMaxCapacity() != null) {
            int totalSoldForEvent = ticketRepository.countByTicketTypeEventId(event.getId());
            if (totalSoldForEvent + quantity > event.getMaxCapacity()) {
                throw new TicketsSoldOutException(
                        String.format(
                                "This event has reached its venue capacity of %d. Only %d ticket(s) remaining across all ticket types.",
                                event.getMaxCapacity(), event.getMaxCapacity() - totalSoldForEvent
                        )
                );
            }
        }

        // ── Business Rule 5: Organizer self-purchase — allow but audit ────────────
        boolean isOrganizerPurchasing = authorizationService.isOrganizer(userId, event);
        if (isOrganizerPurchasing) {
            log.warn("Organizer '{}' is purchasing {} ticket(s) to their own event '{}'",
                    userId, quantity, event.getId());
            emitOrganizerSelfPurchaseAudit(user, event, quantity);
        }

        // ── Calculate pricing ─────────────────────────────────────────────────────
        BigDecimal basePrice = BigDecimal.valueOf(ticketType.getPrice());
        Optional<Discount> activeDiscount = discountService.findActiveDiscount(ticketTypeId);

        BigDecimal finalPrice;
        BigDecimal discountAmount;

        if (activeDiscount.isPresent()) {
            Discount discount = activeDiscount.get();
            finalPrice = discountService.calculateFinalPrice(basePrice, discount);
            discountAmount = basePrice.subtract(finalPrice);
        } else {
            finalPrice = basePrice;
            discountAmount = BigDecimal.ZERO;
        }

        // ── Create tickets ────────────────────────────────────────────────────────
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
            createdTickets.add(ticketRepository.save(savedTicket));
        }

        return createdTickets;
    }

    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity) {
        // Validate eventId matches ticketType's event
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)
                ));

        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID '%s' not found", ticketTypeId)
                ));

        if (!ticketType.getEvent().getId().equals(eventId)) {
            throw new InvalidBusinessStateException("Ticket type does not belong to the specified event.");
        }

        // Delegate to existing purchase logic
        return purchaseTickets(userId, ticketTypeId, quantity);
    }

    // ── Organizer CRUD operations ─────────────────────────────────────────────

    @Override
    @Transactional
    public TicketType createTicketType(UUID organizerId, UUID eventId, CreateTicketTypeRequest request) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)
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
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)
                ));

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
        authorizationService.requireOrganizerAccess(organizerId, eventId);

        TicketType ticketType = ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID '%s' not found for event '%s'", ticketTypeId, eventId)
                ));

        if (!ticketType.getTickets().isEmpty()) {
            throw new TicketTypeDeleteNotAllowedException("Cannot delete ticket type with sold tickets");
        }

        ticketTypeRepository.delete(ticketType);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void emitOrganizerSelfPurchaseAudit(User organizer, Event event, int quantity) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ORGANIZER_SELF_PURCHASE)
                    .actor(organizer)
                    .targetUser(organizer)
                    .event(event)
                    .resourceType("TICKET")
                    .details(String.format("organizerId=%s,eventId=%s,quantity=%d,eventName=%s",
                            organizer.getId(), event.getId(), quantity, event.getName()))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();

            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            // Audit failure must never block the purchase
            log.error("Failed to emit ORGANIZER_SELF_PURCHASE audit: {}", e.getMessage());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
