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
import lombok.extern.slf4j.Slf4j;
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

/**
 * Ticket Controller
 *
 * Handles ticket operations including viewing and QR code exports.
 *
 * QR CODE EXPORT SECURITY (READ-ONLY OPERATIONS):
 * - ATTENDEE: Can view/download QR for OWN tickets only
 * - ORGANIZER: Can view/download QR for tickets of events they own
 * - STAFF: NO access to QR operations
 * - ADMIN: NO bypass access
 *
 * QR CODE SECURITY MODEL:
 * - QR codes encode ONLY immutable ticket ID (UUID)
 * - QR image integrity is NOT a security boundary
 * - Security enforced during backend validation at scan time
 * - Safe to re-download, re-view, or share QR codes
 * - All QR operations are IDEMPOTENT (same content every time)
 */
@RestController
@RequestMapping(path = "/api/v1/tickets")
@RequiredArgsConstructor
@Slf4j
public class TicketController {

  private final TicketService ticketService;
  private final TicketMapper ticketMapper;
  private final QrCodeService qrCodeService;

  /**
   * Lists all tickets for the authenticated user.
   * ROLE-ONLY endpoint (no ownership check at list level).
   */
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

  /**
   * Gets a specific ticket by ID.
   * RESOURCE endpoint: Ownership checked in service layer.
   */
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

  /**
   * Legacy QR code endpoint (maintained for backward compatibility).
   * DEPRECATED: Use /qr-codes/view instead.
   */
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

  /**
   * VIEW QR CODE (Inline Display)
   *
   * Returns QR code as PNG image for inline display in browser/app.
   * Content-Disposition: inline (browser displays, doesn't download)
   *
   * IDEMPOTENT: Safe to view multiple times, no state change.
   * AUDIT: Logs QR_CODE_VIEWED action.
   *
   * ACCESS CONTROL:
   * - ATTENDEE: Own tickets only
   * - ORGANIZER: Tickets from own events only
   * - STAFF/ADMIN: NO access
   */
  @GetMapping(path = "/{ticketId}/qr-codes/view")
  @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
  public ResponseEntity<byte[]> viewQrCode(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId
  ) {
    UUID userId = parseUserId(jwt);
    log.info("QR code view requested: userId={}, ticketId={}", userId, ticketId);

    byte[] qrCodePng = qrCodeService.generateQrCodePngForViewing(userId, ticketId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    headers.setContentLength(qrCodePng.length);
    headers.setContentDisposition(
        org.springframework.http.ContentDisposition.inline()
            .filename("qr-code.png")
            .build()
    );
    headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic());

    return ResponseEntity.ok()
        .headers(headers)
        .body(qrCodePng);
  }

  /**
   * DOWNLOAD QR CODE (PNG)
   *
   * Returns QR code as PNG image for download.
   * Content-Disposition: attachment (forces download)
   * Filename: <event>_<ticket-type>_<user>_<id>.png
   *
   * IDEMPOTENT: Safe to download multiple times, no state change.
   * AUDIT: Logs QR_CODE_DOWNLOADED_PNG action.
   *
   * ACCESS CONTROL:
   * - ATTENDEE: Own tickets only
   * - ORGANIZER: Tickets from own events only
   * - STAFF/ADMIN: NO access
   */
  @GetMapping(path = "/{ticketId}/qr-codes/png")
  @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
  public ResponseEntity<byte[]> downloadQrCodePng(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId
  ) {
    UUID userId = parseUserId(jwt);
    log.info("QR code PNG download requested: userId={}, ticketId={}", userId, ticketId);

    byte[] qrCodePng = qrCodeService.generateQrCodePngForDownload(userId, ticketId);

    // Get ticket for filename generation (authorization already checked in service)
    String filename = "qr-code.png"; // Fallback filename
    try {
      var ticket = ticketService.getTicketForUser(userId, ticketId);
      if (ticket.isPresent()) {
        filename = qrCodeService.generateQrCodeFilename(ticket.get(), "png");
      }
    } catch (Exception ex) {
      log.warn("Failed to generate custom filename for ticketId={}", ticketId, ex);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    headers.setContentLength(qrCodePng.length);
    headers.setContentDisposition(
        org.springframework.http.ContentDisposition.attachment()
            .filename(filename)
            .build()
    );

    return ResponseEntity.ok()
        .headers(headers)
        .body(qrCodePng);
  }

  /**
   * DOWNLOAD QR CODE (PDF)
   *
   * Returns QR code embedded in PDF document with ticket details.
   * Content-Disposition: attachment (forces download)
   * Filename: <event>_<ticket-type>_<user>_<id>.pdf
   *
   * IDEMPOTENT: Safe to download multiple times, no state change.
   * AUDIT: Logs QR_CODE_DOWNLOADED_PDF action.
   *
   * ACCESS CONTROL:
   * - ATTENDEE: Own tickets only
   * - ORGANIZER: Tickets from own events only
   * - STAFF/ADMIN: NO access
   */
  @GetMapping(path = "/{ticketId}/qr-codes/pdf")
  @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
  public ResponseEntity<byte[]> downloadQrCodePdf(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID ticketId
  ) {
    UUID userId = parseUserId(jwt);
    log.info("QR code PDF download requested: userId={}, ticketId={}", userId, ticketId);

    byte[] qrCodePdf = qrCodeService.generateQrCodePdf(userId, ticketId);

    // Get ticket for filename generation (authorization already checked in service)
    String filename = "qr-code.pdf"; // Fallback filename
    try {
      var ticket = ticketService.getTicketForUser(userId, ticketId);
      if (ticket.isPresent()) {
        filename = qrCodeService.generateQrCodeFilename(ticket.get(), "pdf");
      }
    } catch (Exception ex) {
      log.warn("Failed to generate custom filename for ticketId={}", ticketId, ex);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentLength(qrCodePdf.length);
    headers.setContentDisposition(
        org.springframework.http.ContentDisposition.attachment()
            .filename(filename)
            .build()
    );

    return ResponseEntity.ok()
        .headers(headers)
        .body(qrCodePdf);
  }
}
