package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.AssignStaffRequestDto;
import com.event.tickets.domain.dtos.EventStaffResponseDto;
import com.event.tickets.domain.dtos.StaffMemberDto;
import com.event.tickets.services.EventStaffService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event Staff Management Controller
 *
 * Provides ORGANIZER-only endpoints for managing event-scoped staff assignments.
 *
 * All data access goes through EventStaffService — no direct repository calls here.
 * Event name is fetched via eventStaffService.getEventName() to avoid the redundant
 * eventRepository.findById() that previously existed after every staff operation.
 *
 * Endpoints:
 * - POST   /api/v1/events/{eventId}/staff           - Assign staff to event
 * - DELETE /api/v1/events/{eventId}/staff/{userId}  - Remove staff from event
 * - GET    /api/v1/events/{eventId}/staff           - List event staff
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/staff")
@RequiredArgsConstructor
@Slf4j
public class EventStaffController {

    private final EventStaffService eventStaffService;

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventStaffResponseDto> assignStaffToEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @Valid @RequestBody AssignStaffRequestDto request
    ) {
        UUID organizerId = parseUserId(jwt);
        UUID userId = request.getUserId();
        log.info("Organizer '{}' assigning staff '{}' to event '{}'", organizerId, userId, eventId);

        eventStaffService.assignStaffToEvent(organizerId, eventId, userId);

        List<StaffMemberDto> staffList = eventStaffService.listEventStaff(organizerId, eventId);
        String eventName = eventStaffService.getEventName(eventId);

        log.info("Successfully assigned staff '{}' to event '{}'", userId, eventId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new EventStaffResponseDto(eventId, eventName, staffList, staffList.size()));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventStaffResponseDto> removeStaffFromEvent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            @PathVariable UUID userId
    ) {
        UUID organizerId = parseUserId(jwt);
        log.info("Organizer '{}' removing staff '{}' from event '{}'", organizerId, userId, eventId);

        eventStaffService.removeStaffFromEvent(organizerId, eventId, userId);

        List<StaffMemberDto> staffList = eventStaffService.listEventStaff(organizerId, eventId);
        String eventName = eventStaffService.getEventName(eventId);

        log.info("Successfully removed staff '{}' from event '{}'", userId, eventId);
        return ResponseEntity.ok(new EventStaffResponseDto(eventId, eventName, staffList, staffList.size()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventStaffResponseDto> listEventStaff(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId
    ) {
        UUID organizerId = parseUserId(jwt);
        log.debug("Organizer '{}' listing staff for event '{}'", organizerId, eventId);

        List<StaffMemberDto> staffList = eventStaffService.listEventStaff(organizerId, eventId);
        String eventName = eventStaffService.getEventName(eventId);

        return ResponseEntity.ok(new EventStaffResponseDto(eventId, eventName, staffList, staffList.size()));
    }
}
