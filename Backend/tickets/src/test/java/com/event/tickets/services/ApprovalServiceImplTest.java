package com.event.tickets.services;

import com.event.tickets.domain.dtos.UserApprovalDto;
import com.event.tickets.domain.entities.ApprovalStatus;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalServiceImpl")
class ApprovalServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private EmailService emailService;

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
            // FIX #7: rejectedAt must NOT be set on approval
            assertThat(saved.getRejectedAt()).isNull();
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
        @DisplayName("Keycloak failure does not break approval (non-critical)")
        void keycloakFailureDoesNotBreakApproval() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Keycloak down"))
                    .when(keycloakAdminService).activateUser(userId);

            // Should NOT throw — Keycloak failure is logged and swallowed
            assertThatCode(() -> service.approveUser(userId, adminId))
                    .doesNotThrowAnyException();

            // DB record still saved
            verify(userRepository).save(any());
        }
    }

    // ── rejectUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectUser")
    class RejectUser {

        @Test
        @DisplayName("FIX #7 — sets rejectedAt NOT approvedAt on rejection")
        void setsRejectedAtNotApprovedAt() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.rejectUser(userId, adminId, "Suspicious activity");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
            // FIX #7: rejectedAt must be set, approvedAt must remain null
            assertThat(saved.getRejectedAt()).isNotNull();
            assertThat(saved.getApprovedAt()).isNull();
            assertThat(saved.getRejectionReason()).isEqualTo("Suspicious activity");
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
    }

    // ── getPendingApprovals ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getPendingApprovals")
    class GetPendingApprovals {

        @Test
        @DisplayName("returns only PENDING users")
        void returnsOnlyPendingUsers() {
            Page<User> page = new PageImpl<>(List.of(pendingUser));
            when(userRepository.findByApprovalStatus(eq(ApprovalStatus.PENDING), any()))
                    .thenReturn(page);
            when(keycloakAdminService.getUserRoles(any())).thenReturn(List.of());

            Page<UserApprovalDto> result = service.getPendingApprovals(PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getApprovalStatus()).isEqualTo("PENDING");
        }
    }
}