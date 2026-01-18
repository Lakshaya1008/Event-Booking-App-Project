package com.event.tickets.services;

import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.Ticket;
import java.util.UUID;

/**
 * QR Code Service
 *
 * Handles QR code generation and export operations.
 *
 * SECURITY MODEL:
 * - QR codes encode ONLY immutable identifiers (ticketId or qrCodeId)
 * - QR image integrity is NOT a security boundary
 * - Security enforced during backend validation at scan time
 * - Safe to re-download, re-view, or share
 *
 * ACCESS CONTROL:
 * - ATTENDEE: Can access QR for OWN tickets only
 * - ORGANIZER: Can access QR for tickets of events they own
 * - STAFF: NO access
 * - ADMIN: NO bypass
 */
public interface QrCodeService {

  /**
   * Generates and persists QR code for a ticket.
   * Called during ticket purchase.
   */
  QrCode generateQrCode(Ticket ticket);

  /**
   * Gets QR code image for inline viewing (legacy method).
   * Authorization: User must own ticket or own the event.
   */
  byte[] getQrCodeImageForUserAndTicket(UUID userId, UUID ticketId);

  /**
   * Generates QR code PNG image for viewing (inline display).
   * IDEMPOTENT: Same content every time for same ticket.
   * NO side effects, read-only operation.
   *
   * @param userId User requesting the QR code
   * @param ticketId Ticket ID
   * @return PNG image bytes
   */
  byte[] generateQrCodePngForViewing(UUID userId, UUID ticketId);

  /**
   * Generates QR code PNG image for download.
   * IDEMPOTENT: Same content every time for same ticket.
   * NO side effects, read-only operation.
   *
   * @param userId User requesting the QR code
   * @param ticketId Ticket ID
   * @return PNG image bytes
   */
  byte[] generateQrCodePngForDownload(UUID userId, UUID ticketId);

  /**
   * Generates QR code PDF document for download.
   * IDEMPOTENT: Same content every time for same ticket.
   * NO side effects, read-only operation.
   *
   * @param userId User requesting the QR code
   * @param ticketId Ticket ID
   * @return PDF document bytes
   */
  byte[] generateQrCodePdf(UUID userId, UUID ticketId);

  /**
   * Generates sanitized filename for QR code export.
   * Format: <event-name>_<ticket-type>_<username>_<ticket-id>.<ext>
   *
   * @param ticket The ticket
   * @param extension File extension (png, pdf)
   * @return Sanitized filename
   */
  String generateQrCodeFilename(Ticket ticket, String extension);
}
