package com.event.tickets.services;

import com.event.tickets.domain.dtos.EventStaffResponseDto;
import com.event.tickets.domain.dtos.StaffMemberDto;
import java.util.List;
import java.util.UUID;

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

    String getEventName(UUID eventId);

    /**
     * Checks if a user is assigned as staff to an event.
     * Uses an EXISTS query — no collection loaded.
     */
    boolean isStaffAssignedToEvent(UUID eventId, UUID userId);
}