package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import com.event.tickets.domain.entities.User;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.AuditLogRepository;
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
 * Ticket Validation Service Implementation
 *
 * Key fixes applied:
 * 1. validateTicket() now receives the validator (User) and stores it in TicketValidation.validatedBy
 * 2. Successful validations are now audited (TICKET_VALIDATED action)
 * 3. Failed validations still emit FAILED_TICKET_VALIDATION
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

    private final QrCodeRepository qrCodeRepository;
    private final TicketValidationRepository ticketValidationRepository;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    @Override
    public TicketValidation validateTicketByQrCode(UUID userId, UUID qrCodeId) {
        User validator = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        try {
            QrCode qrCode = qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE)
                    .orElseThrow(() -> new QrCodeNotFoundException(
                            String.format("QR Code with ID %s was not found", qrCodeId)
                    ));

            Ticket ticket = qrCode.getTicket();
            Event event = ticket.getTicketType().getEvent();

            authorizationService.requireOrganizerOrStaffAccess(userId, event);

            // Pass validator so it gets stored on the record
            TicketValidation result = validateTicket(ticket, TicketValidationMethod.QR_SCAN, validator);

            // Audit successful validation
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

            // Pass validator so it gets stored on the record
            TicketValidation result = validateTicket(ticket, TicketValidationMethod.MANUAL, validator);

            // Audit successful validation
            emitSuccessfulTicketValidation(validator, ticket, "MANUAL");

            return result;

        } catch (TicketNotFoundException e) {
            emitFailedTicketValidation(validator, null, "TICKET_NOT_FOUND: " + e.getMessage(), "MANUAL");
            throw e;
        }
    }

    /**
     * Core validation logic.
     *
     * A ticket is VALID on first scan. Any subsequent scan marks it INVALID
     * (already used). The validator identity is now stored so we know exactly
     * who scanned which ticket at what time.
     */
    private TicketValidation validateTicket(Ticket ticket,
                                            TicketValidationMethod method, User validator) {

        TicketValidationStatusEnum status = ticket.getValidations().stream()
                .filter(v -> TicketValidationStatusEnum.VALID.equals(v.getStatus()))
                .findFirst()
                .map(v -> TicketValidationStatusEnum.INVALID)   // already validated once → INVALID
                .orElse(TicketValidationStatusEnum.VALID);      // first scan → VALID

        TicketValidation validation = new TicketValidation();
        validation.setTicket(ticket);
        validation.setValidationMethod(method);
        validation.setValidatedBy(validator);               // FIX #1: record who scanned
        validation.setStatus(status);

        return ticketValidationRepository.save(validation);
    }

    // ─── listing operations ────────────────────────────────────────────────────

    @Override
    public Page<TicketValidation> listValidationsForEvent(UUID userId, UUID eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)
                ));

        authorizationService.requireOrganizerOrStaffAccess(userId, event);

        return ticketValidationRepository.findByTicketTicketTypeEventId(eventId, pageable);
    }

    @Override
    public List<TicketValidation> getValidationsByTicket(UUID userId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(TicketNotFoundException::new);

        Event event = ticket.getTicketType().getEvent();

        authorizationService.requireOrganizerOrStaffAccess(userId, event);

        return ticketValidationRepository.findByTicketId(ticketId);
    }

    // ─── audit helpers ─────────────────────────────────────────────────────────

    /**
     * Audits a SUCCESSFUL ticket validation.
     * Previously no success audit existed — only failures were logged.
     */
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
            log.error("Failed to emit TICKET_VALIDATED audit event: ticketId={}, error={}",
                    ticket.getId(), e.getMessage());
        }
    }

    private void emitFailedTicketValidation(User user, Ticket ticket, String reason, String method) {
        try {
            if (user == null) {
                user = systemUserProvider.getSystemUser();
            }
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
            log.error("Failed to emit FAILED_TICKET_VALIDATION audit event: error={}", e.getMessage());
        }
    }

    private jakarta.servlet.http.HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
