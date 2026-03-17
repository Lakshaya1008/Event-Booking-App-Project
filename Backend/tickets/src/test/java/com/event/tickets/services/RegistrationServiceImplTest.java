package com.event.tickets.services;

import com.event.tickets.domain.dtos.RegisterRequestDto;
import com.event.tickets.domain.dtos.RegisterResponseDto;
import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.RegistrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationServiceImpl")
class RegistrationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private EmailService emailService;

    @InjectMocks
    private RegistrationServiceImpl service;

    private RegisterRequestDto request;
    private UUID keycloakUserId;

    @BeforeEach
    void setUp() {
        keycloakUserId = UUID.randomUUID();

        request = new RegisterRequestDto();
        request.setEmail("newuser@test.com");
        request.setPassword("Password1!");
        request.setName("New User");
        request.setInviteCode(null);

        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    // ── register — happy paths ────────────────────────────────────────────────

    @Nested
    @DisplayName("register — happy paths")
    class HappyPath {

        @BeforeEach
        void mockSuccess() {
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(request.getEmail())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());

            User savedUser = new User();
            savedUser.setId(keycloakUserId);
            savedUser.setEmail(request.getEmail());
            savedUser.setName(request.getName());
            savedUser.setApprovalStatus(ApprovalStatus.PENDING);
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
            doNothing().when(emailService).sendRegistrationEmail(any(), any());
        }

        @Test
        @DisplayName("registers ATTENDEE user successfully")
        void registersAttendeeSuccessfully() {
            RegisterResponseDto result = service.register(request);

            assertThat(result.getEmail()).isEqualTo("newuser@test.com");
            assertThat(result.getAssignedRole()).isEqualTo("ATTENDEE");
            assertThat(result.isRequiresApproval()).isTrue();
        }

        @Test
        @DisplayName("sends registration email after successful registration")
        void sendsRegistrationEmail() {
            service.register(request);
            verify(emailService).sendRegistrationEmail("newuser@test.com", "New User");
        }

        @Test
        @DisplayName("user is created with PENDING approval status")
        void userCreatedAsPending() {
            service.register(request);

            verify(userRepository).save(argThat(u ->
                    u.getApprovalStatus() == ApprovalStatus.PENDING));
        }
    }

    // ── FIX #2 — STAFF event assignment ──────────────────────────────────────

    @Nested
    @DisplayName("FIX #2 — STAFF event assignment actually executes")
    class StaffAssignment {

        @Test
        @DisplayName("adds STAFF user to event.staff list via invite code")
        void assignsStaffToEvent() {
            UUID eventId = UUID.randomUUID();

            InviteCode inviteCode = new InviteCode();
            inviteCode.setId(UUID.randomUUID());
            inviteCode.setCode("STAFF-CODE");
            inviteCode.setRoleName("STAFF");
            inviteCode.setStatus(InviteCodeStatus.PENDING);
            inviteCode.setExpiresAt(LocalDateTime.now().plusHours(24));

            Event staffEvent = new Event();
            staffEvent.setId(eventId);
            staffEvent.setName("Tech Conf");
            staffEvent.setStaff(new ArrayList<>());
            inviteCode.setEvent(staffEvent);

            request.setInviteCode("STAFF-CODE");

            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(inviteCodeRepository.findByCode("STAFF-CODE")).thenReturn(Optional.of(inviteCode));
            when(keycloakAdminService.getUserIdByEmail(request.getEmail())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());

            User savedUser = new User();
            savedUser.setId(keycloakUserId);
            savedUser.setEmail(request.getEmail());
            savedUser.setName(request.getName());
            savedUser.setApprovalStatus(ApprovalStatus.PENDING);
            when(userRepository.save(any())).thenReturn(savedUser);
            when(userRepository.saveAndFlush(any())).thenReturn(savedUser);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(staffEvent));
            when(eventRepository.save(any())).thenReturn(staffEvent);
            doNothing().when(emailService).sendRegistrationEmail(any(), any());

            service.register(request);

            // FIX #2: event.staff must actually contain the new user
            verify(eventRepository).save(argThat(e ->
                    !e.getStaff().isEmpty() &&
                    e.getStaff().stream().anyMatch(u -> u.getId().equals(keycloakUserId))));
        }
    }

    // ── FIX #12 — Keycloak rollback safety ───────────────────────────────────

    @Nested
    @DisplayName("FIX #12 — no double rollback of Keycloak user")
    class RollbackSafety {

        @Test
        @DisplayName("rolls back Keycloak when DB save fails")
        void rollsBackKeycloakOnDbFailure() {
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(request.getEmail())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            when(userRepository.save(any())).thenThrow(new RuntimeException("DB down"));

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(RegistrationException.class);

            // Keycloak user must be deleted
            verify(keycloakAdminService).deleteUser(keycloakUserId);
        }

        @Test
        @DisplayName("FIX #12 — Keycloak deleteUser called exactly ONCE when role assignment fails")
        void keycloakDeleteCalledOnceOnRoleFailure() {
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(request.getEmail())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doThrow(new RuntimeException("role error"))
                    .when(keycloakAdminService).assignRoleToUser(any(), any());

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(RegistrationException.class);

            // FIX #12: exactly ONE deleteUser call — not two (no double rollback)
            verify(keycloakAdminService, times(1)).deleteUser(keycloakUserId);
        }

        @Test
        @DisplayName("throws EmailAlreadyInUseException without touching Keycloak")
        void throwsEarlyWhenEmailTaken() {
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(EmailAlreadyInUseException.class);

            verify(keycloakAdminService, never()).createUser(any(), any(), any());
            verify(keycloakAdminService, never()).deleteUser(any());
        }
    }
}

