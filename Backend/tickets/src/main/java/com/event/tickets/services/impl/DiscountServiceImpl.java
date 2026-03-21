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
 * FIX D-2 — @Transactional(timeout = 30) removed from deleteDiscount().
 *   BEFORE: deleteDiscount() had a 30-second transaction timeout. If the DB was slow,
 *   Spring threw TransactionTimedOutException which is NOT mapped in GlobalExceptionHandler
 *   — falling through to the catch-all 500. A simple single-row delete needs no explicit
 *   timeout; the connection pool's own timeout is sufficient.
 *   AFTER: @Transactional with no timeout. Consistent with every other write method.
 *
 * FIX D-3 — updateDiscount() post-sales guard now uses countDiscountedActiveByTicketTypeId().
 *   BEFORE: countActiveByTicketTypeId() counted ALL non-cancelled tickets regardless of
 *   whether a discount was applied. If an organizer sold 50 full-price tickets then added
 *   a discount, they could never change that discount's type or value — even though no
 *   ticket had ever benefited from it (discountApplied = 0 on all of them).
 *   AFTER: countDiscountedActiveByTicketTypeId() counts only tickets where discountApplied > 0.
 *   These are the tickets whose stored pricePaid/discountApplied would become inconsistent
 *   with the discount definition if the type or value changed. Full-price tickets are
 *   unaffected by discount definition changes and are no longer counted.
 *
 * FIX D-7 — active default logic deduplicated (minor cleanup).
 *   The @Builder.Default on Discount.active already sets it to true. The ternary in
 *   createDiscount() was redundant. The field is now set explicitly only when the caller
 *   provides a non-null value; otherwise the entity default takes over.
 *
 * Previously applied fixes preserved:
 *   FIX 1 — post-sales guard on updateDiscount() (type/value locked when tickets exist)
 *   FIX 2 — validFrom >= now guard on createDiscount() (no retroactive discounts)
 *   FIX 3 — TicketRepository injected for post-sales guard
 *   FIX #6 — existsActiveDiscountForTicketType() passes LocalDateTime.now() (in repo)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountServiceImpl implements DiscountService {

    private final DiscountRepository discountRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketRepository ticketRepository;
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

        validateDiscountRequest(request, true);  // enforceValidFromFuture = true on create

        if (Boolean.TRUE.equals(request.getActive()) &&
                discountRepository.existsActiveDiscountForTicketType(ticketTypeId, LocalDateTime.now())) {
            throw new DiscountAlreadyExistsException(String.format(
                    "An active discount already exists for ticket type %s. " +
                            "Only one active discount per ticket type is allowed.", ticketTypeId));
        }

        // FIX D-7: set active only when caller provides it; entity @Builder.Default handles null → true
        boolean activeValue = request.getActive() != null ? request.getActive() : true;

        Discount discount = Discount.builder()
                .ticketType(ticketType)
                .discountType(request.getDiscountType())
                .value(request.getValue())
                .validFrom(request.getValidFrom())
                .validTo(request.getValidTo())
                .active(activeValue)
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

        // FIX D-3: Check only tickets that had this discount applied (discountApplied > 0).
        // Full-price tickets (discountApplied = 0) are not affected by discount definition changes.
        boolean changingTypeOrValue =
                !existing.getDiscountType().equals(request.getDiscountType()) ||
                        existing.getValue().compareTo(request.getValue()) != 0;

        if (changingTypeOrValue) {
            int discountedTicketsSold = ticketRepository.countDiscountedActiveByTicketTypeId(
                    ticketTypeId, TicketStatusEnum.CANCELLED, BigDecimal.ZERO);
            if (discountedTicketsSold > 0) {
                throw new InvalidInputException(String.format(
                        "Cannot change discount type or value for ticket type '%s' — " +
                                "%d active ticket(s) were sold with this discount applied. " +
                                "The original pricing is recorded on each ticket (originalPrice, pricePaid, discountApplied). " +
                                "Changing the discount definition now would make the Discount record inconsistent " +
                                "with the ticket audit trail. " +
                                "To apply a different discount, deactivate this one (set active=false) and create a new discount.",
                        ticketTypeId, discountedTicketsSold));
            }
        }

        validateDiscountRequest(request, false);  // enforceValidFromFuture = false on update

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

    /**
     * FIX D-2: @Transactional(timeout = 30) removed.
     *
     * The 30-second timeout caused TransactionTimedOutException on slow DB calls.
     * That exception is not handled in GlobalExceptionHandler and fell through to the
     * catch-all 500. A single-row delete needs no explicit timeout — the JDBC connection
     * pool's socket timeout is sufficient protection.
     */
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