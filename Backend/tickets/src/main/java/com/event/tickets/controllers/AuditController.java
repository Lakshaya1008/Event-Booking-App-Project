package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.AuditLogDto;
import com.event.tickets.domain.entities.AuditAction;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto>> getAllAuditLogs(
            Pageable pageable,
            @RequestParam(required = false) AuditAction action
    ) {
        if (action != null) {
            log.debug("ADMIN fetching audit logs filtered by action '{}'", action);
            return ResponseEntity.ok(auditLogService.findByAction(action, pageable).map(this::mapToDto));
        }
        log.debug("ADMIN fetching all audit logs");
        return ResponseEntity.ok(auditLogService.findAll(pageable).map(this::mapToDto));
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<Page<AuditLogDto>> getEventAuditLogs(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            Pageable pageable
    ) {
        UUID callerId = parseUserId(jwt);
        boolean isAdmin = hasAdminRole(jwt);

        if (!isAdmin) {
            // ORGANIZER must own the event
            authorizationService.requireOrganizerAccess(callerId, eventId);
            log.debug("ORGANIZER '{}' fetching audit logs for event '{}'", callerId, eventId);
        } else {
            log.debug("ADMIN '{}' fetching audit logs for event '{}'", callerId, eventId);
        }

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

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsForUser(
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        log.debug("ADMIN fetching audit logs targeting user '{}'", userId);
        return ResponseEntity.ok(auditLogService.findByTargetUserId(userId, pageable).map(this::mapToDto));
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
                .userAgent(auditLog.getUserAgent())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    /**
     * Reads realm_access.roles from the Keycloak JWT — same pattern as InviteCodeController.
     */
    private boolean hasAdminRole(Jwt jwt) {
        java.util.Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return false;
        Object roles = realmAccess.get("roles");
        if (roles instanceof java.util.Collection<?> roleList) {
            return roleList.contains("ADMIN");
        }
        return false;
    }
}