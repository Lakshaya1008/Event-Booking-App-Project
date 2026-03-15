package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.StaffMemberDto;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.InvalidBusinessStateException;
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
 * Event Staff Management Service
 *
 * FIX #14: Replaced event.getStaff().contains(user) with an explicit ID-based check.
 *
 * ROOT CAUSE:
 * User.equals() previously compared id + name + email + createdAt + updatedAt.
 * Two references to the same user loaded at different points in a transaction
 * could have different updatedAt values (due to JPA flush), making contains()
 * return false — allowing the same user to be added to staff multiple times.
 *
 * The fix uses anyMatch(s -> s.getId().equals(userId)) which is independent of
 * any mutable fields and always correctly identifies the same user.
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
        log.info("Assigning user '{}' as staff to event '{}' by organizer '{}'",
                userId, eventId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        if (!keycloakAdminService.userHasRole(userId, "STAFF")) {
            throw new InvalidBusinessStateException(String.format(
                    "User '%s' (%s) does not have STAFF role. " +
                            "STAFF role must be assigned by ADMIN before event assignment.",
                    user.getName(), userId));
        }

        // FIX #14: ID-based check instead of contains(user) which uses broken equals()
        boolean alreadyAssigned = event.getStaff().stream()
                .anyMatch(s -> s.getId().equals(userId));

        if (alreadyAssigned) {
            throw new InvalidBusinessStateException(String.format(
                    "User '%s' is already assigned as staff to event '%s'",
                    user.getName(), event.getName()));
        }

        event.getStaff().add(user);
        eventRepository.save(event);

        log.info("Successfully assigned user '{}' as staff to event '{}'",
                user.getName(), event.getName());

        User organizer = userRepository.findById(organizerId)
                .orElseGet(systemUserProvider::getSystemUser);
        HttpServletRequest request = getCurrentRequest();

        AuditLog auditLog = AuditLog.builder()
                .action(AuditAction.STAFF_ASSIGNED)
                .actor(organizer).targetUser(user).event(event)
                .resourceType("EventStaff").resourceId(event.getId())
                .details(String.format("Assigned %s as staff to event: %s",
                        user.getName(), event.getName()))
                .ipAddress(extractClientIp(request))
                .userAgent(extractUserAgent(request))
                .build();
        auditLogService.saveAuditLog(auditLog);
    }

    @Override
    @Transactional
    public void removeStaffFromEvent(UUID organizerId, UUID eventId, UUID userId) {
        log.info("Removing user '{}' from staff of event '{}' by organizer '{}'",
                userId, eventId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        // FIX #14: ID-based removal
        boolean removed = event.getStaff().removeIf(s -> s.getId().equals(userId));

        if (removed) {
            eventRepository.save(event);
            log.info("Removed user '{}' from staff of event '{}'", user.getName(), event.getName());

            User organizer = userRepository.findById(organizerId)
                    .orElseGet(systemUserProvider::getSystemUser);
            HttpServletRequest request = getCurrentRequest();

            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.STAFF_REMOVED)
                    .actor(organizer).targetUser(user).event(event)
                    .resourceType("EventStaff").resourceId(event.getId())
                    .details(String.format("Removed %s from staff of event: %s",
                            user.getName(), event.getName()))
                    .ipAddress(extractClientIp(request))
                    .userAgent(extractUserAgent(request))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } else {
            log.warn("User '{}' was not staff of event '{}'", user.getName(), event.getName());
        }
    }

    @Override
    public List<StaffMemberDto> listEventStaff(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
        return event.getStaff().stream()
                .map(u -> new StaffMemberDto(u.getId(), u.getName(), u.getEmail()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isStaffAssignedToEvent(UUID eventId, UUID userId) {
        return eventRepository.findById(eventId)
                .map(event -> event.getStaff().stream()
                        .anyMatch(s -> s.getId().equals(userId))) // FIX #14
                .orElse(false);
    }

    @Override
    public String getEventName(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)))
                .getName();
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}