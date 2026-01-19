package com.event.tickets.services;

import com.event.tickets.domain.entities.Discount;
import com.event.tickets.domain.dtos.CreateDiscountRequestDto;
import com.event.tickets.domain.dtos.DiscountResponseDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing discounts on ticket types.
 *
 * <p><strong>Business Rules:</strong>
 * <ul>
 *   <li>Only ONE active discount per ticket type at a time</li>
 *   <li>Discounts apply at purchase time only (never retroactive)</li>
 *   <li>Organizer must own both event and ticket type</li>
 * </ul>
 */
public interface DiscountService {

  /**
   * Creates a new discount for a ticket type.
   *
   * <p>Enforces:
   * <ul>
   *   <li>Organizer owns the event (via ticket type)</li>
   *   <li>Only one active discount per ticket type</li>
   *   <li>Valid date range (validTo > validFrom)</li>
   *   <li>Percentage range (0 < value <= 100) if PERCENTAGE type</li>
   * </ul>
   *
   * @param organizerId ID of the organizer creating the discount
   * @param eventId ID of the event (for ownership verification)
   * @param ticketTypeId ID of the ticket type
   * @param request Discount configuration
   * @return Created discount
   * @throws com.event.tickets.exceptions.UnauthorizedException if organizer doesn't own event
   * @throws com.event.tickets.exceptions.TicketTypeNotFoundException if ticket type not found
   * @throws com.event.tickets.exceptions.DiscountAlreadyExistsException if active discount exists
   * @throws IllegalArgumentException if validation fails
   */
  Discount createDiscount(
      UUID organizerId,
      UUID eventId,
      UUID ticketTypeId,
      CreateDiscountRequestDto request
  );

  /**
   * Updates an existing discount.
   *
   * <p>Enforces same rules as create, plus:
   * <ul>
   *   <li>Organizer owns the event</li>
   *   <li>Discount belongs to the specified ticket type</li>
   *   <li>If activating, no other active discount exists</li>
   * </ul>
   *
   * @param organizerId ID of the organizer
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param discountId ID of the discount to update
   * @param request Updated discount configuration
   * @return Updated discount
   */
  Discount updateDiscount(
      UUID organizerId,
      UUID eventId,
      UUID ticketTypeId,
      UUID discountId,
      CreateDiscountRequestDto request
  );

  /**
   * Deletes a discount.
   *
   * @param organizerId ID of the organizer
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param discountId ID of the discount to delete
   */
  void deleteDiscount(
      UUID organizerId,
      UUID eventId,
      UUID ticketTypeId,
      UUID discountId
  );

  /**
   * Gets a specific discount.
   *
   * @param organizerId ID of the organizer
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param discountId ID of the discount
   * @return Discount if found and organizer owns it
   */
  Optional<Discount> getDiscount(
      UUID organizerId,
      UUID eventId,
      UUID ticketTypeId,
      UUID discountId
  );

  /**
   * Lists all discounts for a ticket type.
   *
   * @param organizerId ID of the organizer
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @return List of discounts (active and inactive)
   */
  List<Discount> listDiscounts(
      UUID organizerId,
      UUID eventId,
      UUID ticketTypeId
  );

  /**
   * Finds the currently active and valid discount for a ticket type.
   * Used during purchase flow.
   *
   * @param ticketTypeId ID of the ticket type
   * @return Optional containing active discount, or empty if none
   */
  Optional<Discount> findActiveDiscount(UUID ticketTypeId);

  /**
   * Calculates final price after applying discount.
   *
   * @param basePrice Original ticket price
   * @param discount Discount to apply
   * @return Final price (never negative)
   */
  BigDecimal calculateFinalPrice(BigDecimal basePrice, Discount discount);
}
