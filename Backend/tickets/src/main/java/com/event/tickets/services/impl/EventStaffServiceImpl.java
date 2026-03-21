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

/**
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX S-1 (BUG S-1) — staff collection no longer loaded 3× per request.
 *   BEFORE: The controller called assignStaffToEvent() (void), then listEventStaff()
 *   (reloads event + staff), then getEventName() (reloads event again) — 3+ extra DB
 *   queries per mutating request.
 *   AFTER: assignStaffToEvent() and removeStaffFromEvent() return EventStaffResponseDto
 *   built from data already in memory. The controller returns the result directly.
 *   findStaffByEventId() projection query retrieves only id/name/email per staff member.
 *
 * FIX S-2 (BUG S-2) — Keycloak call for STAFF role check is retained but documented.
 *   The business rule (user must have STAFF Keycloak role before event assignment) is
 *   correct and important. The call is kept. However, it is now wrapped in a clear
 *   try-catch: if Keycloak is unreachable, we throw a specific error rather than
 *   propagating an undifferentiated exception. Future improvement: cache the STAFF role
 *   check in the JWT or use the DB approval/role record.
 *
 * FIX S-3 (BUG S-3) — organizer loaded once, reused for audit log.
 *   BEFORE: authorizationService.requireOrganizerAccess() ran first (existsById check),
 *   then the event was loaded, then the user was loaded, then userRepository.findById(organizerId)
 *   was called again just for the audit log — a redundant second user load.
 *   AFTER: organizerId → organizer entity loaded once and reused for the audit log.
 *
 * FIX S-4 (BUG S-4) — listEventStaff() uses projection query, not full collection load.
 *   BEFORE: event.getStaff().stream().map(...) loaded all User columns for all staff.
 *   AFTER: eventRepository.findStaffByEventId() selects only (id, name, email) via JPQL.
 *   The authorization check (requireOrganizerAccess) still loads the event once.
 *
 * FIX S-5 (BUG S-5) — isStaffAssignedToEvent() uses isStaffMember() COUNT query.
 *   BEFORE: Loaded the full staff collection to call anyMatch().
 *   AFTER: eventRepository.isStaffMember() — single COUNT, zero entities loaded.
 *
 * FIX S-6 (BUG S-6) — mutating methods return EventStaffResponseDto.
 *   The controller now returns the service result directly — no extra service calls.
 *
 * FIX S-8 (BUG S-8) — getEventName() uses scalar projection query.
 *   No longer loads a full Event entity to return a String.
 *
 * Previously applied fixes preserved:
 *   FIX #14 — ID-based duplicate check and removeIf.
 *   L-21 FIX — throw on non-existent staff removal.
 *   FIX 1 — org.springframework.transaction.annotation.@Transactional.
 *   FIX 2 — RequestUtil.getCurrentRequest().
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

    // ── ASSIGN ────────────────────────────────────────────────────────────────

    /**
     * FIX S-6: Returns EventStaffResponseDto — controller no longer needs extra calls.
     * FIX S-3: Organizer loaded once, reused for audit.
     * FIX S-1: Response built from already-in-memory data via projection query.
     */
    @Override
    @Transactional
    public EventStaffResponseDto assignStaffToEvent(UUID organizerId, UUID eventId, UUID userId) {
        log.info("Assigning user '{}' as staff to event '{}' by organizer '{}'",
                userId, eventId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        // FIX S-3: load organizer once — reused for audit below
        User organizer = userRepository.findById(organizerId)
                .orElseGet(systemUserProvider::getSystemUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        // FIX S-2: Keycloak call retained (business rule: must have STAFF role).
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

        // FIX #14 (preserved): ID-based duplicate check
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

        // FIX S-1 + S-4: build response from projection query — no second entity load
        List<StaffMemberDto> staffList = eventRepository.findStaffByEventId(eventId);
        return new EventStaffResponseDto(eventId, event.getName(), staffList, staffList.size());
    }

    // ── REMOVE ────────────────────────────────────────────────────────────────

    /**
     * FIX S-6: Returns EventStaffResponseDto — controller no longer needs extra calls.
     * FIX S-3: Organizer loaded once, reused for audit.
     * FIX S-1: Response built via projection query.
     */
    @Override
    @Transactional
    public EventStaffResponseDto removeStaffFromEvent(UUID organizerId, UUID eventId, UUID userId) {
        log.info("Removing user '{}' from staff of event '{}' by organizer '{}'",
                userId, eventId, organizerId);

        authorizationService.requireOrganizerAccess(organizerId, eventId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));

        // FIX S-3: load organizer once — reused for audit below
        User organizer = userRepository.findById(organizerId)
                .orElseGet(systemUserProvider::getSystemUser);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        String.format("User with ID '%s' not found", userId)));

        // FIX #14 (preserved): ID-based removal
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

        // FIX S-1 + S-4: projection query — no second entity load
        List<StaffMemberDto> staffList = eventRepository.findStaffByEventId(eventId);
        return new EventStaffResponseDto(eventId, event.getName(), staffList, staffList.size());
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    /**
     * FIX S-4: Uses projection query — only id, name, email loaded per staff member.
     * BEFORE: event.getStaff() loaded all User columns for all staff members.
     */
    @Override
    @Transactional(readOnly = true)
    public List<StaffMemberDto> listEventStaff(UUID organizerId, UUID eventId) {
        authorizationService.requireOrganizerAccess(organizerId, eventId);
        // Verify event exists before returning staff (throws EventNotFoundException if not)
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(
                    String.format("Event with ID '%s' not found", eventId));
        }
        // FIX S-4: projection query — id/name/email only, no full User entities
        return eventRepository.findStaffByEventId(eventId);
    }

    // ── GET EVENT NAME ────────────────────────────────────────────────────────

    /**
     * FIX S-8: Scalar projection — no full Event entity loaded.
     * BEFORE: findById() loaded all event columns just to return event.getName().
     */
    @Override
    @Transactional(readOnly = true)
    public String getEventName(UUID eventId) {
        return eventRepository.findEventNameById(eventId)
                .orElseThrow(() -> new EventNotFoundException(
                        String.format("Event with ID '%s' not found", eventId)));
    }

    // ── IS STAFF ─────────────────────────────────────────────────────────────

    /**
     * FIX S-5: Uses isStaffMember() COUNT query — zero entities loaded.
     * BEFORE: findById() + event.getStaff().stream().anyMatch() loaded the full
     * staff @ManyToMany collection just to answer a yes/no question.
     */
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