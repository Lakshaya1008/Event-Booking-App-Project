package com.event.tickets.services;

import com.event.tickets.domain.dtos.StaffMemberDto;
import java.util.List;
import java.util.UUID;

/**
 * Event Staff Management Service
 *
 * Manages event-scoped staff assignments.
 *
 * Business Rules:
 * - Only event organizers can manage staff for their events
 * - STAFF role in Keycloak is prerequisite (assigned via ADMIN)
 * - Event-staff relationship persisted in database
 * - STAFF role alone provides no access without event assignment
 *
 * Authorization:
 * - Enforced via AuthorizationService (organizer ownership)
 * - No direct Keycloak calls from organizers
 * - Database is source of truth for event-staff assignments
 */
public interface EventStaffService {

    /**
     * Assigns a staff member to an event.
     *
     * Requirements:
     * - User must have STAFF role in Keycloak
     * - Organizer must own the event
     * - Creates entry in user_staffing_events table
     *
     * @param organizerId The ID of the organizer (for authorization)
     * @param eventId The ID of the event
     * @param userId The ID of the user to assign as staff
     */
    void assignStaffToEvent(UUID organizerId, UUID eventId, UUID userId);

    /**
     * Removes a staff member from an event.
     *
     * Requirements:
     * - Organizer must own the event
     * - Removes entry from user_staffing_events table
     *
     * @param organizerId The ID of the organizer (for authorization)
     * @param eventId The ID of the event
     * @param userId The ID of the staff member to remove
     */
    void removeStaffFromEvent(UUID organizerId, UUID eventId, UUID userId);

    /**
     * Lists all staff members assigned to an event.
     *
     * @param organizerId The ID of the organizer (for authorization)
     * @param eventId The ID of the event
     * @return List of staff members assigned to the event
     */
    List<StaffMemberDto> listEventStaff(UUID organizerId, UUID eventId);

    /**
     * Returns the name of an event.
     * Used by EventStaffController to build response DTOs without a redundant DB query —
     * the event was already loaded inside the service during the preceding operation.
     *
     * @param eventId The ID of the event
     * @return The event name
     * @throws com.event.tickets.exceptions.EventNotFoundException if event does not exist
     */
    String getEventName(UUID eventId);

    /**
     * Checks if a user is assigned as staff to an event.
     *
     * @param eventId The ID of the event
     * @param userId The ID of the user to check
     * @return true if user is assigned as staff, false otherwise
     */
    boolean isStaffAssignedToEvent(UUID eventId, UUID userId);
}
