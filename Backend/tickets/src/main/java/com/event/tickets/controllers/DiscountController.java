package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.CreateDiscountRequestDto;
import com.event.tickets.domain.dtos.DiscountResponseDto;
import com.event.tickets.domain.entities.Discount;
import com.event.tickets.mappers.DiscountMapper;
import com.event.tickets.services.DiscountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for managing discounts.
 *
 * <p><strong>Access Control:</strong>
 * <ul>
 *   <li>All endpoints require ORGANIZER role</li>
 *   <li>Organizer must own the event (enforced by service layer)</li>
 * </ul>
 *
 * <p><strong>Base Path:</strong>
 * {@code /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts}
 *
 * <p><strong>Business Rules:</strong>
 * <ul>
 *   <li>Only ONE active discount per ticket type at a time</li>
 *   <li>Discounts apply at purchase time only (never retroactive)</li>
 *   <li>Two discount types: PERCENTAGE (0-100%) or FIXED_AMOUNT (currency)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts")
@RequiredArgsConstructor
@Slf4j
public class DiscountController {

  private final DiscountService discountService;
  private final DiscountMapper discountMapper;

  /**
   * Creates a new discount for a ticket type.
   *
   * <p><strong>Authorization:</strong> ORGANIZER must own the event
   *
   * <p><strong>Business Rules:</strong>
   * <ul>
   *   <li>Only one active discount per ticket type</li>
   *   <li>validTo must be after validFrom</li>
   *   <li>PERCENTAGE: value must be 0-100</li>
   *   <li>FIXED_AMOUNT: value must be positive</li>
   * </ul>
   *
   * @param jwt Authenticated user's JWT token
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param request Discount configuration
   * @return Created discount
   */
  @PostMapping
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<DiscountResponseDto> createDiscount(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @PathVariable UUID ticketTypeId,
      @Valid @RequestBody CreateDiscountRequestDto request
  ) {
    UUID organizerId = UUID.fromString(jwt.getSubject());

    log.info("Organizer {} creating discount for ticket type {} in event {}",
        organizerId, ticketTypeId, eventId);

    Discount discount = discountService.createDiscount(
        organizerId,
        eventId,
        ticketTypeId,
        request
    );

    DiscountResponseDto response = discountMapper.toResponseDto(discount);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  /**
   * Updates an existing discount.
   *
   * <p><strong>Authorization:</strong> ORGANIZER must own the event
   *
   * @param jwt Authenticated user's JWT token
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param discountId ID of the discount to update
   * @param request Updated discount configuration
   * @return Updated discount
   */
  @PutMapping("/{discountId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<DiscountResponseDto> updateDiscount(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @PathVariable UUID ticketTypeId,
      @PathVariable UUID discountId,
      @Valid @RequestBody CreateDiscountRequestDto request
  ) {
    UUID organizerId = UUID.fromString(jwt.getSubject());

    log.info("Organizer {} updating discount {} for ticket type {} in event {}",
        organizerId, discountId, ticketTypeId, eventId);

    Discount discount = discountService.updateDiscount(
        organizerId,
        eventId,
        ticketTypeId,
        discountId,
        request
    );

    DiscountResponseDto response = discountMapper.toResponseDto(discount);
    return ResponseEntity.ok(response);
  }

  /**
   * Deletes a discount.
   *
   * <p><strong>Authorization:</strong> ORGANIZER must own the event
   *
   * @param jwt Authenticated user's JWT token
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param discountId ID of the discount to delete
   * @return No content
   */
  @DeleteMapping("/{discountId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<Void> deleteDiscount(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @PathVariable UUID ticketTypeId,
      @PathVariable UUID discountId
  ) {
    UUID organizerId = UUID.fromString(jwt.getSubject());

    log.info("Organizer {} deleting discount {} for ticket type {} in event {}",
        organizerId, discountId, ticketTypeId, eventId);

    discountService.deleteDiscount(organizerId, eventId, ticketTypeId, discountId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Gets a specific discount.
   *
   * <p><strong>Authorization:</strong> ORGANIZER must own the event
   *
   * @param jwt Authenticated user's JWT token
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @param discountId ID of the discount
   * @return Discount details
   */
  @GetMapping("/{discountId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<DiscountResponseDto> getDiscount(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @PathVariable UUID ticketTypeId,
      @PathVariable UUID discountId
  ) {
    UUID organizerId = UUID.fromString(jwt.getSubject());

    Discount discount = discountService.getDiscount(
        organizerId,
        eventId,
        ticketTypeId,
        discountId
    ).orElseThrow(() -> new com.event.tickets.exceptions.DiscountNotFoundException(
        String.format("Discount %s not found", discountId)
    ));

    DiscountResponseDto response = discountMapper.toResponseDto(discount);
    return ResponseEntity.ok(response);
  }

  /**
   * Lists all discounts for a ticket type.
   *
   * <p><strong>Authorization:</strong> ORGANIZER must own the event
   *
   * @param jwt Authenticated user's JWT token
   * @param eventId ID of the event
   * @param ticketTypeId ID of the ticket type
   * @return List of discounts (active and inactive)
   */
  @GetMapping
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<List<DiscountResponseDto>> listDiscounts(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @PathVariable UUID ticketTypeId
  ) {
    UUID organizerId = UUID.fromString(jwt.getSubject());

    List<Discount> discounts = discountService.listDiscounts(
        organizerId,
        eventId,
        ticketTypeId
    );

    List<DiscountResponseDto> response = discounts.stream()
        .map(discountMapper::toResponseDto)
        .toList();

    return ResponseEntity.ok(response);
  }
}
