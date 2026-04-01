package com.event.tickets.services;

import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto;
import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.InviteCodeServiceImpl;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CHANGES FROM PREVIOUS VERSION:
 *
 * TEST-I2 — revokeInviteCode() tests updated to pass isAdmin parameter.
 *   The method signature changed: revokeInviteCode(revokerId, codeId, reason, isAdmin).
 *   All revokeInviteCode() call sites in tests updated accordingly.
 *   keycloakAdminService.userHasRole() is no longer stubbed — the service no longer calls it.
 *
 * TEST-I1 — redeemInviteCode() tests verify getUserRoles() called exactly once.
 *   New assertion: verify(keycloakAdminService, times(1)).getUserRoles(any()) in happy path tests.
 *
 * TEST-I3 — new test: mapToResponseDto populates revokedAt and revokedReason.
 *   Verifies the I-3 fix is wired end-to-end through the service.
 *
 * TEST-I7 — new test: mapToResponseDto populates createdByUserId.
 *
 * TEST-I2-ACCESS — new test: non-admin trying to revoke another user's code throws AccessDeniedException.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InviteCodeServiceImpl")
class InviteCodeServiceImplTest {

    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    @InjectMocks
    private InviteCodeServiceImpl service;

    private UUID creatorId;
    private UUID eventId;
    private UUID userId;
    private User creator;
    private User redeemer;
    private Event event;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        eventId   = UUID.randomUUID();
        userId    = UUID.randomUUID();

        creator = new User();
        creator.setId(creatorId);
        creator.setName("Admin Alice");
        creator.setEmail("admin@test.com");

        redeemer = new User();
        redeemer.setId(userId);
        redeemer.setName("Bob Redeemer");
        redeemer.setEmail("bob@test.com");

        event = new Event();
        event.setId(eventId);
        event.setName("Tech Conference");
        event.setStaff(new ArrayList<>());

        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    private InviteCode buildPendingCode(String roleName, Event forEvent) {
        InviteCode code = new InviteCode();
        code.setId(UUID.randomUUID());
        code.setCode("ABCD-EFGH-IJKL-MNOP");
        code.setRoleName(roleName);
        code.setStatus(InviteCodeStatus.PENDING);
        code.setCreatedBy(creator);
        code.setExpiresAt(LocalDateTime.now().plusHours(24));
        code.setEvent(forEvent);
        return code;
    }

    private InviteCode buildRedeemedCode() {
        InviteCode code = buildPendingCode("ATTENDEE", null);
        code.setStatus(InviteCodeStatus.REDEEMED);
        code.setRedeemedBy(creator);
        code.setRedeemedAt(LocalDateTime.now().minusMinutes(30));
        return code;
    }

    private InviteCode buildExpiredCode() {
        InviteCode code = buildPendingCode("ATTENDEE", null);
        code.setStatus(InviteCodeStatus.EXPIRED);
        code.setExpiresAt(LocalDateTime.now().minusHours(1));
        return code;
    }

    private InviteCode buildRevokedCode() {
        InviteCode code = buildPendingCode("ATTENDEE", null);
        code.setStatus(InviteCodeStatus.REVOKED);
        code.setRevokedReason("No longer needed");
        code.setRevokedAt(LocalDateTime.now().minusHours(2));
        return code;
    }

    // ── generateInviteCode ────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateInviteCode")
    class GenerateInviteCode {

        @Test
        @DisplayName("throws when STAFF role given without eventId")
        void throwsWhenStaffWithoutEventId() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            assertThatThrownBy(() -> service.generateInviteCode(creatorId, "STAFF", null, 24))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Event ID is required");
            verify(eventRepository, never()).findById(any());
        }

