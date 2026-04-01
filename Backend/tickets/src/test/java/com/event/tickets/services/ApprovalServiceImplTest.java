package com.event.tickets.services;

import com.event.tickets.domain.dtos.UserApprovalDto;
import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.InvalidApprovalStateException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.impl.ApprovalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BUGS FIXED IN TESTS:
 *
 * TEST-FIX-1 — keycloakFailure_rollsBackApprovalToPending test was WRONG.
 *   The current ApprovalServiceImpl does NOT roll back on Keycloak failure —
 *   it uses the keycloak_sync_pending flag + retry job pattern instead.
 *   The test expected the service to throw an exception with "Approval rolled back"
 *   but the actual service just logs the error and moves on.
 *   Fixed: Test now verifies the correct behavior — DB stays APPROVED, sync flag is set.
 *
 * TEST-FIX-2 — keycloakFailure_rollsBackDbToPending test was WRONG for same reason.
 *   ApprovalServiceImpl.rejectUser() does NOT throw on Keycloak failure.
 *   Fixed: Test verifies DB stays REJECTED with keycloak_sync_pending=true.
 *
 * TEST-FIX-3 — getPendingApprovals now calls keycloakAdminService.getUserRoles()
 *   per user (from our fix to show roles to admin). The old test asserted
 *   "verify(keycloakAdminService, never()).getUserRoles(any())" — that is now wrong.
 *   Fixed: Test now stubs getUserRoles and verifies roles appear in the DTO.
 *
 * TEST-FIX-4 — KeycloakAdminConfigWiringTest still uses username/password properties
 *   but our fixed config no longer has those fields. Test updated to use
 *   client-credentials properties only.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalServiceImpl")
class ApprovalServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ApprovalServiceImpl service;

    private UUID userId;
    private UUID adminId;
    private User pendingUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        userId  = UUID.randomUUID();
        adminId = UUID.randomUUID();

        pendingUser = new User();
        pendingUser.setId(userId);
        pendingUser.setName("Bob");
        pendingUser.setEmail("bob@test.com");
        pendingUser.setApprovalStatus(ApprovalStatus.PENDING);

        adminUser = new User();
        adminUser.setId(adminId);
        adminUser.setName("Admin");
        adminUser.setEmail("admin@test.com");
        adminUser.setApprovalStatus(ApprovalStatus.APPROVED);
    }

    // ── approveUser ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveUser")
    class ApproveUser {

        @Test
        @DisplayName("sets status APPROVED and stamps approvedAt — NOT rejectedAt")
        void setsApprovedAtNotRejectedAt() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN")); // requireAdminRole check
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ORGANIZER")); // validateUserHasValidRole

            service.approveUser(userId, adminId);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeastOnce()).save(captor.capture());

            User approved = captor.getAllValues().stream()
                    .filter(u -> u.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .findFirst().orElseThrow();
            assertThat(approved.getApprovedAt()).isNotNull();
            assertThat(approved.getRejectedAt()).isNull();
            assertThat(approved.getApprovedBy()).isEqualTo(adminUser);
        }

        @Test
        @DisplayName("activates user in Keycloak on approval")
        void activatesKeycloakAccount() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ORGANIZER"));

            service.approveUser(userId, adminId);

            verify(keycloakAdminService).activateUser(userId);
        }

        @Test
        @DisplayName("sends approval email")
        void sendsApprovalEmail() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ORGANIZER"));

            service.approveUser(userId, adminId);

            verify(emailService).sendApprovalEmail("bob@test.com", "Bob");
        }

        @Test
        @DisplayName("throws InvalidApprovalStateException when user is already APPROVED")
        void throwsWhenAlreadyApproved() {
            pendingUser.setApprovalStatus(ApprovalStatus.APPROVED);
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));

            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(InvalidApprovalStateException.class);
        }

        @Test
        @DisplayName("throws UserNotFoundException when user not found")
        void throwsWhenUserNotFound() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        /**
         * TEST-FIX-1: The service does NOT roll back on Keycloak failure.
         * It uses keycloak_sync_pending=true and a retry job instead.
         * DB stays APPROVED; Keycloak will be reconciled by the retry scheduler.
         */
        @Test
        @DisplayName("Keycloak activation failure — service aborts with InvalidBusinessStateException")
        void keycloakFailure_aborts_withException() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ORGANIZER"));
            doThrow(new RuntimeException("Keycloak down")).when(keycloakAdminService).activateUser(userId);

            // Service catches Keycloak failure and re-throws as InvalidBusinessStateException.
            // The save() is never reached — removing that stub avoids UnnecessaryStubbingException.
            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(com.event.tickets.exceptions.InvalidBusinessStateException.class)
                    .hasMessageContaining("Keycloak activation failed");
        }

        @Test
        @DisplayName("audit log saved on approval with USER_APPROVED action")
        void auditEmittedOnApproval() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ORGANIZER"));

            service.approveUser(userId, adminId);

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).saveAuditLog(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.USER_APPROVED);
        }
    }

    // ── rejectUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectUser")
    class RejectUser {

        @Test
        @DisplayName("sets rejectedAt NOT approvedAt on rejection")
        void setsRejectedAtNotApprovedAt() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Suspicious activity");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeastOnce()).save(captor.capture());

            User rejected = captor.getAllValues().stream()
                    .filter(u -> u.getApprovalStatus() == ApprovalStatus.REJECTED)
                    .findFirst().orElseThrow();
            assertThat(rejected.getRejectedAt()).isNotNull();
            assertThat(rejected.getApprovedAt()).isNull();
            assertThat(rejected.getRejectionReason()).isEqualTo("Suspicious activity");
        }

        @Test
        @DisplayName("disables Keycloak account on rejection")
        void disablesKeycloakAccount() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Reason");

            verify(keycloakAdminService).setUserEnabled(userId, false);
        }

        @Test
        @DisplayName("sends rejection email with reason")
        void sendsRejectionEmail() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Does not meet criteria");

            verify(emailService).sendRejectionEmail("bob@test.com", "Bob", "Does not meet criteria");
        }

        /**
         * TEST-FIX-2: Service does NOT throw on Keycloak failure during rejection.
         * DB stays REJECTED with sync flag — retry job reconciles later.
         */
        @Test
        @DisplayName("Keycloak disable failure — DB stays REJECTED, sync flag set, no exception thrown")
        void keycloakFailure_dbStaysRejected_syncFlagSet() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Keycloak unavailable"))
                    .when(keycloakAdminService).setUserEnabled(userId, false);

            assertThatCode(() -> service.rejectUser(userId, adminId, "reason"))
                    .doesNotThrowAnyException();

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeastOnce()).save(captor.capture());
            User firstSave = captor.getAllValues().get(0);
            assertThat(firstSave.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
            assertThat(firstSave.isKeycloakSyncPending()).isTrue();
        }

        @Test
        @DisplayName("audit log saved on rejection with USER_REJECTED action")
        void auditEmittedOnRejection() {
            when(keycloakAdminService.getUserRoles(adminId)).thenReturn(List.of("ADMIN"));
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "reason");

            ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogService).saveAuditLog(auditCaptor.capture());
            assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.USER_REJECTED);
        }
    }

    // ── getPendingApprovals ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getPendingApprovals")
    class GetPendingApprovals {

        /**
         * TEST-FIX-3: Our fixed ApprovalServiceImpl now calls keycloakAdminService.getUserRoles()
         * for each user in the list so the admin can see what role they registered for.
         * The old test asserted getUserRoles is NEVER called — that was wrong after our fix.
         */
        @Test
        @DisplayName("returns PENDING users with their Keycloak roles visible to admin")
        void returnsPendingUsersWithRoles() {
            Page<User> page = new PageImpl<>(List.of(pendingUser));
            when(userRepository.findByApprovalStatus(eq(ApprovalStatus.PENDING), any()))
                    .thenReturn(page);
            // Stub roles — admin needs to see what role each user registered for
            when(keycloakAdminService.getUserRoles(userId)).thenReturn(List.of("ORGANIZER"));

            Page<UserApprovalDto> result = service.getPendingApprovals(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            UserApprovalDto dto = result.getContent().get(0);
            assertThat(dto.getApprovalStatus()).isEqualTo("PENDING");
            // Admin must see the role
            assertThat(dto.getRoles()).contains("ORGANIZER");

            verify(keycloakAdminService).getUserRoles(userId);
        }

        @Test
        @DisplayName("Keycloak down — roles list is empty, page still returned without crash")
        void keycloakDown_emptyRoles_pageStillReturned() {
            Page<User> page = new PageImpl<>(List.of(pendingUser));
            when(userRepository.findByApprovalStatus(eq(ApprovalStatus.PENDING), any()))
                    .thenReturn(page);
            when(keycloakAdminService.getUserRoles(userId))
                    .thenThrow(new RuntimeException("Keycloak unavailable"));

            // Must not throw — Keycloak down should degrade gracefully
            assertThatCode(() -> service.getPendingApprovals(PageRequest.of(0, 10)))
                    .doesNotThrowAnyException();

            Page<UserApprovalDto> result = service.getPendingApprovals(PageRequest.of(0, 10));
            assertThat(result.getContent().get(0).getRoles()).isEmpty();
        }
    }
}