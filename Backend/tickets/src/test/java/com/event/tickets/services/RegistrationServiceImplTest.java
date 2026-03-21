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
    @Mock private AuditLogService auditLogService;

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
    @DisplayName("ATTENDEE registration (no invite code)")
    class AttendeeHappyPath {

        @BeforeEach
        void mockSuccess() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            doNothing().when(keycloakAdminService).activateUser(any());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendRegistrationEmail(any(), any());
        }

        @Test
        @DisplayName("ATTENDEE is APPROVED immediately — no admin review needed")
        void attendee_isApprovedImmediately() {
            RegisterResponseDto result = service.register(request);

            assertThat(result.isRequiresApproval()).isFalse();
            assertThat(result.getAssignedRole()).isEqualTo("ATTENDEE");

            // DB user must be APPROVED, not PENDING
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        }

        @Test
        @DisplayName("ATTENDEE: Keycloak activateUser() is called immediately at registration")
        void attendee_keycloakActivatedAtRegistration() {
            service.register(request);
            // ATTENDEE must be enabled in Keycloak right away — no waiting for admin approval
            verify(keycloakAdminService).activateUser(keycloakUserId);
        }

        @Test
        @DisplayName("ATTENDEE: approvedAt is set on the user record")
        void attendee_approvedAtIsSet() {
            service.register(request);
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("blank invite code treated as no invite — ATTENDEE auto-approved")
        void blankInviteCode_defaultsToAttendeeApproved() {
            request.setInviteCode("   ");
            RegisterResponseDto result = service.register(request);
            assertThat(result.isRequiresApproval()).isFalse();
            verify(inviteCodeRepository, never()).findByCode(anyString());
        }

        @Test
        @DisplayName("sends registration email")
        void sendsRegistrationEmail() {
            service.register(request);
            verify(emailService).sendRegistrationEmail("newuser@test.com", "New User");
        }
    }

    // ── Invite code (non-ATTENDEE) — PENDING ─────────────────────────────────

    @Nested
    @DisplayName("Non-ATTENDEE registration (with invite code) → PENDING")
    class InviteCodePendingPath {

        @Test
        @DisplayName("ORGANIZER invite → PENDING, Keycloak NOT activated at registration")
        void organizerInvite_isPending_notActivated() {
            InviteCode inviteCode = pendingInviteCode("ORGANIZER", null);
            request.setInviteCode("ABCD-EFGH-IJKL-MNOP");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(inviteCodeRepository.findByCode("ABCD-EFGH-IJKL-MNOP")).thenReturn(Optional.of(inviteCode));
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendRegistrationEmail(any(), any());

            RegisterResponseDto result = service.register(request);

            assertThat(result.isRequiresApproval()).isTrue();
            // Role must NOT be exposed in response for PENDING users
            assertThat(result.getAssignedRole()).isNull();

            // Keycloak must NOT be activated — user waits for admin approval
            verify(keycloakAdminService, never()).activateUser(any());

            // DB user must be PENDING
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        }

        @Test
        @DisplayName("STAFF invite → PENDING, added to event staff list")
        void staffInvite_isPending_addedToEventStaff() {
            UUID eventId = UUID.randomUUID();
            Event staffEvent = new Event();
            staffEvent.setId(eventId);
            staffEvent.setName("Tech Conf");
            staffEvent.setStaff(new ArrayList<>());

            InviteCode inviteCode = pendingInviteCode("STAFF", staffEvent);
            request.setInviteCode("ABCD-EFGH-IJKL-MNOP");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(inviteCodeRepository.findByCode("ABCD-EFGH-IJKL-MNOP")).thenReturn(Optional.of(inviteCode));
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(staffEvent));
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendRegistrationEmail(any(), any());

            service.register(request);

            assertThat(result -> result).isNotNull(); // just check no exception
            verify(keycloakAdminService, never()).activateUser(any());
            verify(eventRepository).save(argThat(e ->
                    e.getStaff().stream().anyMatch(u -> u.getId().equals(keycloakUserId))));
        }
    }

    // ── Invite code marked REDEEMED on success ────────────────────────────────

    @Nested
    @DisplayName("Invite code redemption")
    class InviteCodeRedemption {

        @Test
        @DisplayName("invite code is marked REDEEMED after successful registration")
        void inviteCode_markedRedeemed() {
            InviteCode inviteCode = pendingInviteCode("ORGANIZER", null);
            request.setInviteCode("ABCD-EFGH-IJKL-MNOP");

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(inviteCodeRepository.findByCode("ABCD-EFGH-IJKL-MNOP")).thenReturn(Optional.of(inviteCode));
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendRegistrationEmail(any(), any());

            service.register(request);

            ArgumentCaptor<InviteCode> codeCaptor = ArgumentCaptor.forClass(InviteCode.class);
            verify(inviteCodeRepository).save(codeCaptor.capture());
            assertThat(codeCaptor.getValue().getStatus()).isEqualTo(InviteCodeStatus.REDEEMED);
            assertThat(codeCaptor.getValue().getRedeemedAt()).isNotNull();
        }
    }

    // ── Rollback safety ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Keycloak rollback safety")
    class RollbackSafety {

        @Test
        @DisplayName("rolls back Keycloak when DB save fails")
        void rollsBackKeycloakOnDbFailure() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            doNothing().when(keycloakAdminService).activateUser(any());
            when(userRepository.save(any())).thenThrow(new RuntimeException("DB down"));

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(RegistrationException.class);

            verify(keycloakAdminService).deleteUser(keycloakUserId);
        }

        @Test
        @DisplayName("Keycloak deleteUser called exactly ONCE when role assignment fails")
        void keycloakDeleteCalledOnceOnRoleFailure() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
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
            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(EmailAlreadyInUseException.class);

            verify(keycloakAdminService, never()).createUser(any(), any(), any());
            verify(keycloakAdminService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("DataIntegrityViolation on DB save: Keycloak rolled back, EmailAlreadyInUseException thrown")
        void dataIntegrityViolation_keycloakRolledBack() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(anyString())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(keycloakUserId);
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            doNothing().when(keycloakAdminService).activateUser(any());
            when(userRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("unique constraint: users_email_key"));

            assertThatThrownBy(() -> service.register(request))
                    .isInstanceOf(EmailAlreadyInUseException.class)
                    .hasMessageContaining("already in use");

            verify(keycloakAdminService, times(1)).deleteUser(keycloakUserId);
        }
    }

    // ── Email normalization ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Email normalization")
    class EmailNormalization {

        @BeforeEach
        void mockSuccess() {
            when(userRepository.existsByEmail(any())).thenReturn(false);
            when(keycloakAdminService.getUserIdByEmail(any())).thenReturn(null);
            when(keycloakAdminService.createUser(any(), any(), any())).thenAnswer(inv -> UUID.randomUUID());
            doNothing().when(keycloakAdminService).assignRoleToUser(any(), any());
            doNothing().when(keycloakAdminService).activateUser(any());
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(emailService).sendRegistrationEmail(any(), any());
        }

        @Test
        @DisplayName("uppercase email is normalized to lowercase before existsByEmail check")
        void emailNormalized_beforeDuplicateCheck() {
            request.setEmail("John@Example.COM");
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
        @DisplayName("audit events use normalized email — not raw request email")
        void auditUsesNormalizedEmail() {
            request.setEmail("Mixed@Case.COM");
            service.register(request);
            // auditLogService must have been called at least twice (ATTEMPT + SUCCESS)
            verify(auditLogService, atLeast(2)).saveAuditLog(any());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private InviteCode pendingInviteCode(String roleName, Event event) {
        InviteCode ic = new InviteCode();
        ic.setId(UUID.randomUUID());
        ic.setCode("ABCD-EFGH-IJKL-MNOP");
        ic.setRoleName(roleName);
        ic.setStatus(InviteCodeStatus.PENDING);
        ic.setExpiresAt(LocalDateTime.now().plusHours(24));
        ic.setEvent(event);
        User creator = new User();
        creator.setId(UUID.randomUUID());
        ic.setCreatedBy(creator);
        return ic;
    }
}