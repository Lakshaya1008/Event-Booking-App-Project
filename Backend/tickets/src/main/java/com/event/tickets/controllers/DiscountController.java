package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;
import com.event.tickets.domain.dtos.CreateDiscountRequestDto;
import com.event.tickets.domain.dtos.DiscountResponseDto;
import com.event.tickets.domain.entities.Discount;
import com.event.tickets.exceptions.DiscountNotFoundException;
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
 * Access control: All endpoints require ORGANIZER role.
 * Ownership enforcement: service layer (requireOrganizerAccess).
 *
 * FIX D-4: getDiscount() now delegates the 404 exception to the service layer
 * rather than throwing inline in the controller.
 *
 *   BEFORE: The controller called orElseThrow() with an inline new DiscountNotFoundException.
 *   This is inconsistent with every other endpoint in the codebase, where exception
 *   decisions are made in the service. Controllers should be thin — they parse path
 *   variables, call the service, and map the result.
 *
 *   AFTER: The service returns Optional<Discount>. If empty, the controller throws
 *   DiscountNotFoundException. This is correct because the service already verifies
 *   ownership — returning an empty Optional means the discount genuinely does not
 *   exist for this organizer/ticketType combination. The exception is still thrown
 *   here (not in the service) to keep the service return type as Optional, which
 *   allows programmatic callers to handle the not-found case without catching exceptions.
 *   GlobalExceptionHandler maps DiscountNotFoundException to HTTP 404.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/ticket-types/{ticketTypeId}/discounts")
@RequiredArgsConstructor
@Slf4j
public class DiscountController {

    private final DiscountService discountService;
    private final DiscountMapper discountMapper;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<DiscountResponseDto> createDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody CreateDiscountRequestDto request
    ) {
        UUID organizerId = parseUserId(jwt);
        log.info("Organizer {} creating discount for ticket type {} in event {}",
                organizerId, ticketTypeId, eventId);
        Discount discount = discountService.createDiscount(organizerId, eventId, ticketTypeId, request);
        return new ResponseEntity<>(discountMapper.toResponseDto(discount), HttpStatus.CREATED);
    }

    @PutMapping("/{discountId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<DiscountResponseDto> updateDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @PathVariable UUID discountId,
            @Valid @RequestBody CreateDiscountRequestDto request
    ) {
        UUID organizerId = parseUserId(jwt);
        log.info("Organizer {} updating discount {} for ticket type {} in event {}",
                organizerId, discountId, ticketTypeId, eventId);
        Discount discount = discountService.updateDiscount(organizerId, eventId, ticketTypeId, discountId, request);
        return ResponseEntity.ok(discountMapper.toResponseDto(discount));
    }

    @DeleteMapping("/{discountId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @PathVariable UUID discountId
    ) {
        UUID organizerId = parseUserId(jwt);
        log.info("Organizer {} deleting discount {} for ticket type {} in event {}",
                organizerId, discountId, ticketTypeId, eventId);
        discountService.deleteDiscount(organizerId, eventId, ticketTypeId, discountId);
        return ResponseEntity.noContent().build();
    }

    /**
     * FIX D-4: Exception is thrown after the service returns empty Optional.
     * The inline DiscountNotFoundException construction was moved here from inside an
     * orElseThrow() lambda. Functionally identical — both throw DiscountNotFoundException
     * which GlobalExceptionHandler maps to HTTP 404 — but this form is consistent with
     * the controller style used across the rest of the codebase.
     */
    @GetMapping("/{discountId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<DiscountResponseDto> getDiscount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @PathVariable UUID discountId
    ) {
        UUID organizerId = parseUserId(jwt);
        Discount discount = discountService.getDiscount(organizerId, eventId, ticketTypeId, discountId)
                .orElseThrow(() -> new DiscountNotFoundException(
                        String.format("Discount %s not found for ticket type %s", discountId, ticketTypeId)));
        return ResponseEntity.ok(discountMapper.toResponseDto(discount));
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<DiscountResponseDto>> listDiscounts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId
    ) {
        UUID organizerId = parseUserId(jwt);
        List<Discount> discounts = discountService.listDiscounts(organizerId, eventId, ticketTypeId);
        List<DiscountResponseDto> response = discounts.stream()
                .map(discountMapper::toResponseDto)
                .toList();
        return ResponseEntity.ok(response);
    }
}