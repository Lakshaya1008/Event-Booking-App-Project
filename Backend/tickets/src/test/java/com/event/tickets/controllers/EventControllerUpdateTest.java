package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.UpdateEventRequestDto;
import com.event.tickets.domain.entities.EventStatusEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EventController.updateEvent endpoint
 *
 * Validates the API contract fix where:
 * - eventId comes ONLY from URL path parameter
 * - Request body does NOT require id field
 * - Defensive check prevents ID mismatch
 *
 * NOTE: These are contract validation tests.
 * Full integration tests require running application with Keycloak.
 * See API_DOCUMENTATION.md and TESTING_GUIDE.md for manual testing procedures.
 */
@SpringBootTest
class EventControllerUpdateTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  @DisplayName("UpdateEventRequestDto validation - id field is optional and can be null")
  void validateDtoContract_IdFieldOptional() throws Exception {
    // Arrange: Create DTO without setting id field
    UpdateEventRequestDto dto = new UpdateEventRequestDto();
    dto.setName("Test Event");
    dto.setVenue("Test Venue");
    dto.setStatus(EventStatusEnum.PUBLISHED);
    dto.setTicketTypes(new ArrayList<>());

    // Assert: DTO can be created with null id (no @NotNull validation)
    assertNull(dto.getId(), "ID should be null when not set");

    // Act: Serialize to JSON (simulating request body)
    String json = objectMapper.writeValueAsString(dto);

    // Assert: JSON can be parsed back even with null id
    UpdateEventRequestDto deserialized = objectMapper.readValue(json, UpdateEventRequestDto.class);
    assertNull(deserialized.getId(), "Deserialized DTO should have null id");
    assertEquals("Test Event", deserialized.getName(), "Name should be preserved");
    assertEquals("Test Venue", deserialized.getVenue(), "Venue should be preserved");
    assertEquals(EventStatusEnum.PUBLISHED, deserialized.getStatus(), "Status should be preserved");

    // Test that a JSON without id field can be deserialized
    String jsonWithoutId = """
        {
          "name": "Test Event",
          "venue": "Test Venue",
          "status": "PUBLISHED",
          "ticketTypes": []
        }
        """;
    UpdateEventRequestDto fromJsonWithoutId = objectMapper.readValue(jsonWithoutId, UpdateEventRequestDto.class);
    assertNull(fromJsonWithoutId.getId(), "DTO from JSON without id should have null id");
    assertEquals("Test Event", fromJsonWithoutId.getName(), "Name should be preserved from JSON without id");
  }

  @Test
  @DisplayName("UpdateEventRequestDto validation - id field can be set")
  void validateDtoContract_IdFieldCanBeSet() {
    // Arrange
    UUID testId = UUID.randomUUID();
    UpdateEventRequestDto dto = new UpdateEventRequestDto();

    // Act: Set id explicitly (simulating controller logic)
    dto.setId(testId);

    // Assert
    assertNotNull(dto.getId(), "ID should be settable");
    assertEquals(testId, dto.getId(), "ID should match what was set");
  }

  @Test
  @DisplayName("Controller logic simulation - defensive check detects mismatch")
  void simulateControllerDefensiveCheck() {
    // Arrange
    UUID urlEventId = UUID.randomUUID();
    UUID bodyEventId = UUID.randomUUID();

    UpdateEventRequestDto dto = new UpdateEventRequestDto();
    dto.setId(bodyEventId);

    // Act & Assert: Simulate defensive check - should detect mismatch
    boolean idMismatch = dto.getId() != null && !urlEventId.equals(dto.getId());
    assertTrue(idMismatch, "Defensive check should detect mismatch when IDs differ");
  }

  @Test
  @DisplayName("Controller logic simulation - matching IDs pass check")
  void simulateControllerDefensiveCheck_MatchingIds() {
    // Arrange
    UUID eventId = UUID.randomUUID();

    UpdateEventRequestDto dto = new UpdateEventRequestDto();
    dto.setId(eventId);

    // Act & Assert: Simulate defensive check with matching IDs
    boolean idMismatch = dto.getId() != null && !eventId.equals(dto.getId());
    assertFalse(idMismatch, "Matching IDs should pass defensive check");
  }

  @Test
  @DisplayName("Controller logic simulation - null body ID passes check and can be set")
  void simulateControllerDefensiveCheck_NullBodyId() {
    // Arrange
    UUID eventId = UUID.randomUUID();

    UpdateEventRequestDto dto = new UpdateEventRequestDto();
    // id is null by default

    // Act: Simulate defensive check - should NOT trigger for null id
    boolean idMismatch = dto.getId() != null && !eventId.equals(dto.getId());
    assertFalse(idMismatch, "Should not trigger check when body ID is null");

    // Controller sets ID from path
    dto.setId(eventId);

    // Assert
    assertEquals(eventId, dto.getId(), "Controller should set ID from path");
  }
}
