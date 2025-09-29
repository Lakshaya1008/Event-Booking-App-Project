package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.TicketValidationRequestDto;
import com.event.tickets.domain.dtos.TicketValidationResponseDto;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.mappers.TicketValidationMapper;
import com.event.tickets.services.TicketValidationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  @PostMapping
  @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")
  public ResponseEntity<TicketValidationResponseDto> validateTicket(
      @RequestBody TicketValidationRequestDto ticketValidationRequestDto
  ){
    TicketValidationMethod method = ticketValidationRequestDto.getMethod();
    TicketValidation ticketValidation;
    if(TicketValidationMethod.MANUAL.equals(method)) {
      ticketValidation = ticketValidationService.validateTicketManually(
          ticketValidationRequestDto.getId());
    } else {
      ticketValidation = ticketValidationService.validateTicketByQrCode(
          ticketValidationRequestDto.getId()
      );
    }
    return ResponseEntity.ok(
        ticketValidationMapper.toTicketValidationResponseDto(ticketValidation)
    );
  }

  // Staff listing endpoints
  @GetMapping("/events/{eventId}")
  @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")
  public ResponseEntity<Page<TicketValidationResponseDto>> listValidationsForEvent(
      @PathVariable UUID eventId,
      Pageable pageable) {
    Page<TicketValidation> validations = ticketValidationService.listValidationsForEvent(eventId, pageable);
    Page<TicketValidationResponseDto> responseDtos = validations.map(ticketValidationMapper::toTicketValidationResponseDto);
    return ResponseEntity.ok(responseDtos);
  }

  @GetMapping("/tickets/{ticketId}")
  @PreAuthorize("hasRole('STAFF') or hasRole('ORGANIZER')")
  public ResponseEntity<List<TicketValidationResponseDto>> getValidationsByTicket(
      @PathVariable UUID ticketId) {
    List<TicketValidation> validations = ticketValidationService.getValidationsByTicket(ticketId);
    List<TicketValidationResponseDto> responseDtos = validations.stream()
        .map(ticketValidationMapper::toTicketValidationResponseDto)
        .toList();
    return ResponseEntity.ok(responseDtos);
  }

}
