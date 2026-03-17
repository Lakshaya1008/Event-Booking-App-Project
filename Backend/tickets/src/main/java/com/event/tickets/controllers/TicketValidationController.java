package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.TicketValidationRequestDto;
import com.event.tickets.domain.dtos.TicketValidationResponseDto;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.mappers.TicketValidationMapper;
import com.event.tickets.services.TicketValidationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/ticket-validations")
@RequiredArgsConstructor
public class TicketValidationController {

    private final TicketValidationService ticketValidationService;
    private final TicketValidationMapper ticketValidationMapper;

    /**
     * H-03 FIX: @Valid added to @RequestBody.
     *
     * Previously had no @Valid, so TicketValidationRequestDto.method could be null.
     * The routing logic TicketValidationMethod.MANUAL.equals(method) would NPE when
     * method was null (the enum constant is non-null, but equals(null) on an enum
     * does not NPE — however the else-branch would call validateTicketByQrCode with
     * a null id, causing a different NPE or QrCodeNotFoundException with a null UUID).
     * With @Valid, the @NotNull on method in the DTO is enforced before the controller
     * body executes, returning HTTP 400 instead of an unhandled NPE.
     */
    @PostMapping
    @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")
    public ResponseEntity<TicketValidationResponseDto> validateTicket(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TicketValidationRequestDto ticketValidationRequestDto
    ) {
        UUID userId = parseUserId(jwt);
        TicketValidationMethod method = ticketValidationRequestDto.getMethod();
        TicketValidation ticketValidation;
        if (TicketValidationMethod.MANUAL.equals(method)) {
            ticketValidation = ticketValidationService.validateTicketManually(
                    userId, ticketValidationRequestDto.getId());
        } else {
            ticketValidation = ticketValidationService.validateTicketByQrCode(
                    userId, ticketValidationRequestDto.getId());
        }
        return ResponseEntity.ok(ticketValidationMapper.toTicketValidationResponseDto(ticketValidation));
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")
    public ResponseEntity<Page<TicketValidationResponseDto>> listValidationsForEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            Pageable pageable) {
        UUID userId = parseUserId(jwt);
        Page<TicketValidation> validations =
                ticketValidationService.listValidationsForEvent(userId, eventId, pageable);
        return ResponseEntity.ok(validations.map(ticketValidationMapper::toTicketValidationResponseDto));
    }

    @GetMapping("/tickets/{ticketId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")
    public ResponseEntity<List<TicketValidationResponseDto>> getValidationsByTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId) {
        UUID userId = parseUserId(jwt);
        List<TicketValidation> validations =
                ticketValidationService.getValidationsByTicket(userId, ticketId);
        return ResponseEntity.ok(validations.stream()
                .map(ticketValidationMapper::toTicketValidationResponseDto)
                .toList());
    }
}