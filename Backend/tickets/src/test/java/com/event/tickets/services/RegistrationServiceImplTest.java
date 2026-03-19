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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CHANGES FROM PREVIOUS VERSION:
 *
 * FIX 1 — Added @Mock AuditLogService auditLogService.
 *   RegistrationServiceImpl calls auditLogService.saveAuditLog() inside emitAuditEvent().
 *   Without this mock, Mockito @InjectMocks leaves it null. The try/catch around the
 *   audit call was swallowing the NPE silently — tests passed but audit coverage was broken.
 *
 * FIX 2 — Added DataIntegrityViolation race condition test.
 *   NEW: registersWithDuplicateEmailRaceCondition_throwsEmailAlreadyInUse
 *   Tests that when userRepository.save() throws DataIntegrityViolationException
 *   (concurrent same-email registration), Keycloak is rolled back and
 *   EmailAlreadyInUseException is returned — not a 500.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RegistrationServiceImpl")
class RegistrationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;  // FIX 1: was missing — silent NPE

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

    // ── Happy paths ───────────────────────────────────────────────────────────

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

        @Test
        @DisplayName("audit REGISTRATION_SUCCESS is emitted on success")
        void auditSuccessEmitted() {
            service.register(request);
            // auditLogService.saveAuditLog() called at least twice (ATTEMPT + SUCCESS)
            verify(auditLogService, atLeast(2)).saveAuditLog(any());
        }
    }

    // ── STAFF event assignment ────────────────────────────────────────────────

    @Nested
    @DisplayName("STAFF event assignment (FIX #2)")
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
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(staffEvent));
            when(eventRepository.save(any())).thenReturn(staffEvent);
            doNothing().when(emailService).sendRegistrationEmail(any(), any());

            service.register(request);

            // event.staff must contain the new user
            verify(eventRepository).save(argThat(e ->
                    !e.getStaff().isEmpty() &&
                            e.getStaff().stream().anyMatch(u -> u.getId().equals(keycloakUserId))));
        }
    }

    // ── Rollback safety ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Keycloak rollback safety (FIX #12)")
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

        @Test
        @DisplayName("FIX 2 — DataIntegrityViolation on DB save: Keycloak rolled back, EmailAlreadyInUseException thrown")
        void dataIntegrityViolation_keycloakRolledBack_emailExceptionThrown() {
            // Setup: both existsByEmail and Keycloak checks pass (race condition window)
            when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(request.getEmail())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            // DB unique constraint fires — concurrent registration won the race
            when(userRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("unique constraint: users_email_key"));

            // Must throw EmailAlreadyInUseException — not a generic 500
            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(EmailAlreadyInUseException.class)
                    .hasMessageContaining("already in use");

            // Keycloak user must be rolled back
            verify(keycloakAdminService).deleteUser(keycloakUserId);

            // deleteUser must be called exactly ONCE (not twice via outer catch)
            verify(keycloakAdminService, times(1)).deleteUser(keycloakUserId);
        }
    }

    // ── Email normalization ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Email normalization (FIX #1-2)")
    class EmailNormalization {

        @BeforeEach
        void mockSuccess() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(any())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any()))
                    .thenAnswer(inv -> UUID.randomUUID());
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            doNothing().when(emailService).sendRegistrationEmail(any(), any());
        }

        @Test
        @DisplayName("uppercase email is normalized to lowercase")
        void emailNormalized_toUpperCase() {
            request.setEmail("John@Example.COM");
            service.register(request);
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(userRepository).existsByEmail(emailCaptor.capture());
            assertThat(emailCaptor.getValue()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("email with whitespace is trimmed and lowercased")
        void emailTrimmed_andLowercased() {
            request.setEmail("  John@Example.COM  ");
            service.register(request);
            ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
            verify(userRepository).existsByEmail(emailCaptor.capture());
            assertThat(emailCaptor.getValue()).isEqualTo("john@example.com");
        }

        @Test
        @DisplayName("stored user email is normalized lowercase")
        void storedUserEmail_isNormalized() {
            request.setEmail("Test@Example.COM");
            service.register(request);
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("duplicate email detection is case-insensitive")
        void duplicateDetection_caseInsensitive() {
            request.setEmail("John@Example.COM");
            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);
            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(EmailAlreadyInUseException.class);
        }
    }
}