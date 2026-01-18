package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.exceptions.QrCodeGenerationException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.repositories.AuditLogRepository;
import com.event.tickets.repositories.QrCodeRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.QrCodeService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * QR Code Service Implementation - READ-ONLY EXPORT FEATURES
 *
 * SECURITY PRINCIPLES:
 * - QR codes encode ONLY immutable identifiers (ticketId or qrCodeId UUID)
 * - QR image integrity is NOT a security boundary
 * - Security enforced during backend validation at scan time, not at download
 * - Safe to re-download, re-view, or share QR codes
 * - Authorization checked via existing AuthorizationService
 *
 * IDEMPOTENCY:
 * - Same QR content generated every time for same ticket
 * - No state changes during export operations
 * - Read-only, safe to call multiple times
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QrCodeServiceImpl implements QrCodeService {

  private static final int QR_HEIGHT = 300;
  private static final int QR_WIDTH = 300;

  private final QRCodeWriter qrCodeWriter = new QRCodeWriter();
  private final QrCodeRepository qrCodeRepository;
  private final TicketRepository ticketRepository;
  private final UserRepository userRepository;
  private final AuthorizationService authorizationService;
  private final AuditLogRepository auditLogRepository;

  @Override
  public QrCode generateQrCode(Ticket ticket) {
    try {
      UUID uniqueId = UUID.randomUUID();
      String qrCodeImage = generateQrCodeImage(uniqueId);

      QrCode qrCode = new QrCode();
      qrCode.setId(uniqueId);
      qrCode.setStatus(QrCodeStatusEnum.ACTIVE);
      qrCode.setValue(qrCodeImage);
      qrCode.setTicket(ticket);

      return qrCodeRepository.saveAndFlush(qrCode);

    } catch(IOException | WriterException ex) {
      throw new QrCodeGenerationException("Failed to generate QR Code", ex);
    }
  }

  @Override
  public byte[] getQrCodeImageForUserAndTicket(UUID userId, UUID ticketId) {
    // Authorization: User must own ticket OR own the event
    authorizeQrCodeAccess(userId, ticketId);

    QrCode qrCode = qrCodeRepository.findByTicketIdAndTicketPurchaserId(ticketId, userId)
        .orElseThrow(QrCodeNotFoundException::new);

    try {
      return Base64.getDecoder().decode(qrCode.getValue());
    } catch(IllegalArgumentException ex) {
      log.error("Invalid base64 QR Code for ticket ID: {}", ticketId, ex);
      throw new QrCodeNotFoundException();
    }
  }

  @Override
  public byte[] generateQrCodePngForViewing(UUID userId, UUID ticketId) {
    log.debug("Generating QR code PNG for viewing: userId={}, ticketId={}", userId, ticketId);

    // Authorization: User must own ticket OR own the event
    Ticket ticket = authorizeQrCodeAccess(userId, ticketId);

    // Generate QR code (idempotent - same content every time)
    byte[] qrCodeBytes = generateQrCodeImageBytes(ticket.getId());

    // Audit log: QR viewed
    auditQrCodeAccess(userId, ticketId, AuditAction.QR_CODE_VIEWED);

    return qrCodeBytes;
  }

  @Override
  public byte[] generateQrCodePngForDownload(UUID userId, UUID ticketId) {
    log.debug("Generating QR code PNG for download: userId={}, ticketId={}", userId, ticketId);

    // Authorization: User must own ticket OR own the event
    Ticket ticket = authorizeQrCodeAccess(userId, ticketId);

    // Generate QR code (idempotent - same content every time)
    byte[] qrCodeBytes = generateQrCodeImageBytes(ticket.getId());

    // Audit log: QR downloaded as PNG
    auditQrCodeAccess(userId, ticketId, AuditAction.QR_CODE_DOWNLOADED_PNG);

    return qrCodeBytes;
  }

  @Override
  public byte[] generateQrCodePdf(UUID userId, UUID ticketId) {
    log.debug("Generating QR code PDF: userId={}, ticketId={}", userId, ticketId);

    // Authorization: User must own ticket OR own the event
    Ticket ticket = authorizeQrCodeAccess(userId, ticketId);

    // Generate QR code PNG bytes
    byte[] qrCodePngBytes = generateQrCodeImageBytes(ticket.getId());

    // Generate PDF with QR code and ticket info
    byte[] pdfBytes = generatePdfWithQrCode(ticket, qrCodePngBytes);

    // Audit log: QR downloaded as PDF
    auditQrCodeAccess(userId, ticketId, AuditAction.QR_CODE_DOWNLOADED_PDF);

    return pdfBytes;
  }

  @Override
  public String generateQrCodeFilename(Ticket ticket, String extension) {
    // Format: <event-name>_<ticket-type>_<username>_<ticket-id>.<ext>
    String eventName = sanitizeForFilename(ticket.getTicketType().getEvent().getName());
    String ticketType = sanitizeForFilename(ticket.getTicketType().getName());
    String username = sanitizeForFilename(ticket.getPurchaser().getName());
    String ticketId = ticket.getId().toString().substring(0, 8); // First 8 chars of UUID

    return String.format("%s_%s_%s_%s.%s",
        eventName, ticketType, username, ticketId, extension);
  }

  /**
   * Authorizes QR code access for a user.
   * User must either:
   * - Own the ticket (ATTENDEE/ORGANIZER who purchased it)
   * - Own the event (ORGANIZER)
   *
   * STAFF and ADMIN have NO access (per requirements).
   *
   * @throws TicketNotFoundException if ticket doesn't exist
   * @throws org.springframework.security.access.AccessDeniedException if unauthorized
   */
  private Ticket authorizeQrCodeAccess(UUID userId, UUID ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId)
        .orElseThrow(() -> new TicketNotFoundException(
            String.format("Ticket with ID '%s' not found", ticketId)
        ));

    // Check if user owns the ticket
    boolean ownsTicket = ticket.getPurchaser().getId().equals(userId);

    // Check if user owns the event (organizer)
    boolean ownsEvent = ticket.getTicketType().getEvent().getOrganizer().getId().equals(userId);

    if (!ownsTicket && !ownsEvent) {
      log.warn("Unauthorized QR code access attempt: userId={}, ticketId={}", userId, ticketId);
      throw new org.springframework.security.access.AccessDeniedException(
          "You do not have permission to access this QR code"
      );
    }

    return ticket;
  }

  /**
   * Generates QR code image bytes from ticket ID.
   * IDEMPOTENT: Same ticketId always produces same QR code.
   */
  private byte[] generateQrCodeImageBytes(UUID ticketId) {
    try {
      BitMatrix bitMatrix = qrCodeWriter.encode(
          ticketId.toString(),
          BarcodeFormat.QR_CODE,
          QR_WIDTH,
          QR_HEIGHT
      );

      BufferedImage qrCodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        ImageIO.write(qrCodeImage, "PNG", baos);
        return baos.toByteArray();
      }
    } catch (WriterException | IOException ex) {
      log.error("Failed to generate QR code image for ticket: {}", ticketId, ex);
      throw new QrCodeGenerationException("Failed to generate QR code image", ex);
    }
  }

  /**
   * Generates PDF document containing QR code and ticket information.
   */
  private byte[] generatePdfWithQrCode(Ticket ticket, byte[] qrCodePngBytes) {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      PdfWriter writer = new PdfWriter(baos);
      PdfDocument pdfDocument = new PdfDocument(writer);
      Document document = new Document(pdfDocument);

      // Add title
      document.add(new Paragraph("Event Ticket QR Code")
          .setFontSize(20)
          .setBold());

      // Add ticket details
      document.add(new Paragraph(String.format("Event: %s",
          ticket.getTicketType().getEvent().getName())));
      document.add(new Paragraph(String.format("Ticket Type: %s",
          ticket.getTicketType().getName())));
      document.add(new Paragraph(String.format("Ticket Holder: %s",
          ticket.getPurchaser().getName())));
      document.add(new Paragraph(String.format("Ticket ID: %s",
          ticket.getId().toString())));

      // Add QR code image
      Image qrImage = new Image(ImageDataFactory.create(qrCodePngBytes));
      qrImage.scaleToFit(300, 300);
      document.add(qrImage);

      document.close();

      return baos.toByteArray();
    } catch (IOException ex) {
      log.error("Failed to generate PDF for ticket: {}", ticket.getId(), ex);
      throw new QrCodeGenerationException("Failed to generate PDF document", ex);
    }
  }

  /**
   * Sanitizes string for use in filename.
   * Converts to lowercase, replaces spaces with underscores, removes special chars.
   */
  private String sanitizeForFilename(String input) {
    if (input == null) {
      return "unknown";
    }
    return input.toLowerCase()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "_")
        .replaceAll("-+", "_")
        .substring(0, Math.min(input.length(), 50)); // Max 50 chars
  }

  /**
   * Audits QR code access with actor, ticket, and action type.
   */
  private void auditQrCodeAccess(UUID userId, UUID ticketId, AuditAction action) {
    try {
      HttpServletRequest request = getCurrentRequest();
      String ipAddress = request != null ? extractClientIp(request) : "unknown";
      String userAgent = request != null ? extractUserAgent(request) : "unknown";

      AuditLog auditLog = AuditLog.builder()
          .action(action)
          .actor(userRepository.findById(userId).orElse(null))
          .resourceType("QRCode")
          .resourceId(ticketId)
          .details(String.format("QR code access for ticket: %s", ticketId))
          .ipAddress(ipAddress)
          .userAgent(userAgent)
          .build();

      auditLogRepository.save(auditLog);

      log.info("Audited QR code access: action={}, userId={}, ticketId={}",
          action, userId, ticketId);
    } catch (Exception ex) {
      log.error("Failed to audit QR code access: userId={}, ticketId={}",
          userId, ticketId, ex);
      // Don't fail the operation if audit logging fails
    }
  }

  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  private String generateQrCodeImage(UUID uniqueId) throws WriterException, IOException {
    BitMatrix bitMatrix = qrCodeWriter.encode(
        uniqueId.toString(),
        BarcodeFormat.QR_CODE,
        QR_WIDTH,
        QR_HEIGHT
    );

    BufferedImage qrCodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

    try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(qrCodeImage, "PNG", baos);
      byte[] imageBytes = baos.toByteArray();

      return Base64.getEncoder().encodeToString(imageBytes);
    }
  }

}
