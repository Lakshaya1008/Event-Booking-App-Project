package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.dtos.CreateEventRequestDto;
import com.event.tickets.domain.dtos.CreateEventResponseDto;
import com.event.tickets.domain.dtos.GetEventDetailsResponseDto;
import com.event.tickets.domain.dtos.ListEventResponseDto;
import com.event.tickets.domain.dtos.UpdateEventRequestDto;
import com.event.tickets.domain.dtos.UpdateEventResponseDto;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.mappers.EventMapper;
import com.event.tickets.services.EventService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/events")
@RequiredArgsConstructor
public class EventController {

  private final EventMapper eventMapper;
  private final EventService eventService;

  @PostMapping
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<CreateEventResponseDto> createEvent(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateEventRequestDto createEventRequestDto) {
    CreateEventRequest createEventRequest = eventMapper.fromDto(createEventRequestDto);
    UUID userId = parseUserId(jwt);

    Event createdEvent = eventService.createEvent(userId, createEventRequest);
    CreateEventResponseDto createEventResponseDto = eventMapper.toDto(createdEvent);
    return new ResponseEntity<>(createEventResponseDto, HttpStatus.CREATED);
  }

  @PutMapping(path = "/{eventId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<UpdateEventResponseDto> updateEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId,
      @Valid @RequestBody UpdateEventRequestDto updateEventRequestDto) {
    UpdateEventRequest updateEventRequest = eventMapper.fromDto(updateEventRequestDto);
    UUID userId = parseUserId(jwt);

    Event updatedEvent = eventService.updateEventForOrganizer(
        userId, eventId, updateEventRequest
    );

    UpdateEventResponseDto updateEventResponseDto = eventMapper.toUpdateEventResponseDto(
        updatedEvent);

    return ResponseEntity.ok(updateEventResponseDto);
  }

  @GetMapping
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<Page<ListEventResponseDto>> listEvents(
      @AuthenticationPrincipal Jwt jwt, Pageable pageable
  ) {
    UUID userId = parseUserId(jwt);
    Page<Event> events = eventService.listEventsForOrganizer(userId, pageable);
    return ResponseEntity.ok(
        events.map(eventMapper::toListEventResponseDto)
    );
  }

  @GetMapping(path = "/{eventId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<GetEventDetailsResponseDto> getEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId
  ) {
    UUID userId = parseUserId(jwt);
    return eventService.getEventForOrganizer(userId, eventId)
        .map(eventMapper::toGetEventDetailsResponseDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @DeleteMapping(path = "/{eventId}")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<Void> deleteEvent(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId
  ) {
    UUID userId = parseUserId(jwt);
    eventService.deleteEventForOrganizer(userId, eventId);
    return ResponseEntity.noContent().build();
  }

  // Sales dashboard endpoints
  @GetMapping("/{eventId}/sales-dashboard")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<Map<String, Object>> getSalesDashboard(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId) {
    UUID organizerId = parseUserId(jwt);
    Map<String, Object> dashboard = eventService.getSalesDashboard(organizerId, eventId);
    return ResponseEntity.ok(dashboard);
  }

  @GetMapping("/{eventId}/attendees-report")
  @PreAuthorize("hasRole('ORGANIZER')")
  public ResponseEntity<Map<String, Object>> getAttendeesReport(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID eventId) {
    UUID organizerId = parseUserId(jwt);
    Map<String, Object> report = eventService.getAttendeesReport(organizerId, eventId);
    return ResponseEntity.ok(report);
  }
}
