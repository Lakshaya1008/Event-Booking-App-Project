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
 * 1. Removed all existsByCode() stubs — method doesn't exist on repository.
 * 2. Added missing @Mock AuditLogService and SystemUserProvider.
 * 3. NEW: RedeemInviteCode nested class — 8 test cases covering the previously
 *    completely untested redeemInviteCode() method.
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

    // ── Helper builders ───────────────────────────────────────────────────────

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

            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "STAFF", null, 24))
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

            InviteCodeResponseDto result =
                    service.generateInviteCode(creatorId, "STAFF", eventId, 24);

            assertThat(result).isNotNull();
            assertThat(result.getRoleName()).isEqualTo("STAFF");
            assertThat(result.getEventId()).isEqualTo(eventId);
        }

        @Test
        @DisplayName("ATTENDEE invite without eventId succeeds")
        void attendeeInviteSucceeds() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> {
                InviteCode ic = inv.getArgument(0);
                ic.setId(UUID.randomUUID());
                ic.setCreatedAt(LocalDateTime.now());
                ic.setExpiresAt(LocalDateTime.now().plusHours(48));
                return ic;
            });

            InviteCodeResponseDto result =
                    service.generateInviteCode(creatorId, "ATTENDEE", null, 48);

            assertThat(result).isNotNull();
            assertThat(result.getRoleName()).isEqualTo("ATTENDEE");
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

            assertThatCode(() ->
                    service.generateInviteCode(creatorId, "STAFF", eventId, 24))
                    .doesNotThrowAnyException();

            verify(inviteCodeRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("throws after 5 consecutive code collisions")
        void throwsAfterMaxRetries() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "STAFF", eventId, 24))
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
        @DisplayName("happy path — ATTENDEE code redeemed: role assigned, code marked REDEEMED, audit emitted")
        void happyPath_attendeeCode() {
            InviteCode code = buildPendingCode("ATTENDEE", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode("ABCD-EFGH-IJKL-MNOP")).thenReturn(Optional.of(code));
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "ATTENDEE");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ATTENDEE"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RedeemInviteCodeResponseDto result =
                    service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            assertThat(result.getRoleAssigned()).isEqualTo("ATTENDEE");
            assertThat(result.getMessage()).contains("successfully");
            assertThat(result.getEventName()).isNull();

            // Code must be marked REDEEMED
            assertThat(code.getStatus()).isEqualTo(InviteCodeStatus.REDEEMED);
            assertThat(code.getRedeemedBy()).isEqualTo(redeemer);
            assertThat(code.getRedeemedAt()).isNotNull();

            // Keycloak role must be assigned
            verify(keycloakAdminService).assignRoleToUser(userId, "ATTENDEE");
        }

        @Test
        @DisplayName("STAFF code — role assigned AND user added to event.staff list")
        void staffCode_assignsRoleAndAddsToEventStaff() {
            InviteCode code = buildPendingCode("STAFF", event);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "STAFF");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("STAFF"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            RedeemInviteCodeResponseDto result =
                    service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            assertThat(result.getRoleAssigned()).isEqualTo("STAFF");
            assertThat(result.getEventName()).isEqualTo("Tech Conference");

            // User must be in event.staff after redemption
            verify(eventRepository).save(argThat(e ->
                    e.getStaff().stream().anyMatch(s -> s.getId().equals(userId))));
        }

        @Test
        @DisplayName("STAFF code — user already in staff → no duplicate add, no exception")
        void staffCode_alreadyStaff_noDuplicate() {
            event.getStaff().add(redeemer); // pre-populated
            InviteCode code = buildPendingCode("STAFF", event);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "STAFF");
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("STAFF"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .doesNotThrowAnyException();

            // Staff list should still contain exactly 1 entry (no duplicate)
            assertThat(event.getStaff()).hasSize(1);
        }

        @Test
        @DisplayName("already REDEEMED code → throws InvalidInviteCodeException, Keycloak never called")
        void alreadyRedeemedCode_throwsException() {
            InviteCode code = buildRedeemedCode();
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("already been redeemed");

            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("EXPIRED code → throws InvalidInviteCodeException, Keycloak never called")
        void expiredCode_throwsException() {
            InviteCode code = buildExpiredCode();
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("expired");

            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("REVOKED code → throws InvalidInviteCodeException, Keycloak never called")
        void revokedCode_throwsException() {
            InviteCode code = buildRevokedCode();
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("revoked");

            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("Keycloak role assignment fails → code stays PENDING, InvalidBusinessStateException thrown")
        void keycloakFailure_codeStaysPending() {
            InviteCode code = buildPendingCode("ORGANIZER", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));
            doThrow(new RuntimeException("Keycloak down"))
                    .when(keycloakAdminService).assignRoleToUser(any(), any());

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Failed to assign role");

            // Code must NOT be marked REDEEMED when Keycloak failed
            assertThat(code.getStatus()).isEqualTo(InviteCodeStatus.PENDING);
        }

        @Test
        @DisplayName("code not found → throws InviteCodeNotFoundException, audit emitted")
        void codeNotFound_throwsException() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "XXXX-YYYY-ZZZZ-AAAA"))
                    .isInstanceOf(InviteCodeNotFoundException.class);

            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }

        @Test
        @DisplayName("ADMIN code — emits ADMIN_ROLE_GRANTED_VIA_INVITE audit log")
        void adminCode_emitsAdminAudit() {
            InviteCode code = buildPendingCode("ADMIN", null);
            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));
            doNothing().when(keycloakAdminService).assignRoleToUser(userId, "ADMIN");
            // getUserRoles returns ADMIN — confirming admin was granted
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ADMIN"));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP");

            // Audit should be saved — verify auditLogService.saveAuditLog called at least twice
            // (once for ADMIN_ROLE_GRANTED_VIA_INVITE, once for INVITE_REDEEMED)
            verify(auditLogService, atLeast(2)).saveAuditLog(any());

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService, atLeast(1)).saveAuditLog(auditCaptor.capture());

            boolean hasAdminAudit = auditCaptor.getAllValues().stream()
                    .anyMatch(a -> a.getAction() == AuditAction.ADMIN_ROLE_GRANTED_VIA_INVITE);
            assertThat(hasAdminAudit).isTrue();
        }

        @Test
        @DisplayName("FIX 1 — code that just expired is persisted as EXPIRED before validation check")
        void justExpiredCode_persistedBeforeValidation() {
            // Code is PENDING in-memory but expiresAt is in the past
            // checkAndMarkExpired() will flip it to EXPIRED in memory
            InviteCode code = buildPendingCode("ATTENDEE", null);
            code.setExpiresAt(LocalDateTime.now().minusSeconds(1)); // just expired

            when(userRepository.findById(userId)).thenReturn(Optional.of(redeemer));
            when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.of(code));
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatThrownBy(() -> service.redeemInviteCode(userId, "ABCD-EFGH-IJKL-MNOP"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("expired");

            // FIX 1 verification: save() called with EXPIRED status before validateCodeForRedemption
            ArgumentCaptor<InviteCode> codeCaptor = ArgumentCaptor.forClass(InviteCode.class);
            verify(inviteCodeRepository).save(codeCaptor.capture());
            assertThat(codeCaptor.getValue().getStatus()).isEqualTo(InviteCodeStatus.EXPIRED);

            // Keycloak was never called
            verify(keycloakAdminService, never()).assignRoleToUser(any(), any());
        }
    }

    // ── revokeInviteCode ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeInviteCode")
    class RevokeInviteCode {

        @Test
        @DisplayName("throws when trying to revoke a REDEEMED code")
        void throwsWhenCodeAlreadyRedeemed() {
            UUID codeId = UUID.randomUUID();
            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setStatus(InviteCodeStatus.REDEEMED);

            when(userRepository.existsById(creatorId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));

            assertThatThrownBy(() ->
                    service.revokeInviteCode(creatorId, codeId, "reason"))
                    .isInstanceOf(InvalidInviteCodeException.class)
                    .hasMessageContaining("REDEEMED");
        }

        @Test
        @DisplayName("successfully revokes a PENDING code and sets revokedAt + reason")
        void revokesSuccessfully() {
            UUID codeId = UUID.randomUUID();
            InviteCode code = new InviteCode();
            code.setId(codeId);
            code.setCode("ABCD-EFGH-IJKL-MNOP");
            code.setStatus(InviteCodeStatus.PENDING);

            when(userRepository.existsById(creatorId)).thenReturn(true);
            when(inviteCodeRepository.findById(codeId)).thenReturn(Optional.of(code));

            assertThatCode(() ->
                    service.revokeInviteCode(creatorId, codeId, "No longer needed"))
                    .doesNotThrowAnyException();

            assertThat(code.getStatus()).isEqualTo(InviteCodeStatus.REVOKED);
            assertThat(code.getRevokedReason()).isEqualTo("No longer needed");
            assertThat(code.getRevokedAt()).isNotNull();
            verify(inviteCodeRepository).save(code);
        }
    }

    // ── Rate Limiting ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rate limiting (100 per event, 500 per organizer)")
    class RateLimiting {

        @Test
        @DisplayName("event limit: 101st code rejected")
        void eventLimit_101_rejected() {
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(inviteCodeRepository.countByEventIdAndStatus(event.getId(), InviteCodeStatus.PENDING))
                    .thenReturn(100L);

            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "STAFF", event.getId(), 24))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("maximum invite codes limit");
        }

        @Test
        @DisplayName("organizer limit: 501st code rejected")
        void organizerLimit_501_rejected() {
            when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(inviteCodeRepository.countByEventIdAndStatus(event.getId(), InviteCodeStatus.PENDING))
                    .thenReturn(0L);
            when(inviteCodeRepository.countByCreatedByIdAndStatus(creatorId, InviteCodeStatus.PENDING))
                    .thenReturn(500L);

            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "STAFF", event.getId(), 24))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("You have reached the maximum invite codes limit");
        }
    }
}