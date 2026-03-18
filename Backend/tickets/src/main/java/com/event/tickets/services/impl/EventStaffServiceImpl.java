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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

/**
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX 1 — jakarta.transaction.@Transactional replaced with org.springframework.
 *   assignStaffToEvent() and removeStaffFromEvent() now use Spring-managed transactions.
 *   listEventStaff() and isStaffAssignedToEvent() get @Transactional(readOnly=true).
 *
 * FIX 2 — getCurrentRequest() private copy-paste removed.
 *   Replaced with static import of RequestUtil.getCurrentRequest().
 *
 * All other fixes (#14 ID-based duplicate check, L-21 throw on non-existent removal) preserved.
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
    @Transactional  // FIX 1: org.springframework
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

        // FIX #14: ID-based check instead of contains(user) — independent of mutable fields
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

        // FIX 2: RequestUtil.getCurrentRequest()
        AuditLog auditLog = AuditLog.builder()
                .action(AuditAction.STAFF_ASSIGNED)
                .actor(organizer).targetUser(user).event(event)
                .resourceType("EventStaff").resourceId(event.getId())
                .details(String.format("Assigned %s as staff to event: %s",
                        user.getName(), event.getName()))
                .ipAddress(extractClientIp(getCurrentRequest()))
                .userAgent(extractUserAgent(getCurrentRequest()))
                .build();
        auditLogService.saveAuditLog(auditLog);
    }

    @Override
    @Transactional  // FIX 1: org.springframework
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

            // FIX 2: RequestUtil.getCurrentRequest()
            AuditLog auditLog = AuditLog.builder()
                    .action(AuditAction.STAFF_REMOVED)
                    .actor(organizer).targetUser(user).event(event)
                    .resourceType("EventStaff").resourceId(event.getId())
                    .details(String.format("Removed %s from staff of event: %s",
                            user.getName(), event.getName()))
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } else {
            // L-21 FIX: throw instead of silently logging — 400 makes contract clear
            throw new InvalidBusinessStateException(
                    String.format("User '%s' is not assigned as staff to event '%s'.",
                            user.getName(), event.getName()));
        }
    }

    @Override
    @Transactional(readOnly = true)  // FIX 1: read-only transaction
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
    @Transactional(readOnly = true)  // FIX 1: read-only transaction
    public boolean isStaffAssignedToEvent(UUID eventId, UUID userId) {
        return eventRepository.findById(eventId)
                .map(event -> event.getStaff().stream()
                        .anyMatch(s -> s.getId().equals(userId)))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public String getEventName(UUID eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)))
                .getName();
    }
}