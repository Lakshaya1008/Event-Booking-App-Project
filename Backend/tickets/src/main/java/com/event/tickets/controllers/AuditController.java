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

/**
 * Audit Controller
 *
 * READ-ONLY access to audit logs.
 *
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX A-5 — getEventAuditLogs() now allows ADMIN access.
 *   BEFORE: @PreAuthorize("hasRole('ORGANIZER')") — ADMINs could not query logs
 *   for a specific event. They had to use GET /audit and filter client-side.
 *   AFTER: hasRole('ADMIN') or hasRole('ORGANIZER'). ADMIN bypasses the ownership
 *   check. ORGANIZER still goes through requireOrganizerAccess().
 *
 * FIX A-6 — GET /audit?action={action} endpoint added.
 *   AuditLogRepository.findByAction() and AuditLogService.findByAction() existed
 *   but were never exposed via any endpoint. Added as an optional query parameter
 *   on the existing GET /audit endpoint — cleaner than a separate URL.
 *
 * FIX A-7 — GET /audit/users/{userId} endpoint added.
 *   Allows ADMIN to query all actions targeting a specific user (USER_APPROVED,
 *   USER_REJECTED, ROLE_ASSIGNED, STAFF_ASSIGNED, etc.).
 *   Previously there was no server-side way to filter by target user.
 *
 * FIX A-9 — mapToDto() extracted to a private helper (preserved as-is).
 *   The mapping logic is correct and well-structured. A full MapStruct mapper
 *   would be the ideal final state but is out of scope for this audit cycle.
 *   The method is kept private to the controller for now — documented as
 *   a future improvement.
 *
 * Security:
 * - ADMIN:              All audit log endpoints
 * - ORGANIZER:          Their own event logs only (ownership enforced)
 * - Any authenticated:  Their own actions only (/me endpoint)
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    /**
     * All audit logs — ADMIN only.
     *
     * FIX A-6: Optional ?action= query parameter filters by AuditAction type.
     * When action is null, returns all logs (existing behaviour).
     * When action is provided (e.g. ?action=ROLE_ASSIGNED), filters in the DB.
     */
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

    /**
     * Audit logs for a specific event.
     *
     * FIX A-5: ADMIN now allowed in addition to ORGANIZER.
     * ORGANIZER still requires ownership check via requireOrganizerAccess().
     * ADMIN bypasses ownership check — they can audit any event.
     */
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

    /**
     * FIX A-7: All actions targeting a specific user — ADMIN only.
     *
     * Covers: USER_APPROVED, USER_REJECTED, ROLE_ASSIGNED, ROLE_REVOKED,
     * STAFF_ASSIGNED, STAFF_REMOVED, ADMIN_ROLE_GRANTED_VIA_INVITE.
     *
     * Previously there was no server-side query for "what has been done TO user X".
     * An admin investigating a user's history had to scroll all audit logs.
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsForUser(
            @PathVariable UUID userId,
            Pageable pageable
    ) {
        log.debug("ADMIN fetching audit logs targeting user '{}'", userId);
        return ResponseEntity.ok(auditLogService.findByTargetUserId(userId, pageable).map(this::mapToDto));
    }

    /**
     * FIX A-9 note: mapToDto() kept as a private controller method for this cycle.
     * Future improvement: extract to an AuditLogMapper (@Mapper) consistent with
     * other mappers in the codebase (DiscountMapper, EventMapper, etc.).
     */
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