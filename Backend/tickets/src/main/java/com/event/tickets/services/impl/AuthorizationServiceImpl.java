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
     * Checks staff membership using a repository query.
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