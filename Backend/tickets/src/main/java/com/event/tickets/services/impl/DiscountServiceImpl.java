package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.CreateDiscountRequestDto;
import com.event.tickets.domain.entities.Discount;
import com.event.tickets.domain.entities.DiscountType;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.exceptions.DiscountAlreadyExistsException;
import com.event.tickets.exceptions.DiscountNotFoundException;
import com.event.tickets.exceptions.InvalidInputException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.repositories.DiscountRepository;
import com.event.tickets.repositories.TicketTypeRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.DiscountService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FIX #6: existsActiveDiscountForTicketType() now receives LocalDateTime.now().
 *
 * BEFORE: the method signature had no :now param, only checked active=true.
 * An expired discount (active=true, validTo in the past) permanently blocked
 * creation of any new discount for that ticket type.
 *
 * AFTER: query also checks validTo > :now so expired discounts are ignored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public Discount createDiscount(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                   CreateDiscountRequestDto request) {
        log.info("Creating discount for ticket type {} by organizer {}", ticketTypeId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(
                        String.format("Ticket type with ID %s not found", ticketTypeId)));

        if (!ticketType.getEvent().getId().equals(eventId)) {
            throw new TicketTypeNotFoundException(
                    String.format("Ticket type %s does not belong to event %s", ticketTypeId, eventId));
        }

        validateDiscountRequest(request);

        // FIX #6: pass LocalDateTime.now() so expired discounts don't block new ones
        if (Boolean.TRUE.equals(request.getActive()) &&
                discountRepository.existsActiveDiscountForTicketType(ticketTypeId, LocalDateTime.now())) {
            throw new DiscountAlreadyExistsException(String.format(
                    "An active discount already exists for ticket type %s. " +
                            "Only one active discount per ticket type is allowed.", ticketTypeId));
        }

        Discount discount = Discount.builder()
                .ticketType(ticketType)
                .discountType(request.getDiscountType())
                .value(request.getValue())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .active(request.getActive() != null ? request.getActive() : true)
                .description(request.getDescription())
                .createdBy(organizerId)
                .build();

        Discount saved = discountRepository.save(discount);
        log.info("Created discount {} for ticket type {}", saved.getId(), ticketTypeId);
        return saved;
    }

    @Override
    @Transactional
    public Discount updateDiscount(UUID organizerId, UUID eventId, UUID ticketTypeId,
                                   UUID discountId, CreateDiscountRequestDto request) {
        log.info("Updating discount {} by organizer {}", discountId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Discount existing = discountRepository.findById(discountId)
                .orElseThrow(() -> new DiscountNotFoundException(
                        String.format("Discount with ID %s not found", discountId)));

        if (!existing.getTicketType().getId().equals(ticketTypeId)) {
            throw new DiscountNotFoundException(
                    String.format("Discount %s does not belong to ticket type %s", discountId, ticketTypeId));
        }

        validateDiscountRequest(request);

        // FIX #6: pass now() so expired discounts don't count as conflicts
        if (Boolean.TRUE.equals(request.getActive()) && !existing.isActive()) {
            if (discountRepository.existsActiveDiscountForTicketType(ticketTypeId, LocalDateTime.now())) {
                throw new DiscountAlreadyExistsException(String.format(
                        "Cannot activate discount %s. Another active discount exists for ticket type %s.",
                        discountId, ticketTypeId));
            }
        }

        existing.setDiscountType(request.getDiscountType());
        existing.setValue(request.getValue());
        existing.setValidFrom(request.getValidFrom());
        existing.setValidTo(request.getValidTo());
        if (request.getActive() != null) existing.setActive(request.getActive());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());

        Discount updated = discountRepository.save(existing);
        log.info("Updated discount {}", discountId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteDiscount(UUID organizerId, UUID eventId, UUID ticketTypeId, UUID discountId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Discount existing = discountRepository.findById(discountId)
                .orElseThrow(() -> new DiscountNotFoundException(
                        String.format("Discount with ID %s not found", discountId)));
        if (!existing.getTicketType().getId().equals(ticketTypeId)) {
            throw new DiscountNotFoundException(
                    String.format("Discount %s does not belong to ticket type %s", discountId, ticketTypeId));
        }
        discountRepository.delete(existing);
        log.info("Deleted discount {}", discountId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Discount> getDiscount(UUID organizerId, UUID eventId,
                                          UUID ticketTypeId, UUID discountId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Optional<Discount> discount = discountRepository.findById(discountId);
        if (discount.isPresent() && !discount.get().getTicketType().getId().equals(ticketTypeId)) {
            return Optional.empty();
        }
        return discount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Discount> listDiscounts(UUID organizerId, UUID eventId, UUID ticketTypeId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        return discountRepository.findAllByTicketTypeId(ticketTypeId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Discount> findActiveDiscount(UUID ticketTypeId) {
        return discountRepository.findActiveDiscount(ticketTypeId, LocalDateTime.now());
    }

    @Override
    public BigDecimal calculateFinalPrice(BigDecimal basePrice, Discount discount) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidInputException("Base price must be positive");
        if (discount == null) return basePrice;
        return discount.calculateFinalPrice(basePrice);
    }

    private void validateDiscountRequest(CreateDiscountRequestDto request) {
        if (!request.getValidTo().isAfter(request.getValidFrom()))
            throw new InvalidInputException("Valid to date must be after valid from date");
        if (request.getDiscountType() == DiscountType.PERCENTAGE) {
            if (request.getValue().compareTo(BigDecimal.ZERO) <= 0 ||
                    request.getValue().compareTo(BigDecimal.valueOf(100)) > 0)
                throw new InvalidInputException("Percentage discount must be between 0 and 100");
        }
        if (request.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            if (request.getValue().compareTo(BigDecimal.ZERO) <= 0)
                throw new InvalidInputException("Fixed amount discount must be positive");
        }
    }
}