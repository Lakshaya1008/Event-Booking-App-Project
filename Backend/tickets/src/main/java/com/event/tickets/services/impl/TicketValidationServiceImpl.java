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
 * FIXES APPLIED:
 *
 * FIX-TV1 (BUG 6-2) — validateTicket() replaced collection load with EXISTS query.
 *
 *   BEFORE: ticket.getValidations().stream().filter(VALID).findFirst() loaded ALL
 *   TicketValidation records for the ticket into the JPA session to detect a prior scan.
 *   At a busy event a ticket might have been scanned many times (each retry producing
 *   an INVALID record). All of those loaded just to find the first VALID one.
 *
 *   AFTER: ticketValidationRepository.existsByTicketIdAndStatus(ticketId, VALID)
 *   Executes one EXISTS query. Zero TicketValidation entities loaded into memory.
 *   This runs on every scan at the venue door — keeping it lean is critical.
 *
 * All previous fixes preserved:
 *   FIX 1 (previous) — Ticket.status transitions to VALIDATED on first valid scan.
 *   FIX 2 (previous) — Spring @Transactional throughout.
 *   FIX 3 (previous) — RequestUtil.getCurrentRequest() for audits.
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
                            String.format("QR Code with ID %s was not found or is not active", qrCodeId)));
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
     * State machine:
     *   CANCELLED  → throws (cannot validate a cancelled ticket)
     *   VALIDATED  → throws (already validated — fast path, no query needed)
     *   PURCHASED  → checks for prior VALID scan via EXISTS query
     *                  - no prior VALID → creates VALID record, transitions ticket to VALIDATED
     *                  - prior VALID found → creates INVALID record (duplicate scan)
     */
    private TicketValidation validateTicket(Ticket ticket, TicketValidationMethod method, User validator) {
        if (TicketStatusEnum.CANCELLED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket " + ticket.getId() + " has been cancelled and cannot be validated.");
        }

        // Fast path — VALIDATED status written after first scan, so second scan never reaches the DB check
        if (TicketStatusEnum.VALIDATED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket " + ticket.getId() + " has already been validated and cannot be validated again.");
        }

        if (!TicketStatusEnum.PURCHASED.equals(ticket.getStatus())) {
            throw new InvalidBusinessStateException(
                    "Ticket must be in PURCHASED status to validate, but is " + ticket.getStatus());
        }

        // FIX-TV1: Single EXISTS query — no TicketValidation collection loaded
        boolean hasPriorValidScan = ticketValidationRepository
                .existsByTicketIdAndStatus(ticket.getId(), TicketValidationStatusEnum.VALID);

        TicketValidationStatusEnum validationStatus = hasPriorValidScan
                ? TicketValidationStatusEnum.INVALID   // duplicate scan
                : TicketValidationStatusEnum.VALID;    // first valid scan

        // Transition ticket status to VALIDATED on first successful scan
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
                    .resourceType("TICKET").resourceId(ticket.getId())
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
                    .actor(user).targetUser(user)
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