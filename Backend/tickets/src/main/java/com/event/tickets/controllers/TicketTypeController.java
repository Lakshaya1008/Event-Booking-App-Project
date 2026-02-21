package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.CreateTicketTypeRequestDto;
import com.event.tickets.domain.dtos.CreateTicketTypeResponseDto;
import com.event.tickets.domain.dtos.GetTicketResponseDto;
import com.event.tickets.domain.dtos.PurchaseTicketRequestDto;
import com.event.tickets.domain.dtos.UpdateTicketTypeRequestDto;
import com.event.tickets.domain.dtos.UpdateTicketTypeResponseDto;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.mappers.TicketMapper;
import com.event.tickets.mappers.TicketTypeMapper;
import com.event.tickets.services.TicketTypeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/v1/events/{eventId}/ticket-types")
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;
    private final TicketTypeMapper ticketTypeMapper;
    private final TicketMapper ticketMapper;

    /**
     * Purchase tickets.
     *
     * FIX: now passes eventId from the URL path into purchaseTickets().
     * The service validates that the ticketTypeId actually belongs to that event,
     * preventing a crafted request from buying tickets across unrelated events.
     * The service also enforces PUBLISHED status and the sales window.
     */
    @PostMapping(path = "/{ticketTypeId}/tickets")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
    public ResponseEntity<List<GetTicketResponseDto>> purchaseTicket(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody PurchaseTicketRequestDto request
    ) {
        List<Ticket> createdTickets = ticketTypeService.purchaseTickets(
                parseUserId(jwt),
                eventId,          // now forwarded — was silently ignored before
                ticketTypeId,
                request.getQuantity()
        );
        List<GetTicketResponseDto> response = createdTickets.stream()
                .map(ticketMapper::toGetTicketResponseDto)
                .toList();
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ─── Organizer CRUD (unchanged logic) ──────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CreateTicketTypeResponseDto> createTicketType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody CreateTicketTypeRequestDto requestDto) {
        var request = ticketTypeMapper.fromDto(requestDto);
        var ticketType = ticketTypeService.createTicketType(parseUserId(jwt), eventId, request);
        var responseDto = ticketTypeMapper.toCreateResponseDto(ticketType);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<CreateTicketTypeResponseDto>> listTicketTypes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId) {
        var ticketTypes = ticketTypeService.listTicketTypesForEvent(parseUserId(jwt), eventId);
        var responseDtos = ticketTypes.stream()
                .map(ticketTypeMapper::toCreateResponseDto)
                .toList();
        return ResponseEntity.ok(responseDtos);
    }

    @GetMapping("/{ticketTypeId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<CreateTicketTypeResponseDto> getTicketType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId) {
        return ticketTypeService.getTicketType(parseUserId(jwt), eventId, ticketTypeId)
                .map(ticketTypeMapper::toCreateResponseDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{ticketTypeId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<UpdateTicketTypeResponseDto> updateTicketType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody UpdateTicketTypeRequestDto requestDto) {
        var request = ticketTypeMapper.fromUpdateDto(requestDto);
        var ticketType = ticketTypeService.updateTicketType(parseUserId(jwt), eventId, ticketTypeId, request);
        var responseDto = ticketTypeMapper.toUpdateResponseDto(ticketType);
        return ResponseEntity.ok(responseDto);
    }

    @DeleteMapping("/{ticketTypeId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Void> deleteTicketType(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId) {
        ticketTypeService.deleteTicketType(parseUserId(jwt), eventId, ticketTypeId);
        return ResponseEntity.noContent().build();
    }
}
