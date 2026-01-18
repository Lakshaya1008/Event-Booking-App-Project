package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.AssignStaffRequestDto;
import com.event.tickets.domain.dtos.EventStaffResponseDto;
import com.event.tickets.domain.dtos.StaffMemberDto;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.services.EventStaffService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event Staff Management Controller
 *
 * Provides ORGANIZER-only endpoints for managing event-scoped staff assignments.
 *
 * Business Model:
 * - Only event organizers can manage staff for their events
 * - STAFF role must exist in Keycloak (assigned by ADMIN)
 * - Event-staff relationship persisted in database (user_staffing_events)
 * - STAFF role alone provides no access without event assignment
 *
 * Authorization:
 * - Controller checks ORGANIZER role
 * - Service enforces event ownership via AuthorizationService
 * - No direct Keycloak Admin API calls from organizers
 *
 * Endpoints:
 * - POST /events/{eventId}/staff - Assign staff to event
 * - DELETE /events/{eventId}/staff/{userId} - Remove staff from event
 * - GET /events/{eventId}/staff - List event staff
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/staff")
@RequiredArgsConstructor
@Slf4j
public class EventStaffController {

  private final EventStaffService eventStaffService;
  private final EventRepository eventRepository;

  /**
   * Assign a staff member to an event.
   *
   * Requirements:
   * - User must be authenticated as ORGANIZER
   * - Must be the event organizer
   * - Target user must have STAFF role in Keycloak
   *
   * @param jwt JWT token containing organizer ID
   * @param eventId The ID of the event
   * @param request The staff assignment request
   * @return Event staff response with updated staff list
   */
  @PostMapping
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<EventStaffResponseDto> assignStaffToEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @Valid @RequestBody AssignStaffRequestDto request
  ) {
    UUID organizerId = parseUserId(jwt);
    UUID userId = request.getUserId();

    log.info("Organizer '{}' assigning staff '{}' to event '{}'",
        organizerId, userId, eventId);

    // Assign staff (authorization enforced in service)
    eventStaffService.assignStaffToEvent(organizerId, eventId, userId);

    // Fetch updated staff list
    List<StaffMemberDto> staffList = eventStaffService.listEventStaff(organizerId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    EventStaffResponseDto response = new EventStaffResponseDto(
        eventId,
        event.getName(),
        staffList,
        staffList.size()
    );

    log.info("Successfully assigned staff '{}' to event '{}'", userId, eventId);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  /**
   * Remove a staff member from an event.
   *
   * Requirements:
   * - User must be authenticated as ORGANIZER
   * - Must be the event organizer
   *
   * @param jwt JWT token containing organizer ID
   * @param eventId The ID of the event
   * @param userId The ID of the staff member to remove
   * @return Event staff response with updated staff list
   */
  @DeleteMapping("/{userId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<EventStaffResponseDto> removeStaffFromEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @PathVariable UUID userId
  ) {
    UUID organizerId = parseUserId(jwt);

    log.info("Organizer '{}' removing staff '{}' from event '{}'",
        organizerId, userId, eventId);

    // Remove staff (authorization enforced in service)
    eventStaffService.removeStaffFromEvent(organizerId, eventId, userId);

    // Fetch updated staff list
    List<StaffMemberDto> staffList = eventStaffService.listEventStaff(organizerId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    EventStaffResponseDto response = new EventStaffResponseDto(
        eventId,
        event.getName(),
        staffList,
        staffList.size()
    );

    log.info("Successfully removed staff '{}' from event '{}'", userId, eventId);
    return ResponseEntity.ok(response);
  }

  /**
   * List all staff members assigned to an event.
   *
   * Requirements:
   * - User must be authenticated as ORGANIZER
   * - Must be the event organizer
   *
   * @param jwt JWT token containing organizer ID
   * @param eventId The ID of the event
   * @return Event staff response with staff list
   */
  @GetMapping
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<EventStaffResponseDto> listEventStaff(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId
  ) {
    UUID organizerId = parseUserId(jwt);

    log.debug("Organizer '{}' listing staff for event '{}'", organizerId, eventId);

    // List staff (authorization enforced in service)
    List<StaffMemberDto> staffList = eventStaffService.listEventStaff(organizerId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    EventStaffResponseDto response = new EventStaffResponseDto(
        eventId,
        event.getName(),
        staffList,
        staffList.size()
    );

    return ResponseEntity.ok(response);
  }
}
