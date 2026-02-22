package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.AuditLogDto;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.AuthorizationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit Controller
 *
 * READ-ONLY access to audit logs. All queries go through AuditLogService —
 * never directly to AuditLogRepository — to maintain consistent service layering.
 *
 * Security:
 * - ADMIN:              Can view all audit logs
 * - ORGANIZER:          Can view audit logs for their own events (ownership enforced)
 * - Any authenticated:  Can view their own actions only
 *
 * Endpoints:
 * - GET /api/v1/audit                   - All logs (ADMIN only)
 * - GET /api/v1/audit/events/{eventId}  - Event logs (ORGANIZER must own event)
 * - GET /api/v1/audit/me                - Caller's own actions
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    /** All audit logs — ADMIN only. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto>> getAllAuditLogs(Pageable pageable) {
        log.debug("ADMIN fetching all audit logs");
        return ResponseEntity.ok(auditLogService.findAll(pageable).map(this::mapToDto));
    }

    /** Audit logs for a specific event — ORGANIZER must own the event. */
    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<Page<AuditLogDto>> getEventAuditLogs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            Pageable pageable
    ) {
        UUID organizerId = parseUserId(jwt);
        log.debug("ORGANIZER '{}' fetching audit logs for event '{}'", organizerId, eventId);
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        return ResponseEntity.ok(auditLogService.findByEventId(eventId, pageable).map(this::mapToDto));
    }

    /** Caller's own audit trail — any authenticated user. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AuditLogDto>> getMyAuditLogs(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        UUID userId = parseUserId(jwt);
        log.debug("User '{}' fetching their own audit logs", userId);
        return ResponseEntity.ok(auditLogService.findByActorId(userId, pageable).map(this::mapToDto));
    }

    private AuditLogDto mapToDto(AuditLog auditLog) {
        return AuditLogDto.builder()
                .id(auditLog.getId())
                .action(auditLog.getAction().name())
                .actorName(auditLog.getActor() != null ? auditLog.getActor().getName() : null)
                .actorId(auditLog.getActor() != null ? auditLog.getActor().getId() : null)
                .targetUserName(auditLog.getTargetUser() != null ? auditLog.getTargetUser().getName() : null)
                .targetUserId(auditLog.getTargetUser() != null ? auditLog.getTargetUser().getId() : null)
                .eventName(auditLog.getEvent() != null ? auditLog.getEvent().getName() : null)
                .eventId(auditLog.getEvent() != null ? auditLog.getEvent().getId() : null)
                .resourceType(auditLog.getResourceType())
                .resourceId(auditLog.getResourceId())
                .details(auditLog.getDetails())
                .ipAddress(auditLog.getIpAddress())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
