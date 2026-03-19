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
 * CHANGES FROM PREVIOUS VERSION:
 *
 * FIX 1 — Added @Mock AuditLogService auditLogService.
 *   ApprovalServiceImpl calls auditLogService.saveAuditLog() inside emitApprovalAudit().
 *   Without this mock, Mockito @InjectMocks leaves auditLogService null. The try/catch
 *   around the audit call was silently swallowing the NPE — tests were passing only because
 *   audit failures are non-critical. Adding the mock makes the test honest and prevents
 *   future NPEs from going unnoticed when audit call patterns change.
 *
 * FIX 2 — getPendingApprovals test no longer stubs keycloakAdminService.getUserRoles().
 *   The service no longer calls getUserRoles() in list responses (M-03 FIX).
 *   The old stub was an UnnecessaryStubbingException waiting to happen.
 *
 * NEW TESTS:
 *   - rejectUser_keycloakFailure_rollsBackToP_ENDING — verifies the DB rollback on Keycloak failure
 *   - auditEmittedOnApproval — verifies audit log is actually saved (was silently NPE'd before)
 *   - auditEmittedOnRejection — same
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalServiceImpl")
class ApprovalServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;  // FIX 1: was missing — silent NPE on every test

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
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.approveUser(userId, adminId);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
            assertThat(saved.getApprovedAt()).isNotNull();
            assertThat(saved.getRejectedAt()).isNull();  // must NOT be set on approval
            assertThat(saved.getApprovedBy()).isEqualTo(adminUser);
        }

        @Test
        @DisplayName("activates user in Keycloak")
        void activatesKeycloakAccount() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.approveUser(userId, adminId);

            verify(keycloakAdminService).activateUser(userId);
        }

        @Test
        @DisplayName("sends approval email")
        void sendsApprovalEmail() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.approveUser(userId, adminId);

            verify(emailService).sendApprovalEmail("bob@test.com", "Bob");
        }

        @Test
        @DisplayName("throws InvalidApprovalStateException when user is already APPROVED")
        void throwsWhenAlreadyApproved() {
            pendingUser.setApprovalStatus(ApprovalStatus.APPROVED);
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));

            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(InvalidApprovalStateException.class)
                    .hasMessageContaining("APPROVED");
        }

        @Test
        @DisplayName("throws InvalidApprovalStateException when user is already REJECTED")
        void throwsWhenAlreadyRejected() {
            pendingUser.setApprovalStatus(ApprovalStatus.REJECTED);
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));

            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(InvalidApprovalStateException.class);
        }

        @Test
        @DisplayName("throws UserNotFoundException when user not found")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("Keycloak failure on approve — DB changes rolled back to PENDING")
        void keycloakFailure_rollsBackApprovalToPending() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Keycloak down")).when(keycloakAdminService).activateUser(userId);

            assertThatThrownBy(() -> service.approveUser(userId, adminId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Approval rolled back to PENDING");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(2)).save(captor.capture());
            User rollbackSave = captor.getAllValues().get(1);
            assertThat(rollbackSave.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
            assertThat(rollbackSave.getApprovedAt()).isNull();
            assertThat(rollbackSave.getApprovedBy()).isNull();
        }

        @Test
        @DisplayName("FIX 1 — audit log is actually saved on approval (was silently NPE'd before)")
        void auditEmittedOnApproval() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.approveUser(userId, adminId);

            // Verify audit log was actually saved — not silently swallowed by NPE
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
        @DisplayName("sets rejectedAt NOT approvedAt on rejection (FIX #7)")
        void setsRejectedAtNotApprovedAt() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Suspicious activity");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, atLeastOnce()).save(captor.capture());

            // Find the REJECTED save (not any rollback save)
            User rejectedSave = captor.getAllValues().stream()
                    .filter(u -> u.getApprovalStatus() == ApprovalStatus.REJECTED)
                    .findFirst().orElseThrow();
            assertThat(rejectedSave.getRejectedAt()).isNotNull();
            assertThat(rejectedSave.getApprovedAt()).isNull();
            assertThat(rejectedSave.getRejectionReason()).isEqualTo("Suspicious activity");
        }

        @Test
        @DisplayName("disables Keycloak account on rejection")
        void disablesKeycloakAccount() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Reason");

            verify(keycloakAdminService).setUserEnabled(userId, false);
        }

        @Test
        @DisplayName("sends rejection email with reason")
        void sendsRejectionEmail() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Does not meet criteria");

            verify(emailService).sendRejectionEmail("bob@test.com", "Bob", "Does not meet criteria");
        }

        @Test
        @DisplayName("throws InvalidApprovalStateException when user is not PENDING")
        void throwsWhenNotPending() {
            pendingUser.setApprovalStatus(ApprovalStatus.APPROVED);
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));

            assertThatThrownBy(() -> service.rejectUser(userId, adminId, "reason"))
                    .isInstanceOf(InvalidApprovalStateException.class);
        }

        @Test
        @DisplayName("Keycloak failure on reject — DB changes rolled back to PENDING")
        void keycloakFailure_rollsBackDbToPending() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Keycloak unavailable"))
                    .when(keycloakAdminService).setUserEnabled(userId, false);

            assertThatThrownBy(() -> service.rejectUser(userId, adminId, "reason"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Keycloak synchronization failed");

            // DB must be rolled back: status back to PENDING
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(2)).save(captor.capture());

            // Second save is the rollback — status must be PENDING
            User rollbackSave = captor.getAllValues().get(1);
            assertThat(rollbackSave.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
            assertThat(rollbackSave.getRejectedAt()).isNull();
            assertThat(rollbackSave.getRejectionReason()).isNull();
        }

        @Test
        @DisplayName("FIX 1 — audit log is saved on rejection (was silently NPE'd before)")
        void auditEmittedOnRejection() {
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

        @Test
        @DisplayName("returns only PENDING users — no Keycloak calls (M-03 FIX)")
        void returnsOnlyPendingUsers() {
            Page<User> page = new PageImpl<>(List.of(pendingUser));
            when(userRepository.findByApprovalStatus(eq(ApprovalStatus.PENDING), any()))
                    .thenReturn(page);

            Page<UserApprovalDto> result = service.getPendingApprovals(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getApprovalStatus()).isEqualTo("PENDING");

            // M-03 FIX: NO Keycloak call on list pages
            verify(keycloakAdminService, never()).getUserRoles(any());
        }
    }
}