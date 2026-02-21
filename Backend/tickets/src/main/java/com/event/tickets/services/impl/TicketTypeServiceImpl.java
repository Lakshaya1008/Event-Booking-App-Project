package com.event.tickets.services.impl;

import com.event.tickets.domain.CreateTicketTypeRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
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
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.DiscountService;
import com.event.tickets.services.QrCodeService;
import com.event.tickets.services.TicketTypeService;
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

/**
 * Ticket Type Service Implementation
 *
 * Fixes applied to purchaseTickets():
 * 1. Validates that the ticketTypeId belongs to the eventId in the URL (was never checked).
 * 2. Enforces event status = PUBLISHED before allowing purchase.
 * 3. Enforces salesStart / salesEnd window before allowing purchase.
 * 4. Fixed NPE risk in getAttendeesReport (purchaser null-checked in EventServiceImpl separately).
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

    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID ticketTypeId, int quantity) {
        return purchaseTickets(userId, null, ticketTypeId, quantity);
    }

    /**
     * Purchase tickets with full validation.
     *
     * @param userId       buyer
     * @param eventId      event ID from URL path — if provided, validated against ticketType.event
     * @param ticketTypeId the ticket type to purchase
     * @param quantity     how many tickets
     */
    @Override
    @Transactional
    public List<Ticket> purchaseTickets(UUID userId, UUID eventId, UUID ticketTypeId, int quantity) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(
                String.format("User with ID %s was not found", userId)
        ));

        TicketType ticketType = ticketTypeRepository.findByIdWithLock(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID %s was not found", ticketTypeId)
                ));

        Event event = ticketType.getEvent();

        // FIX #13: Validate that the ticketTypeId actually belongs to the eventId in the URL.
        // Without this check, an attacker could supply any eventId in the path and still
        // purchase tickets for an unrelated ticket type — bypassing any URL-level filtering.
        if (eventId != null && !event.getId().equals(eventId)) {
            throw new InvalidBusinessStateException(
                    String.format("Ticket type '%s' does not belong to event '%s'", ticketTypeId, eventId)
            );
        }

        // FIX #9: Only PUBLISHED events can sell tickets.
        // Without this, tickets could be sold for DRAFT or CANCELLED events.
        if (event.getStatus() != EventStatusEnum.PUBLISHED) {
            throw new InvalidBusinessStateException(
                    String.format("Event '%s' is not available for ticket purchase (status: %s)",
                            event.getName(), event.getStatus())
            );
        }

        // FIX #8: Enforce sales window.
        // salesStart and salesEnd are stored but were never checked.
        LocalDateTime now = LocalDateTime.now();

        if (event.getSalesStart() != null && now.isBefore(event.getSalesStart())) {
            throw new InvalidBusinessStateException(
                    String.format("Ticket sales for '%s' have not started yet. Sales open: %s",
                            event.getName(), event.getSalesStart())
            );
        }

        if (event.getSalesEnd() != null && now.isAfter(event.getSalesEnd())) {
            throw new InvalidBusinessStateException(
                    String.format("Ticket sales for '%s' have closed. Sales ended: %s",
                            event.getName(), event.getSalesEnd())
            );
        }

        // Capacity check (pessimistic lock already held on ticketType)
        int purchasedTickets = ticketRepository.countByTicketTypeId(ticketType.getId());
        Integer totalAvailable = ticketType.getTotalAvailable();

        if (purchasedTickets + quantity > totalAvailable) {
            throw new TicketsSoldOutException();
        }

        // Calculate pricing
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

    // ─── Organizer CRUD operations (unchanged logic, just cleaned up) ──────────

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
}
