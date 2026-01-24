package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.StaffMemberDto;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.EventStaffService;
import com.event.tickets.services.KeycloakAdminService;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.services.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Event Staff Management Service Implementation
 *
 * Manages event-scoped staff assignments with proper authorization.
 *
 * Key Principles:
 * - Organizers can only manage staff for events they own
 * - Authorization enforced via AuthorizationService
 * - STAFF role must exist in Keycloak (assigned by ADMIN)
 * - Event-staff relationship persisted in database
 * - STAFF role alone provides no access without event assignment
 *
 * Database:
 * - Uses user_staffing_events junction table
 * - Maintains event.staff Many-to-Many relationship
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventStaffServiceImpl implements EventStaffService {

  private final EventRepository eventRepository;
  private final UserRepository userRepository;
  private final AuthorizationService authorizationService;
  private final KeycloakAdminService keycloakAdminService;
  private final SystemUserProvider systemUserProvider;
  private final AuditLogService auditLogService;

  @Override
  @Transactional
  public void assignStaffToEvent(UUID organizerId, UUID eventId, UUID userId) {
    log.info("Organizer '{}' assigning user '{}' as staff to event '{}'",
        organizerId, userId, eventId);

    // Authorization: Verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, eventId);

    // Fetch event
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    // Fetch user
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    // Verify user has STAFF role in Keycloak
    if (!keycloakAdminService.userHasRole(userId, "STAFF")) {
      throw new IllegalStateException(
          String.format(
              "User '%s' (%s) does not have STAFF role. " +
              "STAFF role must be assigned by ADMIN before event assignment.",
              user.getName(), userId
          )
      );
    }

    // Check if already assigned
    if (event.getStaff().contains(user)) {
      throw new IllegalStateException(
          String.format(
              "User '%s' is already assigned as staff to event '%s'",
              user.getName(), event.getName()
          )
      );
    }

    // Assign staff to event (persists in user_staffing_events table)
    event.getStaff().add(user);
    eventRepository.save(event);

    log.info("Successfully assigned user '{}' as staff to event '{}'",
        user.getName(), event.getName());

    // Audit log
    User organizer = userRepository.findById(organizerId).orElse(null);
    if (organizer == null) {
      organizer = systemUserProvider.getSystemUser();
    }
    HttpServletRequest request = getCurrentRequest();
    String ipAddress = extractClientIp(request);

    AuditLog auditLog = AuditLog.builder()
        .action(AuditAction.STAFF_ASSIGNED)
        .actor(organizer)
        .targetUser(user)
        .event(event)
        .resourceType("EventStaff")
        .resourceId(event.getId())
        .details(String.format("Assigned %s as staff to event: %s",
            user.getName(), event.getName()))
        .ipAddress(ipAddress)
        .userAgent(extractUserAgent(request))
        .build();

    auditLogService.saveAuditLog(auditLog);
  }

  @Override
  @Transactional
  public void removeStaffFromEvent(UUID organizerId, UUID eventId, UUID userId) {
    log.info("Organizer '{}' removing user '{}' from staff of event '{}'",
        organizerId, userId, eventId);

    // Authorization: Verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, eventId);

    // Fetch event
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    // Fetch user
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(
            String.format("User with ID '%s' not found", userId)
        ));

    // Remove staff from event (removes from user_staffing_events table)
    boolean removed = event.getStaff().remove(user);

    if (removed) {
      eventRepository.save(event);
      log.info("Successfully removed user '{}' from staff of event '{}'",
          user.getName(), event.getName());

      // Audit log - get organizer (already validated by authorization)
      User organizer = userRepository.findById(organizerId).orElse(null);
      if (organizer == null) {
        organizer = systemUserProvider.getSystemUser();
      }
      HttpServletRequest request = getCurrentRequest();
      String ipAddress = extractClientIp(request);

      AuditLog auditLog = AuditLog.builder()
          .action(AuditAction.STAFF_REMOVED)
          .actor(organizer)
          .targetUser(user)
          .event(event)
          .resourceType("EventStaff")
          .resourceId(event.getId())
          .details(String.format("Removed %s from staff of event: %s",
              user.getName(), event.getName()))
          .ipAddress(ipAddress)
          .userAgent(extractUserAgent(request))
          .build();

      auditLogService.saveAuditLog(auditLog);
    } else {
      log.warn("User '{}' was not assigned as staff to event '{}'",
          user.getName(), event.getName());
    }
  }

  @Override
  public List<StaffMemberDto> listEventStaff(UUID organizerId, UUID eventId) {
    log.debug("Organizer '{}' listing staff for event '{}'", organizerId, eventId);

    // Authorization: Verify organizer owns the event
    authorizationService.requireOrganizerAccess(organizerId, eventId);

    // Fetch event
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    // Map staff to DTOs
    List<StaffMemberDto> staffList = event.getStaff().stream()
        .map(user -> new StaffMemberDto(
            user.getId(),
            user.getName(),
            user.getEmail()
        ))
        .collect(Collectors.toList());

    log.debug("Event '{}' has {} staff members", event.getName(), staffList.size());
    return staffList;
  }

  @Override
  public boolean isStaffAssignedToEvent(UUID eventId, UUID userId) {
    return eventRepository.findById(eventId)
        .map(event -> event.getStaff().stream()
            .anyMatch(staff -> staff.getId().equals(userId)))
        .orElse(false);
  }

  /**
   * Gets current HTTP request for audit logging.
   */
  private HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }
}
