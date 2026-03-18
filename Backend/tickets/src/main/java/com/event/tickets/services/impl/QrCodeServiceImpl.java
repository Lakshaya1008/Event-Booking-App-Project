package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.User;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.exceptions.QrCodeGenerationException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.repositories.QrCodeRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.AuditLogService;
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

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * QR Code Service Implementation
 *
 * FIX #4: Critical UUID mismatch that caused every downloaded QR to fail scanning.
 *
 * ROOT CAUSE:
 * generateQrCode(ticket) stored a random UUID as qrCode.id and encoded it in
 * the image. But generateQrCodePngForViewing/Download/Pdf() generated a FRESH
 * image encoding ticket.getId() — a completely different UUID.
 * When a user scanned the downloaded QR, the validator received ticket.getId()
 * and searched for a QrCode with that ID — finding nothing, because no
 * QrCode.id == ticket.id. Every scan failed with QrCodeNotFoundException.
 *
 * FIX: All download/view endpoints now call getActiveQrCodeForTicket() to load
 * the stored QrCode, then encode qrCode.getId() into the image. This is the
 * exact UUID the validator will receive when the code is scanned.
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
    private final AuditLogService auditLogService;
    private final SystemUserProvider systemUserProvider;

    @Override
    public QrCode generateQrCode(Ticket ticket) {
        try {
            UUID qrCodeId = UUID.randomUUID();
            // Encode qrCode.id into the image — this is what the scanner will read back
            String qrCodeImage = generateQrCodeImageBase64(qrCodeId);

            QrCode qrCode = new QrCode();
            qrCode.setId(qrCodeId);
            qrCode.setStatus(QrCodeStatusEnum.ACTIVE);
            qrCode.setValue(qrCodeImage);
            qrCode.setTicket(ticket);

            return qrCodeRepository.saveAndFlush(qrCode);
        } catch (IOException | WriterException ex) {
            throw new QrCodeGenerationException("Failed to generate QR Code", ex);
        }
    }

    @Override
    public byte[] getQrCodeImageForUserAndTicket(UUID userId, UUID ticketId) {
        authorizeQrCodeAccess(userId, ticketId);
        QrCode qrCode = qrCodeRepository.findByTicketIdAndTicketPurchaserId(ticketId, userId)
                .orElseThrow(QrCodeNotFoundException::new);
        try {
            return Base64.getDecoder().decode(qrCode.getValue());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid base64 QR Code for ticket ID: {}", ticketId, ex);
            throw new QrCodeNotFoundException();
        }
    }

    /**
     * FIX #4: Fetches the stored QrCode and encodes qrCode.getId() into the image.
     * Previously encoded ticket.getId() which never matched any QrCode in the DB.
     */
    @Override
    public byte[] generateQrCodePngForViewing(UUID userId, UUID ticketId) {
        authorizeQrCodeAccess(userId, ticketId);

        // FIX #4: load the stored QrCode, encode its ID — not ticket.getId()
        QrCode qrCode = getActiveQrCodeForTicket(ticketId);
        byte[] qrCodeBytes = generateQrCodeImageBytes(qrCode.getId());

        auditQrCodeAccess(userId, ticketId, AuditAction.QR_CODE_VIEWED);
        return qrCodeBytes;
    }

    @Override
    public byte[] generateQrCodePngForDownload(UUID userId, UUID ticketId) {
        authorizeQrCodeAccess(userId, ticketId);

        QrCode qrCode = getActiveQrCodeForTicket(ticketId); // FIX #4
        byte[] qrCodeBytes = generateQrCodeImageBytes(qrCode.getId());

        auditQrCodeAccess(userId, ticketId, AuditAction.QR_CODE_DOWNLOADED_PNG);
        return qrCodeBytes;
    }

    @Override
    public byte[] generateQrCodePdf(UUID userId, UUID ticketId) {
        Ticket ticket = authorizeQrCodeAccess(userId, ticketId);

        QrCode qrCode = getActiveQrCodeForTicket(ticketId); // FIX #4
        byte[] qrCodePngBytes = generateQrCodeImageBytes(qrCode.getId());
        byte[] pdfBytes = generatePdfWithQrCode(ticket, qrCodePngBytes);

        auditQrCodeAccess(userId, ticketId, AuditAction.QR_CODE_DOWNLOADED_PDF);
        return pdfBytes;
    }

    @Override
    public String generateQrCodeFilename(Ticket ticket, String extension) {
        String eventName = sanitizeForFilename(ticket.getTicketType().getEvent().getName());
        String ticketType = sanitizeForFilename(ticket.getTicketType().getName());
        String username = sanitizeForFilename(ticket.getPurchaser().getName());
        String ticketId = ticket.getId().toString().substring(0, 8);
        return String.format("%s_%s_%s_%s.%s", eventName, ticketType, username, ticketId, extension);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * FIX #4: Loads the active QrCode record for a ticket.
     * All download/view endpoints use this to get the correct UUID to encode.
     */
    private QrCode getActiveQrCodeForTicket(UUID ticketId) {
        return qrCodeRepository.findByTicketIdAndStatus(ticketId, QrCodeStatusEnum.ACTIVE)
                .orElseThrow(() -> {
                    log.error("No active QrCode found for ticket: {}", ticketId);
                    return new QrCodeNotFoundException();
                });
    }

    private Ticket authorizeQrCodeAccess(UUID userId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(
                        String.format("Ticket with ID '%s' not found", ticketId)));
        boolean ownsTicket = ticket.getPurchaser().getId().equals(userId);
        boolean ownsEvent = ticket.getTicketType().getEvent().getOrganizer().getId().equals(userId);
        if (!ownsTicket && !ownsEvent) {
            log.warn("Unauthorized QR code access: userId={}, ticketId={}", userId, ticketId);
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have permission to access this QR code");
        }
        return ticket;
    }

    /** Generates PNG bytes encoding the given UUID. Used by all download/view paths. */
    private byte[] generateQrCodeImageBytes(UUID idToEncode) {
        try {
            BitMatrix bitMatrix = qrCodeWriter.encode(
                    idToEncode.toString(), BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);
            BufferedImage qrCodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(qrCodeImage, "PNG", baos);
                return baos.toByteArray();
            }
        } catch (WriterException | IOException ex) {
            log.error("Failed to generate QR code image for id: {}", idToEncode, ex);
            throw new QrCodeGenerationException("Failed to generate QR code image", ex);
        }
    }

    /** Generates base64-encoded QR image for storage at purchase time. */
    private String generateQrCodeImageBase64(UUID uniqueId) throws WriterException, IOException {
        BitMatrix bitMatrix = qrCodeWriter.encode(
                uniqueId.toString(), BarcodeFormat.QR_CODE, QR_WIDTH, QR_HEIGHT);
        BufferedImage qrCodeImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(qrCodeImage, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private byte[] generatePdfWithQrCode(Ticket ticket, byte[] qrCodePngBytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDocument = new PdfDocument(writer);
            Document document = new Document(pdfDocument);
            document.add(new Paragraph("Event Ticket QR Code").setFontSize(20).setBold());
            document.add(new Paragraph("Event: " + ticket.getTicketType().getEvent().getName()));
            document.add(new Paragraph("Ticket Type: " + ticket.getTicketType().getName()));
            document.add(new Paragraph("Ticket Holder: " + ticket.getPurchaser().getName()));
            document.add(new Paragraph("Ticket ID: " + ticket.getId().toString()));
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

    private String sanitizeForFilename(String input) {
        if (input == null) return "unknown";
        // C-05 FIX: capture sanitized result first, THEN apply length cap.
        // The regex calls can shorten the string — using input.length() caused SIOOBE.
        String sanitized = input.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("-+", "_");
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }

    private void auditQrCodeAccess(UUID userId, UUID ticketId, AuditAction action) {
        try {
            HttpServletRequest request = getCurrentRequest();
            User actor = userRepository.findById(userId).orElseGet(systemUserProvider::getSystemUser);
            AuditLog auditLog = AuditLog.builder()
                    .action(action).actor(actor)
                    .resourceType("QRCode").resourceId(ticketId)
                    .details("QR code access for ticket: " + ticketId)
                    .ipAddress(extractClientIp(request))
                    .userAgent(extractUserAgent(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception ex) {
            log.error("Failed to audit QR code access: userId={}, ticketId={}", userId, ticketId, ex);
        }
    }


}