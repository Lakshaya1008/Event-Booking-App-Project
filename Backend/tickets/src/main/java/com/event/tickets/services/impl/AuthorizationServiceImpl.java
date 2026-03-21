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
 * FIXES APPLIED:
 *
 * FIX-AZ1 — isStaff() no longer loads the full staff collection.
 *   BEFORE: event.getStaff().stream().anyMatch(...) triggered a full
 *   SELECT from user_staffing_events for every auth check, loading every
 *   staff member into the JPA session. For a large event this is an
 *   unbounded in-memory load on every ticket validation and staff action.
 *   AFTER: Uses EventRepository.isStaffMember(eventId, userId) — a single
 *   COUNT EXISTS query that returns immediately without loading any entities.
 *
 *   Requires adding this method to EventRepository:
 *   @Query("SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s WHERE e.id = :eventId AND s.id = :userId")
 *   boolean isStaffMember(@Param("eventId") UUID eventId, @Param("userId") UUID userId);
 *
 *   The isOrganizer() check continues to use event.getOrganizer().getId() which
 *   is a direct FK column — no collection load, already efficient.
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
                        String.format("Event with ID '%s' not found", eventId)));
        requireOrganizerAccess(userId, event);
    }

    @Override
    public void requireOrganizerAccess(UUID userId, Event event) {
        log.debug("Checking organizer access: userId={}, eventId={}", userId, event.getId());
        verifyUserExists(userId);
        if (!isOrganizer(userId, event)) {
            log.warn("Access denied: User '{}' is not the organizer of event '{}'", userId, event.getId());
            throw new AccessDeniedException(String.format(
                    "Access denied. User '%s' is not the organizer of event '%s' (%s). " +
                            "Only the event organizer can perform this operation.",
                    userId, event.getId(), event.getName()));
        }
        log.debug("Organizer access granted: userId={}, eventId={}", userId, event.getId());
    }

    @Override
    public void requireStaffAccess(UUID userId, UUID eventId) {
        log.debug("Checking staff access: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        requireStaffAccess(userId, event);
    }

    @Override
    public void requireStaffAccess(UUID userId, Event event) {
        log.debug("Checking staff access: userId={}, eventId={}", userId, event.getId());
        verifyUserExists(userId);
        if (!isStaff(userId, event)) {
            log.warn("Access denied: User '{}' is not staff of event '{}'", userId, event.getId());
            throw new AccessDeniedException(String.format(
                    "Access denied. User '%s' is not assigned as staff to event '%s' (%s). " +
                            "Contact the event organizer to be assigned as staff.",
                    userId, event.getId(), event.getName()));
        }
        log.debug("Staff access granted: userId={}, eventId={}", userId, event.getId());
    }

    @Override
    public void requireOrganizerOrStaffAccess(UUID userId, UUID eventId) {
        log.debug("Checking organizer or staff access: userId={}, eventId={}", userId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        requireOrganizerOrStaffAccess(userId, event);
    }

    @Override
    public void requireOrganizerOrStaffAccess(UUID userId, Event event) {
        log.debug("Checking organizer or staff access: userId={}, eventId={}", userId, event.getId());
        verifyUserExists(userId);
        if (!hasEventAccess(userId, event)) {
            log.warn("Access denied: User '{}' is neither organizer nor staff for event '{}'",
                    userId, event.getId());
            throw new AccessDeniedException(String.format(
                    "Access denied. User '%s' is not authorized to access event '%s' (%s). " +
                            "You must be either the event organizer or assigned as staff.",
                    userId, event.getId(), event.getName()));
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

    /**
     * FIX-AZ1: Uses a COUNT EXISTS query instead of loading the full staff collection.
     * For events where the entity is already loaded (requireStaffAccess(userId, event)),
     * we delegate to the repository using the event's ID — one lightweight query.
     */
    @Override
    public boolean isStaff(UUID userId, Event event) {
        return eventRepository.isStaffMember(event.getId(), userId);
    }

    @Override
    public boolean hasEventAccess(UUID userId, Event event) {
        return isOrganizer(userId, event) || isStaff(userId, event);
    }

    private void verifyUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            log.error("User '{}' not found in database", userId);
            throw new UserNotFoundException(
                    String.format("User with ID '%s' not found", userId));
        }
    }
}