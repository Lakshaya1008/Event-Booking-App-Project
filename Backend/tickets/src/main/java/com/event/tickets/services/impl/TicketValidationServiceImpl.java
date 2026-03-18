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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX 1 — Ticket.status transitions to VALIDATED on first successful scan.
 *   BEFORE: TicketStatusEnum.VALIDATED existed in the enum but was NEVER written
 *   to the database. A ticket scanned at the door remained status=PURCHASED
 *   forever. The double-scan prevention worked by checking ticket.getValidations()
 *   for a prior VALID record — which is correct — but:
 *     (a) ticket.status was perpetually stale (always PURCHASED after scan)
 *     (b) any report grouping by ticket.status to count "checked-in attendees"
 *         would return 0 regardless of how many people walked through the door
 *     (c) the VALIDATED guard in validateTicket() (FIX #7-1) could never fire
 *         because status was never actually set to VALIDATED
 *   AFTER: On the FIRST VALID scan, ticket.setStatus(VALIDATED) is called and
 *   ticketRepository.save(ticket) persists it. On all subsequent scans the
 *   existing VALIDATED guard fires immediately, before even checking validations.
 *
 * FIX 2 — @Transactional import standardised to org.springframework.
 *   jakarta.transaction.@Transactional was replaced with
 *   org.springframework.transaction.annotation.@Transactional for consistency
 *   with all other services and to enable readOnly=true on query methods.
 *
 * FIX 3 — getCurrentRequest() now uses RequestUtil.getCurrentRequest().
 *   Removed the private helper copy-paste; delegates to the centralised
 *   RequestUtil method.
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
    private final SystemUserProvider systemUserProvider;
    private final AuditLogService auditLogService;

    // ── VALIDATE BY QR ────────────────────────────────────────────────────────

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

    // ── VALIDATE MANUALLY ─────────────────────────────────────────────────────

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

    // ── CORE VALIDATION LOGIC ─────────────────────────────────────────────────

    /**
     * Core validation logic with ticket status transition.
     *
     * State machine:
     *   CANCELLED → throws immediately (cancelled tickets cannot be validated)
     *   VALIDATED → throws immediately (already validated, FIX #7-1 guard)
     *   PURCHASED with no prior VALID scan → creates VALID TicketValidation,
     *                                        transitions Ticket.status to VALIDATED
     *   PURCHASED with prior VALID scan   → creates INVALID TicketValidation
     *                                        (duplicate scan — person re-scanned)
     *
     * The PURCHASED + prior-VALID branch handles the window between the first
     * scan (VALID created) and the DB flush that writes VALIDATED to the ticket.
     * In a highly concurrent scenario two scans for the same ticket could both
     * read PURCHASED and both reach the validations stream check, where the second
     * one will find the first's VALID record and correctly produce INVALID.
     */
    private TicketValidation validateTicket(Ticket ticket, TicketValidationMethod method, User validator) {

        // Guard 1: CANCELLED tickets — must check first
        if (TicketStatusEnum.CANCELLED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket " + ticket.getId() + " has been cancelled and cannot be validated.");
        }

        // Guard 2: Already VALIDATED — fast path, no need to check validations list
        if (TicketStatusEnum.VALIDATED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket " + ticket.getId() + " has already been validated and cannot be validated again.");
        }

        // Guard 3: Must be PURCHASED to proceed
        if (!TicketStatusEnum.PURCHASED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket must be in PURCHASED status to validate, but is " + ticket.getStatus());
        }

        // Determine validation result from prior scan history
        TicketValidationStatusEnum validationStatus = ticket.getValidations().stream()
                .filter(v -> TicketValidationStatusEnum.VALID.equals(v.getStatus()))
                .findFirst()
                .map(v -> TicketValidationStatusEnum.INVALID)   // prior VALID scan found → INVALID
                .orElse(TicketValidationStatusEnum.VALID);       // no prior VALID → first scan → VALID

        // FIX: Transition Ticket.status to VALIDATED on first successful scan.
        // This makes ticket.status meaningful for reporting and prevents the VALIDATED
        // enum value from being permanently dead code.
        if (TicketValidationStatusEnum.VALID.equals(validationStatus)) {
            ticket.setStatus(TicketStatusEnum.VALIDATED);
            ticketRepository.save(ticket);
            log.info("Ticket {} marked as VALIDATED after first successful scan", ticket.getId());
        }

        TicketValidation validation = new TicketValidation();
        validation.setTicket(ticket);
        validation.setValidationMethod(method);
        validation.setValidatedBy(validator);
        validation.setStatus(validationStatus);

        return ticketValidationRepository.save(validation);
    }

    // ── LIST / GET ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Page<TicketValidation> listValidationsForEvent(UUID userId, UUID eventId, Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        authorizationService.requireOrganizerOrStaffAccess(userId, event);
        return ticketValidationRepository.findByTicketTicketTypeEventId(eventId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketValidation> getValidationsByTicket(UUID userId, UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(TicketNotFoundException::new);
        authorizationService.requireOrganizerOrStaffAccess(userId, ticket.getTicketType().getEvent());
        return ticketValidationRepository.findByTicketId(ticketId);
    }

    // ── AUDIT HELPERS ─────────────────────────────────────────────────────────

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
}