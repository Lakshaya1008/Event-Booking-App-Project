package com.event.tickets.services;

import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.InviteCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteCodeServiceImpl")
class InviteCodeServiceImplTest {

    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private KeycloakAdminService keycloakAdminService;

    @InjectMocks
    private InviteCodeServiceImpl service;

    private UUID creatorId;
    private UUID eventId;
    private User creator;
    private Event event;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        eventId   = UUID.randomUUID();

        creator = new User();
        creator.setId(creatorId);
        creator.setName("Admin Alice");
        creator.setEmail("admin@test.com");

        event = new Event();
        event.setId(eventId);
        event.setName("Tech Conference");
    }

    // ── generateInviteCode ────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateInviteCode")
    class GenerateInviteCode {

        @Test
        @DisplayName("FIX #1 — throws when STAFF role given without eventId")
        void throwsWhenStaffWithoutEventId() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));

            // STAFF role + null eventId → must throw BEFORE any event lookup
            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "STAFF", null, 24))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("Event ID is required");

            // Confirm eventRepository was never consulted (check is before the null check)
            verify(eventRepository, never()).findById(any());
        }

        @Test
        @DisplayName("FIX #1 — STAFF invite with eventId succeeds")
        void staffWithEventIdSucceeds() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.existsByCode(anyString())).thenReturn(false);
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
        @DisplayName("ADMIN invite without eventId succeeds")
        void adminInviteWithoutEventIdSucceeds() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(inviteCodeRepository.existsByCode(anyString())).thenReturn(false);
            when(inviteCodeRepository.save(any())).thenAnswer(inv -> {
                InviteCode ic = inv.getArgument(0);
                ic.setId(UUID.randomUUID());
                ic.setCreatedAt(LocalDateTime.now());
                ic.setExpiresAt(LocalDateTime.now().plusHours(24));
                return ic;
            });

            InviteCodeResponseDto result =
                    service.generateInviteCode(creatorId, "ADMIN", null, 24);

            assertThat(result).isNotNull();
            assertThat(result.getRoleName()).isEqualTo("ADMIN");
            assertThat(result.getEventId()).isNull();
        }

        @Test
        @DisplayName("FIX #17 — retries on DataIntegrityViolationException (code collision)")
        void retriesOnCodeCollision() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.existsByCode(anyString())).thenReturn(false);

            // First save fails (collision), second succeeds
            when(inviteCodeRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenAnswer(inv -> {
                        InviteCode ic = inv.getArgument(0);
                        ic.setId(UUID.randomUUID());
                        ic.setCreatedAt(LocalDateTime.now());
                        ic.setExpiresAt(LocalDateTime.now().plusHours(24));
                        return ic;
                    });

            // Should succeed on retry, not propagate the exception
            assertThatCode(() ->
                    service.generateInviteCode(creatorId, "STAFF", eventId, 24))
                    .doesNotThrowAnyException();

            // save() was called twice — once failing, once succeeding
            verify(inviteCodeRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("throws InvalidBusinessStateException after max collision retries")
        void throwsAfterMaxRetries() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(inviteCodeRepository.existsByCode(anyString())).thenReturn(false);
            // Always fail
            when(inviteCodeRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "STAFF", eventId, 24))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Failed to generate unique invite code");
        }

        @Test
        @DisplayName("throws UserNotFoundException when creator not found")
        void throwsWhenCreatorNotFound() {
            when(userRepository.findById(creatorId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.generateInviteCode(creatorId, "ATTENDEE", null, 24))
                    .isInstanceOf(UserNotFoundException.class);
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
        @DisplayName("successfully revokes a PENDING code")
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
        }
    }
}

