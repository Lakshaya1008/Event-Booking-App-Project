package com.event.tickets.services;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.entities.Event;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface EventService {

  Event createEvent(UUID organizerId, CreateEventRequest event);

  Page<Event> listEventsForOrganizer(UUID organizerId, Pageable pageable);

  Optional<Event> getEventForOrganizer(UUID organizerId, UUID id);

  Event updateEventForOrganizer(UUID organizerId, UUID id, UpdateEventRequest event);

  void deleteEventForOrganizer(UUID organizerId, UUID id);

  Page<Event> listPublishedEvents(Pageable pageable);

  Page<Event> searchPublishedEvents(String query, Pageable pageable);

  Optional<Event> getPublishedEvent(UUID id);

  // Sales dashboard operations
  Map<String, Object> getSalesDashboard(UUID organizerId, UUID eventId);
  Map<String, Object> getAttendeesReport(UUID organizerId, UUID eventId);
}
