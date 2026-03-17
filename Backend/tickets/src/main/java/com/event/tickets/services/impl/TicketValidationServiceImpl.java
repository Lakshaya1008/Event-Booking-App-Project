package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import com.event.tickets.domain.entities.User;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.InvalidBusinessStateException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.QrCodeRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketValidationRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.TicketValidationService;
import com.event.tickets.services.AuditLogService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * H-02 FIX: validateTicket() now rejects CANCELLED tickets immediately.
 * Previously the method only checked past validation records to detect
 * duplicate scans — it never verified the ticket's own status. A CANCELLED
 * ticket with no prior scans would be marked VALID, letting a cancelled
 * attendee walk through the door.
 *
 * L-20 FIX: Removed dead AuditLogRepository dependency. It was injected but
 * never used — all audit writes go through AuditLogService. The unused field
 * forced every test that mocks this class to also provide an AuditLogRepository
 * mock, or Mockito would inject null and silently suppress NPEs in audit helpers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

    // L-20 FIX: AuditLogRepository removed — was injected but never used directly.
    // All audit writes go through AuditLogService.saveAuditLog().
    private final QrCodeRepository qrCodeRepository;
    private final TicketValidationRepository ticketValidationRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    @Override
    public TicketValidation validateTicketByQrCode(UUID userId, UUID qrCodeId) {
        User validator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        try {
            QrCode qrCode = qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE)
                    .orElseThrow(() -> new QrCodeNotFoundException(
                            String.format("QR Code with ID %s was not found", qrCodeId)));

            Ticket ticket = qrCode.getTicket();
            Event event = ticket.getTicketType().getEvent();

            authorizationService.requireOrganizerOrStaffAccess(userId, event);

            TicketValidation result = validateTicket(ticket, TicketValidationMethod.QR_SCAN, validator);
            emitSuccessfulTicketValidation(validator, ticket, "QR_SCAN");
            return result;

        } catch (QrCodeNotFoundException e) {
            emitFailedTicketValidation(validator, null, "QR_CODE_NOT_FOUND: " + e.getMessage(), "QR_SCAN");
            throw e;
        }
    }

    @Override
    public TicketValidation validateTicketManually(UUID userId, UUID ticketId) {
        User validator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        try {
            Ticket ticket = ticketRepository.findById(ticketId)
                    .orElseThrow(TicketNotFoundException::new);

            Event event = ticket.getTicketType().getEvent();
            authorizationService.requireOrganizerOrStaffAccess(userId, event);

            TicketValidation result = validateTicket(ticket, TicketValidationMethod.MANUAL, validator);
            emitSuccessfulTicketValidation(validator, ticket, "MANUAL");
            return result;

        } catch (TicketNotFoundException e) {
            emitFailedTicketValidation(validator, null, "TICKET_NOT_FOUND: " + e.getMessage(), "MANUAL");
            throw e;
        }
    }

    /**
     * H-02 FIX: CANCELLED tickets are now rejected before any duplicate-scan check.
     *
     * Previous logic only examined past validations to detect already-used tickets.
     * It never looked at ticket.getStatus(). A CANCELLED ticket with no prior
     * validation records would reach the stream, find no VALID entry, and receive
     * TicketValidationStatusEnum.VALID — letting a cancelled attendee enter the event.
     *
     * New logic:
     *   1. Reject CANCELLED tickets with InvalidBusinessStateException.
     *   2. VALID on first scan (no prior VALID validation).
     *   3. INVALID on any subsequent scan (already has a VALID validation record).
     */
    private TicketValidation validateTicket(Ticket ticket, TicketValidationMethod method, User validator) {
        // H-02 FIX: reject CANCELLED tickets — must check before the duplicate-scan logic
        if (TicketStatusEnum.CANCELLED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket " + ticket.getId() + " has been cancelled and cannot be validated.");
        }

        TicketValidationStatusEnum status = ticket.getValidations().stream()
                .filter(v -> TicketValidationStatusEnum.VALID.equals(v.getStatus()))
                .findFirst()
                .map(v -> TicketValidationStatusEnum.INVALID)   // already scanned → INVALID
                .orElse(TicketValidationStatusEnum.VALID);       // first scan → VALID

        TicketValidation validation = new TicketValidation();
        validation.setTicket(ticket);
        validation.setValidationMethod(method);
        validation.setValidatedBy(validator);
        validation.setStatus(status);

        return ticketValidationRepository.save(validation);
    }

    @Override
    public Page<TicketValidation> listValidationsForEvent(UUID userId, UUID eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        authorizationService.requireOrganizerOrStaffAccess(userId, event);
        return ticketValidationRepository.findByTicketTicketTypeEventId(eventId, pageable);
    }

    @Override
    public List<TicketValidation> getValidationsByTicket(UUID userId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(TicketNotFoundException::new);
        authorizationService.requireOrganizerOrStaffAccess(userId, ticket.getTicketType().getEvent());
        return ticketValidationRepository.findByTicketId(ticketId);
    }

    private void emitSuccessfulTicketValidation(User validator, Ticket ticket, String method) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.TICKET_VALIDATED)
                    .actor(validator)
                    .targetUser(ticket.getPurchaser())
                    .event(ticket.getTicketType().getEvent())
                    .resourceType("TICKET")
                    .resourceId(ticket.getId())
                    .details("method=" + method + ",validatorId=" + validator.getId())
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit TICKET_VALIDATED audit: ticketId={}", ticket.getId(), e);
        }
    }

    private void emitFailedTicketValidation(User user, Ticket ticket, String reason, String method) {
        try {
            if (user == null) user = systemUserProvider.getSystemUser();
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.FAILED_TICKET_VALIDATION)
                    .actor(user)
                    .targetUser(user)
                    .event(ticket != null ? ticket.getTicketType().getEvent() : null)
                    .resourceType("TICKET")
                    .resourceId(ticket != null ? ticket.getId() : null)
                    .details("method=" + method + ",reason=" + reason)
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit FAILED_TICKET_VALIDATION audit: {}", e.getMessage());
        }
    }

    private jakarta.servlet.http.HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}