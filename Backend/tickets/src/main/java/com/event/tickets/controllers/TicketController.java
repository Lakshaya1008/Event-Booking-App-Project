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

@RestController
@RequestMapping(path = "/api/v1/tickets")
@RequiredArgsConstructor
@Slf4j
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

    /**
     * GET QR CODE (Binary Download - Legacy)
     *
     * MAINTAINED FOR BACKWARD COMPATIBILITY.
     * This endpoint returns QR code as binary PNG image.
     *
     * RECOMMENDED ALTERNATIVES (preferred):
     * - For viewing in browser: GET /api/v1/tickets/{ticketId}/qr-codes/view
     * - For file download: GET /api/v1/tickets/{ticketId}/qr-codes/png
     * - For full ticket data: GET /api/v1/tickets/{ticketId}
     *
     * This endpoint will remain supported indefinitely.
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
        return ResponseEntity.ok().headers(headers).body(qrCodeImage);
    }

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
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate());

        return ResponseEntity.ok().headers(headers).body(qrCodePng);
    }

    @GetMapping(path = "/{ticketId}/qr-codes/png")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
    public ResponseEntity<byte[]> downloadQrCodePng(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId
    ) {
        UUID userId = parseUserId(jwt);
        log.info("QR code PNG download requested: userId={}, ticketId={}", userId, ticketId);

        byte[] qrCodePng = qrCodeService.generateQrCodePngForDownload(userId, ticketId);

        // Use the ticket returned from QrCodeService (authorization already validated there)
        // to build filename. Fall back gracefully for organizers viewing attendee tickets.
        String filename = "qr-code.png";
        try {
            var ticket = ticketService.getTicketForUser(userId, ticketId);
            if (ticket.isPresent()) {
                filename = qrCodeService.generateQrCodeFilename(ticket.get(), "png");
            }
            // Note: if organizer is downloading (not purchaser), getTicketForUser returns empty
            // — fallback filename is used. This is acceptable since authorization is already done.
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

        return ResponseEntity.ok().headers(headers).body(qrCodePng);
    }

    @GetMapping(path = "/{ticketId}/qr-codes/pdf")
    @PreAuthorize("hasRole('ATTENDEE') or hasRole('ORGANIZER')")
    public ResponseEntity<byte[]> downloadQrCodePdf(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID ticketId
    ) {
        UUID userId = parseUserId(jwt);
        log.info("QR code PDF download requested: userId={}, ticketId={}", userId, ticketId);

        byte[] qrCodePdf = qrCodeService.generateQrCodePdf(userId, ticketId);

        String filename = "qr-code.pdf";
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

        return ResponseEntity.ok().headers(headers).body(qrCodePdf);
    }
}