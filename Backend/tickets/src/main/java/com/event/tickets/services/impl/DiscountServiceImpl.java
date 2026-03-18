package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.CreateDiscountRequestDto;
import com.event.tickets.domain.entities.Discount;
import com.event.tickets.domain.entities.DiscountType;
import com.event.tickets.domain.entities.TicketStatusEnum;
import com.event.tickets.domain.entities.TicketType;
import com.event.tickets.exceptions.DiscountAlreadyExistsException;
import com.event.tickets.exceptions.DiscountNotFoundException;
import com.event.tickets.exceptions.InvalidInputException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.repositories.DiscountRepository;
import com.event.tickets.repositories.TicketRepository;
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
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX 1 — Post-sales guard on updateDiscount(): type and value immutable once tickets sold.
 *   BEFORE: An organizer could call PUT /discounts/{id} to change discountType from
 *   PERCENTAGE 20 to FIXED_AMOUNT 5 after tickets had already been sold.
 *   The Ticket records store originalPrice, pricePaid, and discountApplied — calculated
 *   under the original PERCENTAGE 20 discount. After the update, the Discount entity
 *   shows FIXED_AMOUNT 5 but all sold tickets show a percentage-based discount amount.
 *   A compliance auditor or refund calculation reading the Discount record would get
 *   wrong numbers.
 *   AFTER: If any non-cancelled tickets exist for the ticket type with discountApplied > 0,
 *   changing discountType or value is blocked. The organizer must deactivate the discount
 *   and create a new one. Description and validFrom/validTo adjustments are still allowed.
 *
 * FIX 2 — validFrom >= now check on createDiscount().
 *   BEFORE: An organizer could create a discount with validFrom in the past, meaning the
 *   discount would appear to have started 3 days ago — but no tickets in that past window
 *   would have received the discount (purchases already completed without it). This creates
 *   a confusing discount history.
 *   AFTER: validFrom must be >= LocalDateTime.now() for new discounts. This rule does NOT
 *   apply to updateDiscount() — an existing discount whose period has started can still
 *   be updated.
 *
 * FIX 3 — TicketRepository injected for the post-sales guard.
 *   countActiveByTicketTypeId() is used to check if any non-cancelled tickets exist for
 *   the ticket type. This is the same query used by TicketTypeServiceImpl — consistent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;  // FIX 3: injected for post-sales guard
    private final AuthorizationService authorizationService;

    // ── CREATE ────────────────────────────────────────────────────────────────

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

        validateDiscountRequest(request, true);  // true = enforce future validFrom

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

    // ── UPDATE ────────────────────────────────────────────────────────────────

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

        // FIX 1: Block type/value changes if tickets have already been sold under this discount.
        // Description and date range changes are still allowed.
        boolean changingTypeOrValue =
                !existing.getDiscountType().equals(request.getDiscountType()) ||
                        existing.getValue().compareTo(request.getValue()) != 0;

        if (changingTypeOrValue) {
            int activeSoldCount = ticketRepository.countActiveByTicketTypeId(
                    ticketTypeId, TicketStatusEnum.CANCELLED);
            if (activeSoldCount > 0) {
                throw new InvalidInputException(String.format(
                        "Cannot change discount type or value for ticket type '%s' — " +
                                "%d active ticket(s) were sold under this discount. " +
                                "The original pricing is recorded on each ticket (originalPrice, pricePaid, discountApplied). " +
                                "Changing the discount definition now would make the Discount record inconsistent " +
                                "with the ticket audit trail. " +
                                "To apply a different discount, deactivate this one (set active=false) and create a new discount.",
                        ticketTypeId, activeSoldCount));
            }
        }

        validateDiscountRequest(request, false);  // false = do NOT enforce future validFrom on update

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

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional(timeout = 30)
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

    // ── READ ──────────────────────────────────────────────────────────────────

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

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * Validates discount request fields.
     *
     * @param enforceValidFromFuture true on CREATE — validFrom must be in the future.
     *                               false on UPDATE — validFrom may be in the past
     *                               (discount period may have already started).
     */
    private void validateDiscountRequest(CreateDiscountRequestDto request, boolean enforceValidFromFuture) {
        if (!request.getValidTo().isAfter(request.getValidFrom()))
            throw new InvalidInputException("Valid to date must be after valid from date");

        // FIX 2: New discounts must start in the future — no retroactive discounts
        if (enforceValidFromFuture && request.getValidFrom().isBefore(LocalDateTime.now())) {
            throw new InvalidInputException(
                    "Valid from date must be in the future for new discounts. " +
                            "A discount cannot start in the past — tickets sold before validFrom would have paid full price.");
        }

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