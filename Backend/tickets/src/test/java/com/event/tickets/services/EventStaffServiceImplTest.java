package com.event.tickets.services;

import com.event.tickets.domain.dtos.EventStaffResponseDto;
import com.event.tickets.domain.dtos.StaffMemberDto;
import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.EventStaffServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EventStaffServiceImplTest
 *
 * This file did not previously exist — BUG S-7: zero test coverage on all 5 service methods.
 *
 * Covers:
 *   - assignStaffToEvent: happy path, duplicate guard, user not found, no STAFF role,
 *     returns updated EventStaffResponseDto (FIX S-6)
 *   - removeStaffFromEvent: happy path, user not assigned throws, returns updated DTO
 *   - listEventStaff: uses findStaffByEventId projection (FIX S-4)
 *   - isStaffAssignedToEvent: delegates to isStaffMember COUNT query (FIX S-5)
 *   - getEventName: uses findEventNameById scalar projection (FIX S-8)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EventStaffServiceImpl")
class EventStaffServiceImplTest {

    @Mock private EventRepository eventRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EventStaffServiceImpl service;

    private UUID organizerId;
    private UUID eventId;
    private UUID staffUserId;
    private User organizer;
    private User staffUser;
    private Event event;

    @BeforeEach
    void setUp() {
        organizerId  = UUID.randomUUID();
        eventId      = UUID.randomUUID();
        staffUserId  = UUID.randomUUID();

        organizer = new User();
        organizer.setId(organizerId);
        organizer.setName("Carol Organizer");
        organizer.setEmail("carol@test.com");

        staffUser = new User();
        staffUser.setId(staffUserId);
        staffUser.setName("Dave Staff");
        staffUser.setEmail("dave@test.com");

        event = new Event();
        event.setId(eventId);
        event.setName("Tech Conference");
        event.setOrganizer(organizer);
        event.setStaff(new ArrayList<>());

        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    // ── assignStaffToEvent ────────────────────────────────────────────────────

    @Nested
    @DisplayName("assignStaffToEvent")
    class AssignStaffToEvent {

        @BeforeEach
        void mockHappyPath() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));
            when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
            when(keycloakAdminService.userHasRole(staffUserId, "STAFF")).thenReturn(true);
            when(eventRepository.save(any())).thenReturn(event);
            when(eventRepository.findStaffByEventId(eventId))
                    .thenReturn(List.of(new StaffMemberDto(staffUserId, "Dave Staff", "dave@test.com")));
        }

        @Test
        @DisplayName("FIX S-6 — returns EventStaffResponseDto with updated staff list")
        void returnsUpdatedDto() {
            EventStaffResponseDto result = service.assignStaffToEvent(organizerId, eventId, staffUserId);

            assertThat(result).isNotNull();
            assertThat(result.getEventId()).isEqualTo(eventId);
            assertThat(result.getEventName()).isEqualTo("Tech Conference");
            assertThat(result.getStaffMembers()).hasSize(1);
            assertThat(result.getStaffMembers().get(0).getUserName()).isEqualTo("Dave Staff");
            assertThat(result.getTotalStaffCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("FIX S-4 — uses findStaffByEventId projection query, not entity collection")
        void usesProjectionQueryForStaffList() {
            service.assignStaffToEvent(organizerId, eventId, staffUserId);

            // Projection query must be called — not getStaff() on the entity (untestable directly,
            // but verifying findStaffByEventId was called confirms the correct path)
            verify(eventRepository).findStaffByEventId(eventId);
        }

        @Test
        @DisplayName("throws InvalidBusinessStateException when user already assigned")
        void throwsWhenAlreadyAssigned() {
            event.getStaff().add(staffUser); // pre-populate

            assertThatThrownBy(() -> service.assignStaffToEvent(organizerId, eventId, staffUserId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("already assigned");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidBusinessStateException when user does not have STAFF role")
        void throwsWhenNoStaffRole() {
            when(keycloakAdminService.userHasRole(staffUserId, "STAFF")).thenReturn(false);

            assertThatThrownBy(() -> service.assignStaffToEvent(organizerId, eventId, staffUserId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("STAFF role");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidBusinessStateException when Keycloak is unreachable")
        void throwsWhenKeycloakUnreachable() {
            when(keycloakAdminService.userHasRole(staffUserId, "STAFF"))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> service.assignStaffToEvent(organizerId, eventId, staffUserId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Keycloak is temporarily unavailable");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws UserNotFoundException when staff user does not exist")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(staffUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.assignStaffToEvent(organizerId, eventId, staffUserId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("emits STAFF_ASSIGNED audit log on success")
        void emitsAuditLog() {
            service.assignStaffToEvent(organizerId, eventId, staffUserId);

            verify(auditLogService).saveAuditLog(argThat(log ->
                    log.getAction() == AuditAction.STAFF_ASSIGNED));
        }

        @Test
        @DisplayName("FIX S-3 — organizer loaded only once (not twice)")
        void organizerLoadedOnce() {
            service.assignStaffToEvent(organizerId, eventId, staffUserId);

            // organizerId is used for findById (for organizer load) — should be exactly once
            verify(userRepository, times(1)).findById(organizerId);
        }
    }

    // ── removeStaffFromEvent ──────────────────────────────────────────────────

    @Nested
    @DisplayName("removeStaffFromEvent")
    class RemoveStaffFromEvent {

        @BeforeEach
        void mockHappyPath() {
            event.getStaff().add(staffUser); // user is assigned
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));
            when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
            when(eventRepository.save(any())).thenReturn(event);
            when(eventRepository.findStaffByEventId(eventId)).thenReturn(List.of());
        }

        @Test
        @DisplayName("FIX S-6 — returns EventStaffResponseDto after removal")
        void returnsUpdatedDtoAfterRemoval() {
            EventStaffResponseDto result = service.removeStaffFromEvent(organizerId, eventId, staffUserId);

            assertThat(result).isNotNull();
            assertThat(result.getEventId()).isEqualTo(eventId);
            assertThat(result.getStaffMembers()).isEmpty();
            assertThat(result.getTotalStaffCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("L-21 FIX — throws when user not in staff list")
        void throwsWhenUserNotAssigned() {
            event.getStaff().clear(); // user is NOT assigned

            assertThatThrownBy(() -> service.removeStaffFromEvent(organizerId, eventId, staffUserId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("not assigned as staff");

            verify(eventRepository, never()).save(any());
        }

        @Test
        @DisplayName("event is saved after successful removal")
        void eventSavedAfterRemoval() {
            service.removeStaffFromEvent(organizerId, eventId, staffUserId);

            verify(eventRepository).save(argThat(e ->
                    e.getStaff().stream().noneMatch(s -> s.getId().equals(staffUserId))));
        }

        @Test
        @DisplayName("emits STAFF_REMOVED audit log on success")
        void emitsAuditLog() {
            service.removeStaffFromEvent(organizerId, eventId, staffUserId);

            verify(auditLogService).saveAuditLog(argThat(log ->
                    log.getAction() == AuditAction.STAFF_REMOVED));
        }
    }

    // ── listEventStaff ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listEventStaff — FIX S-4: projection query")
    class ListEventStaff {

        @Test
        @DisplayName("FIX S-4 — delegates to findStaffByEventId projection, not entity collection")
        void usesProjectionQuery() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.existsById(eventId)).thenReturn(true);
            when(eventRepository.findStaffByEventId(eventId))
                    .thenReturn(List.of(
                            new StaffMemberDto(staffUserId, "Dave Staff", "dave@test.com")));

            List<StaffMemberDto> result = service.listEventStaff(organizerId, eventId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserName()).isEqualTo("Dave Staff");
            // Verify the projection query was used
            verify(eventRepository).findStaffByEventId(eventId);
        }

        @Test
        @DisplayName("throws EventNotFoundException when event does not exist")
        void throwsWhenEventNotFound() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.existsById(eventId)).thenReturn(false);

            assertThatThrownBy(() -> service.listEventStaff(organizerId, eventId))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }

    // ── isStaffAssignedToEvent ────────────────────────────────────────────────

    @Nested
    @DisplayName("isStaffAssignedToEvent — FIX S-5: COUNT query")
    class IsStaffAssignedToEvent {

        @Test
        @DisplayName("FIX S-5 — delegates to isStaffMember COUNT query, returns true when assigned")
        void returnsTrueWhenAssigned() {
            when(eventRepository.isStaffMember(eventId, staffUserId)).thenReturn(true);

            assertThat(service.isStaffAssignedToEvent(eventId, staffUserId)).isTrue();
            verify(eventRepository).isStaffMember(eventId, staffUserId);
        }

        @Test
        @DisplayName("returns false when not assigned")
        void returnsFalseWhenNotAssigned() {
            when(eventRepository.isStaffMember(eventId, staffUserId)).thenReturn(false);

            assertThat(service.isStaffAssignedToEvent(eventId, staffUserId)).isFalse();
        }

        @Test
        @DisplayName("FIX S-5 — full staff collection is NEVER loaded for this check")
        void staffCollectionNeverLoaded() {
            when(eventRepository.isStaffMember(eventId, staffUserId)).thenReturn(true);

            service.isStaffAssignedToEvent(eventId, staffUserId);

            // findById must NOT be called — no entity loaded at all
            verify(eventRepository, never()).findById(any());
        }
    }

    // ── getEventName ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEventName — FIX S-8: scalar projection")
    class GetEventName {

        @Test
        @DisplayName("FIX S-8 — uses findEventNameById scalar query, returns event name")
        void returnsEventName() {
            when(eventRepository.findEventNameById(eventId))
                    .thenReturn(Optional.of("Tech Conference"));

            String result = service.getEventName(eventId);

            assertThat(result).isEqualTo("Tech Conference");
            verify(eventRepository).findEventNameById(eventId);
            // Full entity load must NOT happen
            verify(eventRepository, never()).findById(any());
        }

        @Test
        @DisplayName("throws EventNotFoundException when event does not exist")
        void throwsWhenNotFound() {
            when(eventRepository.findEventNameById(eventId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getEventName(eventId))
                    .isInstanceOf(EventNotFoundException.class);
        }
    }
}