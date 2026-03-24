package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.KeycloakAdminService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * FIXES APPLIED:
 *
 * FIX-AZ1 (BUG 6-1) — isStaff() now uses EventRepository.isStaffMember() COUNT query.
 *
 *   BEFORE: isStaff(userId, event) called event.getStaff().stream().anyMatch(...)
 *   This triggered Hibernate lazy-loading of the full @ManyToMany staff collection.
 *   For a large event with 50 staff members, this loaded 50 User entities into the JPA
 *   session just to check if one specific user was in the list.
 *
 *   EventRepository.isStaffMember() was added in a previous fix (from Feature 3 audit)
 *   and executes: SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s WHERE e.id=? AND s.id=?
 *   — a single EXISTS-style query that returns boolean without loading any entities.
 *
 *   AFTER: isStaff(userId, event) calls eventRepository.isStaffMember(event.getId(), userId).
 *   Zero staff entities loaded. One COUNT query instead of a full collection fetch.
 *
 *   NOTE: The isStaff(userId, event) overload is called by:
 *   - requireOrganizerOrStaffAccess() in both overloads
 *   - hasEventAccess()
 *   - TicketValidationServiceImpl (every scan call)
 *   This fix improves every ticket scan at the door — the highest-frequency operation
 *   at a live event.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationServiceImpl implements AuthorizationService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

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
            log.warn("Access denied: User '{}' is not the organizer of event '{}'",
                    userId, event.getId());
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
            log.warn("Access denied: User '{}' is not assigned as staff to event '{}'",
                    userId, event.getId());
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        List<String> roles = keycloakAdminService.getUserRoles(userId);

        if (roles != null && roles.contains("ADMIN")) {
            return;
        }

        if (roles != null && roles.contains("ORGANIZER")) {
            if (event.getOrganizer() == null || !event.getOrganizer().getId().equals(userId)) {
                throw new AccessDeniedException("Organizer cannot access this event");
            }
            return;
        }

        if (roles != null && roles.contains("STAFF")) {
            // FIX-AZ1 (consistent): Use COUNT query — no staff collection loaded into memory.
            // isStaff() already uses eventRepository.isStaffMember() — reuse it here
            // instead of reloading the full @ManyToMany staff collection.
            if (!isStaff(userId, event)) {
                throw new AccessDeniedException("Staff not assigned to this event");
            }
            return;
        }

        log.warn("Access denied: user '{}' has no event permission for event '{}'", user.getId(), event.getId());
        throw new AccessDeniedException("User does not have permission for this event");
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
     * FIX-AZ1: Uses EventRepository.isStaffMember() COUNT query instead of loading
     * the full staff @ManyToMany collection.
     *
     * isStaffMember executes:
     *   SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s WHERE e.id=:eventId AND s.id=:userId
     *
     * This is called on every ticket scan — keeping it to one lightweight query is critical
     * for performance at the venue door where scans happen in rapid succession.
     */
    @Override
    public boolean isStaff(UUID userId, Event event) {
        // FIX-AZ1: COUNT query — no staff collection loaded into memory
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