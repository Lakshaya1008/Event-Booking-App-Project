package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.GetTicketResponseDto;
import com.event.tickets.domain.dtos.ListTicketResponseDto;
import com.event.tickets.mappers.TicketMapper;
import com.event.tickets.services.QrCodeService;
import com.event.tickets.services.TicketService;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

  private final TicketService ticketService;
  private final TicketMapper ticketMapper;
  private final QrCodeService qrCodeService;

  @GetMapping
  @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
  public Page<ListTicketResponseDto> listTickets(
      @AuthenticationPrincipal Jwt jwt,
      Pageable pageable
  ) {
    return ticketService.listTicketsForUser(
        parseUserId(jwt),
        pageable
    ).map(ticketMapper::toListTicketResponseDto);
  }

  @GetMapping(path = "/{ticketId}")
  @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
  public ResponseEntity<GetTicketResponseDto> getTicket(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId
  ) {
    return ticketService
        .getTicketForUser(parseUserId(jwt), ticketId)
        .map(ticketMapper::toGetTicketResponseDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping(path = "/{ticketId}/qr-codes")
  @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
  public ResponseEntity<byte[]> getTicketQrCode(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId
  ) {
    byte[] qrCodeImage = qrCodeService.getQrCodeImageForUserAndTicket(
        parseUserId(jwt),
        ticketId
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    headers.setContentLength(qrCodeImage.length);

    return ResponseEntity.ok()
        .headers(headers)
        .body(qrCodeImage);
  }

}
