package com.event.tickets.services;

import com.event.tickets.domain.dtos.EventStaffResponseDto;
import com.event.tickets.domain.dtos.StaffMemberDto;
import java.util.List;
import java.util.UUID;

/**
 * Event Staff Management Service
 *
 * FIX S-6: assignStaffToEvent() and removeStaffFromEvent() now return EventStaffResponseDto
 * instead of void.
 *
 * BEFORE: Both mutating methods returned void. The controller called them, then made
 * two additional service calls to build the response:
 *   1. eventStaffService.listEventStaff(organizerId, eventId)  — reloads event + staff
 *   2. eventStaffService.getEventName(eventId)                 — reloads event again
 * That was 3 extra DB queries per assign/remove request, on top of the mutations themselves.
 *
 * AFTER: The service builds and returns the complete EventStaffResponseDto from within
 * the same transaction that performed the mutation. The event and staff data are already
 * in memory — no extra DB round-trips needed. The controller just returns what it receives.
 *
 * getEventName() is kept on the interface for any other callers, but is no longer
 * called by EventStaffController after assign/remove.
 */
public interface EventStaffService {

    /**
     * Assigns a staff member to an event and returns the updated staff list.
     *
     * Requirements:
     * - User must have STAFF role in Keycloak
     * - Organizer must own the event
     *
     * @return Complete EventStaffResponseDto with updated staff list — no extra queries needed.
     */
    EventStaffResponseDto assignStaffToEvent(UUID organizerId, UUID eventId, UUID userId);

    /**
     * Removes a staff member from an event and returns the updated staff list.
     *
     * @return Complete EventStaffResponseDto with updated staff list — no extra queries needed.
     */
    EventStaffResponseDto removeStaffFromEvent(UUID organizerId, UUID eventId, UUID userId);

    /**
     * Lists all staff members for an event as a flat DTO list.
     * Uses a projection query — only id, name, email loaded per staff member.
     */
    List<StaffMemberDto> listEventStaff(UUID organizerId, UUID eventId);

    /**
     * Returns the event name. Retained for external callers.
     * EventStaffController no longer calls this separately after mutations.
     */
    String getEventName(UUID eventId);

    /**
     * Checks if a user is assigned as staff to an event.
     * Uses an EXISTS query — no collection loaded.
     */
    boolean isStaffAssignedToEvent(UUID eventId, UUID userId);
}