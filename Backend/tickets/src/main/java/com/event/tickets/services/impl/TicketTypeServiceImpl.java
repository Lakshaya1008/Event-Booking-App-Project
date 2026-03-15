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
 * FIX #9: updateTicketType() now blocks setting totalAvailable below
 * the number of tickets already sold.
 *
 * FIX #11: Removed redundant second ticketRepository.save(savedTicket) inside
 * the purchase loop. generateQrCode() does not modify the ticket, so the
 * re-save was a no-op UPDATE statement on every ticket purchased.
 *
 * FIX #15: Price is now BigDecimal throughout — the old BigDecimal.valueOf(ticketType.getPrice())
 * conversion from Double is gone since TicketType.price is now BigDecimal directly.
 *
 * EMAIL: sends ticket confirmation email after successful purchase.
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

    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID %s was not found", userId)));

        // Pessimistic lock — prevents concurrent overselling
        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID %s was not found", ticketTypeId)));

        Event event = ticketType.getEvent();

        if (!EventStatusEnum.PUBLISHED.equals(event.getStatus())) {
            String reason = EventStatusEnum.CANCELLED.equals(event.getStatus())
                    ? "This event has been cancelled."
                    : "Tickets are not available — the event is not open for sales.";
            throw new InvalidBusinessStateException(reason);
        }

        LocalDateTime now = LocalDateTime.now();
        if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
            throw new InvalidBusinessStateException(
                    String.format("Sales have not started yet. Sales open at %s.", event.getSalesStart()));
        }
        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            throw new InvalidBusinessStateException(
                    String.format("Sales have closed. Sales ended at %s.", event.getSalesEnd()));
        }

        int purchasedForType = ticketRepository.countByTicketTypeId(ticketType.getId());
        if (purchasedForType + quantity > ticketType.getTotalAvailable()) {
            throw new TicketsSoldOutException();
        }

        // FIX #8 (also in EventServiceImpl): use countActiveTicketsByEventId for capacity cap
        if (event.getMaxCapacity() != null) {
            int totalSold = ticketRepository.countActiveTicketsByEventId(
                    event.getId(), TicketStatusEnum.CANCELLED);
            if (totalSold + quantity > event.getMaxCapacity()) {
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

        // FIX #15: ticketType.getPrice() is now BigDecimal — no valueOf() conversion needed
        BigDecimal basePrice = ticketType.getPrice();
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
            // FIX #11: removed second redundant save() — generateQrCode does not modify
            // the ticket object, so re-saving produced a no-op UPDATE on every ticket
            createdTickets.add(savedTicket);
        }

        // Send confirmation email to purchaser
        emailService.sendTicketConfirmationEmail(
                user.getEmail(),
                user.getName(),
                event.getName(),
                ticketType.getName(),
                quantity,
                createdTickets.get(0).getId());

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

        // FIX #9: Block reducing totalAvailable below already-sold count
        if (request.getTotalAvailable() != null) {
            int alreadySold = ticketRepository.countByTicketTypeId(ticketTypeId);
            if (request.getTotalAvailable() < alreadySold) {
                throw new InvalidBusinessStateException(String.format(
                        "Cannot set totalAvailable to %d — %d ticket(s) already sold.",
                        request.getTotalAvailable(), alreadySold));
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
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.ORGANIZER_SELF_PURCHASE)
                    .actor(organizer).targetUser(organizer).event(event)
                    .resourceType("TICKET")
                    .details(String.format("organizerId=%s,eventId=%s,quantity=%d",
                            organizer.getId(), event.getId(), quantity))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit ORGANIZER_SELF_PURCHASE audit: {}", e.getMessage());
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}