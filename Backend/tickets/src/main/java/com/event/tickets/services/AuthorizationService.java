package com.event.tickets.services;

import com.event.tickets.domain.entities.Event;
import java.util.UUID;

/**
 * Centralized Authorization Service
 *
 * This service is the single source of truth for all event-related authorization decisions.
 * It enforces ownership and assignment rules without depending on JWT roles alone.
 *
 * Authorization Rules:
 * 1. ORGANIZER ACCESS: User must be the event organizer (event.organizer.id == userId)
 * 2. STAFF ACCESS: User must be assigned as staff to the event (event.staff contains user)
 * 3. ORGANIZER OR STAFF ACCESS: User must satisfy either rule 1 or rule 2
 *
 * All authorization checks fail fast with AccessDeniedException if unauthorized.
 */
public interface AuthorizationService {

  /**
   * Verifies that a user is the organizer of an event.
   *
   * @param userId The ID of the user attempting the operation
   * @param eventId The ID of the event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is not the organizer
   * @throws com.event.tickets.exceptions.EventNotFoundException if event does not exist
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireOrganizerAccess(UUID userId, UUID eventId);

  /**
   * Verifies that a user is the organizer of an event (when event entity is already loaded).
   *
   * @param userId The ID of the user attempting the operation
   * @param event The event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is not the organizer
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireOrganizerAccess(UUID userId, Event event);

  /**
   * Verifies that a user is assigned as staff to an event.
   *
   * @param userId The ID of the user attempting the operation
   * @param eventId The ID of the event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is not assigned as staff
   * @throws com.event.tickets.exceptions.EventNotFoundException if event does not exist
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireStaffAccess(UUID userId, UUID eventId);

  /**
   * Verifies that a user is assigned as staff to an event (when event entity is already loaded).
   *
   * @param userId The ID of the user attempting the operation
   * @param event The event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is not assigned as staff
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireStaffAccess(UUID userId, Event event);

  /**
   * Verifies that a user is either the organizer or assigned as staff to an event.
   *
   * @param userId The ID of the user attempting the operation
   * @param eventId The ID of the event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is neither organizer nor staff
   * @throws com.event.tickets.exceptions.EventNotFoundException if event does not exist
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireOrganizerOrStaffAccess(UUID userId, UUID eventId);

  /**
   * Verifies that a user is either the organizer or assigned as staff to an event
   * (when event entity is already loaded).
   *
   * @param userId The ID of the user attempting the operation
   * @param event The event being accessed
   * @throws org.springframework.security.access.AccessDeniedException if user is neither organizer nor staff
   * @throws com.event.tickets.exceptions.UserNotFoundException if user does not exist
   */
  void requireOrganizerOrStaffAccess(UUID userId, Event event);

  /**
   * Checks if a user is the organizer of an event without throwing exceptions.
   *
   * @param userId The ID of the user
   * @param event The event to check
   * @return true if user is the organizer, false otherwise
   */
  boolean isOrganizer(UUID userId, Event event);

  /**
   * Checks if a user is assigned as staff to an event without throwing exceptions.
   *
   * @param userId The ID of the user
   * @param event The event to check
   * @return true if user is assigned as staff, false otherwise
   */
  boolean isStaff(UUID userId, Event event);

  /**
   * Checks if a user is either the organizer or assigned as staff to an event
   * without throwing exceptions.
   *
   * @param userId The ID of the user
   * @param event The event to check
   * @return true if user has access (organizer or staff), false otherwise
   */
  boolean hasEventAccess(UUID userId, Event event);
}
