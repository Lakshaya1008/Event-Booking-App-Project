package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.AuditLogDto;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.repositories.AuditLogRepository;
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
 * READ-ONLY access to audit logs.
 *
 * Security:
 * - ADMIN: Can view all audit logs
 * - ORGANIZER: Can view audit logs for their own events
 * - Any authenticated user: Can view their own actions
 *
 * Enforcement:
 * - Append-only (no POST/PUT/DELETE)
 * - Pagination mandatory
 * - Authorization via AuthorizationService for event-scoped access
 *
 * Endpoints:
 * - GET /audit - All logs (ADMIN only)
 * - GET /audit/events/{eventId} - Event logs (ORGANIZER must own event)
 * - GET /audit/me - User's own actions (authenticated)
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

  private final AuditLogRepository auditLogRepository;
  private final AuthorizationService authorizationService;

  /**
   * Get all audit logs.
   *
   * ADMIN only - complete audit trail access.
   *
   * @param pageable Pagination parameters
   * @return Page of audit logs
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Page<AuditLogDto>> getAllAuditLogs(Pageable pageable) {
    log.debug("ADMIN fetching all audit logs");

    Page<AuditLogDto> auditLogs = auditLogRepository.findAll(pageable)
        .map(this::mapToDto);

    return ResponseEntity.ok(auditLogs);
  }

  /**
   * Get audit logs for a specific event.
   *
   * ORGANIZER must own the event.
   * Authorization enforced via AuthorizationService.
   *
   * @param jwt JWT token containing user ID
   * @param eventId Event ID
   * @param pageable Pagination parameters
   * @return Page of audit logs for the event
   */
  @GetMapping("/events/{eventId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<Page<AuditLogDto>> getEventAuditLogs(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      Pageable pageable
  ) {
    UUID organizerId = parseUserId(jwt);

    log.debug("ORGANIZER '{}' fetching audit logs for event '{}'", organizerId, eventId);

    // Authorization: verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, eventId);

    Page<AuditLogDto> auditLogs = auditLogRepository.findByEventId(eventId, pageable)
        .map(this::mapToDto);

    return ResponseEntity.ok(auditLogs);
  }

  /**
   * Get user's own audit trail.
   *
   * Any authenticated user can view their own actions.
   *
   * @param jwt JWT token containing user ID
   * @param pageable Pagination parameters
   * @return Page of user's own audit logs
   */
  @GetMapping("/me")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Page<AuditLogDto>> getMyAuditLogs(
      @AuthenticationPrincipal Jwt jwt,
      Pageable pageable
  ) {
    UUID userId = parseUserId(jwt);

    log.debug("User '{}' fetching their own audit logs", userId);

    Page<AuditLogDto> auditLogs = auditLogRepository.findByActorId(userId, pageable)
        .map(this::mapToDto);

    return ResponseEntity.ok(auditLogs);
  }

  /**
   * Maps AuditLog entity to DTO.
   *
   * @param auditLog Audit log entity
   * @return Audit log DTO
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
        .createdAt(auditLog.getCreatedAt())
        .build();
  }
}
