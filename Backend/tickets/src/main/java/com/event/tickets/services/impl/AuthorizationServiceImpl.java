package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Centralized Authorization Service Implementation
 *
 * This service is the SINGLE SOURCE OF TRUTH for all event-related authorization.
 * It enforces business rules around event ownership and staff assignment.
 *
 * Key Principles:
 * 1. Does NOT trust JWT roles alone - validates actual database relationships
 * 2. Fails fast with AccessDeniedException for unauthorized access
 * 3. Provides both enforcing methods (throw exceptions) and checking methods (return boolean)
 * 4. All sensitive operations must call this service
 *
 * Authorization Model:
 * - ORGANIZER ACCESS: event.organizer.id == userId
 * - STAFF ACCESS: event.staff collection contains user with userId
 * - ORGANIZER OR STAFF: Either condition above is satisfied
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationServiceImpl implements AuthorizationService {

  private final EventRepository eventRepository;
  private final UserRepository userRepository;

  @Override
  public void requireOrganizerAccess(UUID userId, UUID eventId) {
    log.debug("Checking organizer access: userId={}, eventId={}", userId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    requireOrganizerAccess(userId, event);
  }

  @Override
  public void requireOrganizerAccess(UUID userId, Event event) {
    log.debug("Checking organizer access: userId={}, eventId={}, eventName={}",
        userId, event.getId(), event.getName());

    verifyUserExists(userId);

    if (!isOrganizer(userId, event)) {
      log.warn("Access denied: User '{}' is not the organizer of event '{}'",
          userId, event.getId());
      throw new AccessDeniedException(
          String.format(
              "Access denied. User '%s' is not the organizer of event '%s' (%s). " +
              "Only the event organizer can perform this operation.",
              userId, event.getId(), event.getName()
          )
      );
    }

    log.debug("Organizer access granted: userId={}, eventId={}", userId, event.getId());
  }

  @Override
  public void requireStaffAccess(UUID userId, UUID eventId) {
    log.debug("Checking staff access: userId={}, eventId={}", userId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    requireStaffAccess(userId, event);
  }

  @Override
  public void requireStaffAccess(UUID userId, Event event) {
    log.debug("Checking staff access: userId={}, eventId={}, eventName={}",
        userId, event.getId(), event.getName());

    verifyUserExists(userId);

    if (!isStaff(userId, event)) {
      log.warn("Access denied: User '{}' is not assigned as staff to event '{}'",
          userId, event.getId());
      throw new AccessDeniedException(
          String.format(
              "Access denied. User '%s' is not assigned as staff to event '%s' (%s). " +
              "Only staff members assigned to this event can perform this operation. " +
              "Contact the event organizer to be assigned as staff.",
              userId, event.getId(), event.getName()
          )
      );
    }

    log.debug("Staff access granted: userId={}, eventId={}", userId, event.getId());
  }

  @Override
  public void requireOrganizerOrStaffAccess(UUID userId, UUID eventId) {
    log.debug("Checking organizer or staff access: userId={}, eventId={}", userId, eventId);

    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    requireOrganizerOrStaffAccess(userId, event);
  }

  @Override
  public void requireOrganizerOrStaffAccess(UUID userId, Event event) {
    log.debug("Checking organizer or staff access: userId={}, eventId={}, eventName={}",
        userId, event.getId(), event.getName());

    verifyUserExists(userId);

    if (!hasEventAccess(userId, event)) {
      log.warn("Access denied: User '{}' is neither organizer nor staff for event '{}'",
          userId, event.getId());
      throw new AccessDeniedException(
          String.format(
              "Access denied. User '%s' is not authorized to access event '%s' (%s). " +
              "You must be either the event organizer or assigned as staff to this event. " +
              "Contact the event organizer for access.",
              userId, event.getId(), event.getName()
          )
      );
    }

    log.debug("Event access granted: userId={}, eventId={}", userId, event.getId());
  }

  @Override
  public boolean isOrganizer(UUID userId, Event event) {
    if (event.getOrganizer() == null) {
      log.warn("Event '{}' has no organizer assigned", event.getId());
      return false;
    }

    return event.getOrganizer().getId().equals(userId);
  }

  @Override
  public boolean isStaff(UUID userId, Event event) {
    if (event.getStaff() == null || event.getStaff().isEmpty()) {
      log.debug("Event '{}' has no staff assigned", event.getId());
      return false;
    }

    return event.getStaff().stream()
        .anyMatch(staff -> staff.getId().equals(userId));
  }

  @Override
  public boolean hasEventAccess(UUID userId, Event event) {
    return isOrganizer(userId, event) || isStaff(userId, event);
  }

  /**
   * Verifies that a user exists in the database.
   * This ensures we're working with valid user IDs from the JWT.
   *
   * @param userId The user ID to verify
   * @throws UserNotFoundException if user does not exist
   */
  private void verifyUserExists(UUID userId) {
    if (!userRepository.existsById(userId)) {
      log.error("User '{}' not found in database", userId);
      throw new UserNotFoundException(
          String.format("User with ID '%s' not found", userId)
      );
    }
  }
}