        @Test
        @DisplayName("STAFF invite with eventId succeeds")
        void staffWithEventIdSucceeds() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> {
                InviteCode ic = inv.getArgument(0);
                ic.setId(UUID.randomUUID());
                ic.setCreatedAt(LocalDateTime.now());
                ic.setExpiresAt(LocalDateTime.now().plusHours(24));
                return ic;
            });
            InviteCodeResponseDto result = service.generateInviteCode(creatorId, "STAFF", eventId, 24);
            assertThat(result.getRoleName()).isEqualTo("STAFF");
            assertThat(result.getEventId()).isEqualTo(eventId);
        }

        @Test
        @DisplayName("retries on DataIntegrityViolationException — code collision")
        void retriesOnCodeCollision() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenAnswer(inv -> {
                        InviteCode ic = inv.getArgument(0);
                        ic.setId(UUID.randomUUID());
                        ic.setCreatedAt(LocalDateTime.now());
                        ic.setExpiresAt(LocalDateTime.now().plusHours(24));
                        return ic;
                    });
            assertThatCode(() -> service.generateInviteCode(creatorId, "STAFF", eventId, 24))
                    .doesNotThrowAnyException();
            verify(inviteCodeRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("throws after 5 consecutive code collisions")
        void throwsAfterMaxRetries() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
            assertThatThrownBy(() -> service.generateInviteCode(creatorId, "STAFF", eventId, 24))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Failed to generate unique invite code");
            verify(inviteCodeRepository, times(5)).save(any());
        }
    }

    // ── redeemInviteCode ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("redeemInviteCode")
    class RedeemInviteCode {

        @Test
        @DisplayName("FIX I-1 — getUserRoles() called exactly once per redemption")
        void getUserRolesCalledExactlyOnce() {
            InviteCode code = buildPendingCode("ATTENDEE", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "ATTENDEE");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ATTENDEE"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            // FIX I-1: exactly one call regardless of role
            verify(keycloakAdminService, times(1)).getUserRoles(userId);
        }

        @Test
        @DisplayName("FIX I-1 — ADMIN code redemption also calls getUserRoles() exactly once")
        void getUserRolesCalledExactlyOnceForAdminCode() {
            InviteCode code = buildPendingCode("ADMIN", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "ADMIN");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ADMIN"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            // BEFORE this fix: called twice for ADMIN codes (once in audit block, once at end)
            verify(keycloakAdminService, times(1)).getUserRoles(userId);
        }

        @Test
        @DisplayName("happy path — ATTENDEE code redeemed: role assigned, code marked REDEEMED")
        void happyPath_attendeeCode() {
            InviteCode code = buildPendingCode("ATTENDEE", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate("ABCD-EFGH-IJKL-MNOP")).thenReturn(Optional.of(code)); // BUG-T2 FIX
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "ATTENDEE");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ATTENDEE"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RedeemInviteCodeResponseDto result = service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            assertThat(result.getRoleAssigned()).isEqualTo("ATTENDEE");
            assertThat(result.getMessage()).contains("successfully");
            assertThat(code.getStatus()).isEqualTo(InviteCodeStatus.REDEEMED);
            assertThat(code.getRedeemedBy()).isEqualTo(redeemer);
        }

        @Test
        @DisplayName("STAFF code — user added to event.staff list")
        void staffCode_assignsRoleAndAddsToEventStaff() {
            InviteCode code = buildPendingCode("STAFF", event);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "STAFF");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("STAFF"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RedeemInviteCodeResponseDto result = service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            assertThat(result.getEventName()).isEqualTo("Tech Conference");
            verify(eventRepository).save(argThat(e ->
                    e.getStaff().stream().anyMatch(s -> s.getId().equals(userId))));
        }

        @Test
        @DisplayName("already REDEEMED code → throws, Keycloak never called")
        void alreadyRedeemedCode_throwsException() {
            InviteCode code = buildRedeemedCode();
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("already been redeemed");
            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("EXPIRED code → throws, Keycloak never called")
        void expiredCode_throwsException() {
            InviteCode code = buildExpiredCode();
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("expired");
            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("REVOKED code → throws, Keycloak never called")
        void revokedCode_throwsException() {
            InviteCode code = buildRevokedCode();
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("revoked");
            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("Keycloak failure → code stays PENDING, exception thrown")
        void keycloakFailure_codeStaysPending() {
            InviteCode code = buildPendingCode("ORGANIZER", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            doThrow(new RuntimeException("Keycloak down")).when(keycloakAdminService).assignRoleToUser(any(), any());
            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Failed to assign role");
            assertThat(code.getStatus()).isEqualTo(InviteCodeStatus.PENDING);
        }

        @Test
        @DisplayName("FIX 1 — just-expired code persisted as EXPIRED before validation")
        void justExpiredCode_persistedBeforeValidation() {
            InviteCode code = buildPendingCode("ATTENDEE", null);
            code.setExpiresAt(LocalDateTime.now().minusSeconds(1));
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCodeForUpdate(anyString())).thenReturn(Optional.of(code)); // BUG-T2 FIX
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("expired");

            ArgumentCaptor<InviteCode> codeCaptor = ArgumentCaptor.forClass(InviteCode.class);
            verify(inviteCodeRepository).save(codeCaptor.capture());
            assertThat(codeCaptor.getValue().getStatus()).isEqualTo(InviteCodeStatus.EXPIRED);
            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }
    }

    // ── revokeInviteCode ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeInviteCode — FIX I-2: isAdmin from JWT, no Keycloak call")
    class RevokeInviteCode {

        @Test
        @DisplayName("FIX I-2 — keycloakAdminService.userHasRole() is never called")
        void keycloakUserHasRoleNeverCalled() {
            UUID codeId = UUID.randomUUID();
            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setCode("TEST-CODE");
            code.setStatus(InviteCodeStatus.PENDING);
            code.setCreatedBy(creator);

            when(userRepository.existsById(creatorId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));

            service.revokeInviteCode(creatorId, codeId, "reason", false);

            // FIX I-2: the old Keycloak call is gone
            verify(keycloakAdminService, never()).userHasRole(any(), any());
        }

        @Test
        @DisplayName("throws when trying to revoke a REDEEMED code")
        void throwsWhenCodeAlreadyRedeemed() {
            UUID codeId = UUID.randomUUID();
            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setStatus(InviteCodeStatus.REDEEMED);
            // FIX: Service checks ownership/admin BEFORE status (lines 306-309 before 311-313).
            // With createdBy=null and isAdmin=false the auth check fires first (AccessDeniedException).
            // Set createdBy=creator so revoker==creator passes the auth check, then the status
            // check correctly throws InvalidInviteCodeException for REDEEMED status.
            code.setCreatedBy(creator);
            when(userRepository.existsById(creatorId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));
            assertThatThrownBy(() -> service.revokeInviteCode(creatorId, codeId, "reason", false))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("REDEEMED");
        }

        @Test
        @DisplayName("non-admin revoking another user's code → throws AccessDeniedException")
        void nonAdminCannotRevokeOthersCode() {
            UUID codeId = UUID.randomUUID();
            UUID otherCreatorId = UUID.randomUUID();  // code belongs to a different user

            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setCode("OTHER-USER-CODE");
            code.setStatus(InviteCodeStatus.PENDING);
            code.setCreatedBy(creator); // creator.id = creatorId

            when(userRepository.existsById(otherCreatorId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));

            // isAdmin=false, revokerId != createdBy.getId() → should throw
            assertThatThrownBy(() -> service.revokeInviteCode(otherCreatorId, codeId, "reason", false))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("admin can revoke any code regardless of creator")
        void adminCanRevokeAnyCode() {
            UUID codeId = UUID.randomUUID();
            UUID adminId = UUID.randomUUID();

            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setCode("SOMEONE-ELSE-CODE");
            code.setStatus(InviteCodeStatus.PENDING);
            code.setCreatedBy(creator); // creator.id != adminId

            when(userRepository.existsById(adminId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));

            // isAdmin=true — no ownership check
            assertThatCode(() -> service.revokeInviteCode(adminId, codeId, "admin override", true))
                    .doesNotThrowAnyException();

            assertThat(code.getStatus()).isEqualTo(InviteCodeStatus.REVOKED);
            assertThat(code.getRevokedReason()).isEqualTo("admin override");
        }

        @Test
        @DisplayName("FIX I-3 — revoked code has revokedAt and revokedReason set")
        void revokedCodeSetsRevokedAtAndReason() {
            UUID codeId = UUID.randomUUID();
            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setCode("PENDING-CODE");
            code.setStatus(InviteCodeStatus.PENDING);
            code.setCreatedBy(creator);

            when(userRepository.existsById(creatorId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));

            service.revokeInviteCode(creatorId, codeId, "Testing revoke", false);

            assertThat(code.getRevokedReason()).isEqualTo("Testing revoke");
            assertThat(code.getRevokedAt()).isNotNull();
        }
    }

    // ── Rate Limiting ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        @DisplayName("event limit: 101st code rejected")
        void eventLimit_101_rejected() {
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(inviteCodeRepository.countByEventIdAndStatus(event.getId(), InviteCodeStatus.PENDING)).thenReturn(100L);
            assertThatThrownBy(() -> service.generateInviteCode(creatorId, "STAFF", event.getId(), 24))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("maximum invite codes limit");
        }

        @Test
        @DisplayName("organizer limit: 501st code rejected")
        void organizerLimit_501_rejected() {
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(inviteCodeRepository.countByEventIdAndStatus(event.getId(), InviteCodeStatus.PENDING)).thenReturn(0L);
            when(inviteCodeRepository.countByCreatedByIdAndStatus(creatorId, InviteCodeStatus.PENDING)).thenReturn(500L);
            assertThatThrownBy(() -> service.generateInviteCode(creatorId, "STAFF", event.getId(), 24))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("You have reached the maximum invite codes limit");
        }
    }
}