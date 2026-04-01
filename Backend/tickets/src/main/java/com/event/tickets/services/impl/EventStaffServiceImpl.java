package com.event.tickets.services.impl;

import com.event.tickets.domain.dtos.EventStaffResponseDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;
import static com.event.tickets.util.RequestUtil.getCurrentRequest;

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

    // ── ASSIGN ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EventStaffResponseDto assignStaffToEvent(UUID organizerId, UUID eventId, UUID userId) {
        log.info("Assigning user '{}' as staff to event '{}' by organizer '{}'",
                userId, eventId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        User organizer = userRepository.findById(organizerId)
                .orElseGet(systemUserProvider::getSystemUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        // Wrapped to produce a clear error if Keycloak is unreachable.
        try {
            if (!keycloakAdminService.userHasRole(userId, "STAFF")) {
                throw new InvalidBusinessStateException(String.format(
                        "User '%s' (%s) does not have STAFF role in Keycloak. " +
                                "STAFF role must be assigned by an ADMIN before event assignment.",
                        user.getName(), userId));
            }
        } catch (InvalidBusinessStateException e) {
            throw e; // re-throw business exceptions unchanged
        } catch (Exception e) {
            log.error("Keycloak unreachable during STAFF role check for user '{}': {}", userId, e.getMessage());
            throw new InvalidBusinessStateException(
                    "Could not verify STAFF role — Keycloak is temporarily unavailable. " +
                            "Please try again shortly.");
        }

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

        emitStaffAudit(AuditAction.STAFF_ASSIGNED, organizer, user, event,
                String.format("Assigned %s as staff to event: %s", user.getName(), event.getName()));

        List<StaffMemberDto> staffList = eventRepository.findStaffByEventId(eventId);
        return new EventStaffResponseDto(eventId, event.getName(), staffList, staffList.size());
    }

    // ── REMOVE ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EventStaffResponseDto removeStaffFromEvent(UUID organizerId, UUID eventId, UUID userId) {
        log.info("Removing user '{}' from staff of event '{}' by organizer '{}'",
                userId, eventId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        User organizer = userRepository.findById(organizerId)
                .orElseGet(systemUserProvider::getSystemUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        boolean removed = event.getStaff().removeIf(s -> s.getId().equals(userId));

        if (!removed) {
            // L-21 FIX (preserved): throw instead of silently logging
            throw new InvalidBusinessStateException(String.format(
                    "User '%s' is not assigned as staff to event '%s'.",
                    user.getName(), event.getName()));
        }

        eventRepository.save(event);
        log.info("Removed user '{}' from staff of event '{}'", user.getName(), event.getName());

        emitStaffAudit(AuditAction.STAFF_REMOVED, organizer, user, event,
                String.format("Removed %s from staff of event: %s", user.getName(), event.getName()));

        List<StaffMemberDto> staffList = eventRepository.findStaffByEventId(eventId);
        return new EventStaffResponseDto(eventId, event.getName(), staffList, staffList.size());
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<StaffMemberDto> listEventStaff(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        // Verify event exists before returning staff (throws EventNotFoundException if not)
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(
                    String.format("Event with ID '%s' not found", eventId));
        }
        return eventRepository.findStaffByEventId(eventId);
    }

    // ── GET EVENT NAME ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String getEventName(UUID eventId) {
        return eventRepository.findEventNameById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
    }

    // ── IS STAFF ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean isStaffAssignedToEvent(UUID eventId, UUID userId) {
        return eventRepository.isStaffMember(eventId, userId);
    }

    // ── AUDIT HELPER ─────────────────────────────────────────────────────────

    private void emitStaffAudit(AuditAction action, User actor, User target,
                                Event event, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .actor(actor).targetUser(target).event(event)
                    .resourceType("EventStaff").resourceId(event.getId())
                    .details(details)
                    .ipAddress(extractClientIp(getCurrentRequest()))
                    .userAgent(extractUserAgent(getCurrentRequest()))
                    .build();
            auditLogService.saveAuditLog(auditLog);
        } catch (Exception e) {
            log.error("Failed to emit {} audit for event '{}': {}", action, event.getId(), e.getMessage());
        }
    }
}