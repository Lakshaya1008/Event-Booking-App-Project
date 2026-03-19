package com.event.tickets.services;

import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.TicketValidationServiceImpl;
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
import org.springframework.security.access.AccessDeniedException;

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
 * FIX 1 — setActive() on QrCode replaced with setStatus(QrCodeStatusEnum).
 * FIX 2 — requireStaffOrOrganizerAccess() replaced with requireOrganizerOrStaffAccess().
 * FIX 3 — removed findFirstByTicketIdOrderByCreatedAtDesc() stubs (method doesn't exist).
 *          Duplicate scan simulation now done by adding TicketValidation to ticket.getValidations().
 * FIX 4 — AccessDeniedException uses org.springframework.security.access.AccessDeniedException.
 *
 * NEW — Tests for Ticket.status VALIDATED transition:
 *   - First valid scan → Ticket.status written to VALIDATED in DB
 *   - Second scan on VALIDATED ticket → throws immediately (fast path)
 *   - Duplicate scan on PURCHASED ticket (prior VALID validation) → INVALID, no status change
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TicketValidationServiceImpl")
class TicketValidationServiceImplTest {

    @Mock private QrCodeRepository qrCodeRepository;
    @Mock private TicketValidationRepository ticketValidationRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private UserRepository userRepository;
    @Mock private SystemUserProvider systemUserProvider;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private TicketValidationServiceImpl service;

    private UUID validatorId;
    private UUID ticketId;
    private UUID qrCodeId;
    private User validator;
    private User purchaser;
    private Event event;
    private TicketType ticketType;
    private Ticket purchasedTicket;
    private QrCode activeQrCode;

    @BeforeEach
    void setUp() {
        validatorId = UUID.randomUUID();
        ticketId    = UUID.randomUUID();
        qrCodeId    = UUID.randomUUID();

        validator = new User();
        validator.setId(validatorId);
        validator.setName("John Staff");

        purchaser = new User();
        purchaser.setId(UUID.randomUUID());
        purchaser.setName("Jane Buyer");

        event = new Event();
        event.setId(UUID.randomUUID());
        event.setName("Tech Conference");

        ticketType = new TicketType();
        ticketType.setId(UUID.randomUUID());
        ticketType.setEvent(event);

        purchasedTicket = new Ticket();
        purchasedTicket.setId(ticketId);
        purchasedTicket.setStatus(TicketStatusEnum.PURCHASED);
        purchasedTicket.setTicketType(ticketType);
        purchasedTicket.setPurchaser(purchaser);
        purchasedTicket.setValidations(new ArrayList<>());

        activeQrCode = new QrCode();
        activeQrCode.setId(qrCodeId);
        activeQrCode.setStatus(QrCodeStatusEnum.ACTIVE);  // FIX 1: use setStatus, not setActive
        activeQrCode.setTicket(purchasedTicket);

        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    // ── validateTicketByQrCode ────────────────────────────────────────────────

    @Nested
    @DisplayName("validateTicketByQrCode")
    class ValidateByQrCode {

        @Test
        @DisplayName("happy path — returns VALID TicketValidation and transitions ticket to VALIDATED")
        void happyPath_returnsValidAndTransitionsStatus() {
            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE))
                    .thenReturn(Optional.of(activeQrCode));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);
            when(ticketValidationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TicketValidation result = service.validateTicketByQrCode(validatorId, qrCodeId);

            // Validation record is VALID
            assertThat(result.getStatus()).isEqualTo(TicketValidationStatusEnum.VALID);
            assertThat(result.getValidationMethod()).isEqualTo(TicketValidationMethod.QR_SCAN);
            assertThat(result.getValidatedBy()).isEqualTo(validator);

            // Ticket.status transitions to VALIDATED (NEW FIX)
            ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(ticketCaptor.capture());
            assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatusEnum.VALIDATED);
        }

        @Test
        @DisplayName("QR code not found — throws QrCodeNotFoundException")
        void qrCodeNotFound_throwsException() {
            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateTicketByQrCode(validatorId, qrCodeId))
                    .isInstanceOf(QrCodeNotFoundException.class);
        }

        @Test
        @DisplayName("non-staff/organizer — throws AccessDeniedException")
        void nonStaff_throwsAccessDenied() {
            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE))
                    .thenReturn(Optional.of(activeQrCode));
            doThrow(new AccessDeniedException("Access denied"))
                    .when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);

            assertThatThrownBy(() -> service.validateTicketByQrCode(validatorId, qrCodeId))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("CANCELLED ticket via QR — throws InvalidBusinessStateException")
        void cancelledTicket_throwsException() {
            purchasedTicket.setStatus(TicketStatusEnum.CANCELLED);

            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE))
                    .thenReturn(Optional.of(activeQrCode));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);

            assertThatThrownBy(() -> service.validateTicketByQrCode(validatorId, qrCodeId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("cancelled");

            // Ticket must NOT be saved when validation is rejected
            verify(ticketRepository, never()).save(any());
        }
    }

    // ── validateTicketManually ────────────────────────────────────────────────

    @Nested
    @DisplayName("validateTicketManually")
    class ValidateManually {

        @Test
        @DisplayName("happy path — returns VALID and transitions ticket to VALIDATED")
        void happyPath_returnsValidAndTransitionsStatus() {
            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(purchasedTicket));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);
            when(ticketValidationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TicketValidation result = service.validateTicketManually(validatorId, ticketId);

            assertThat(result.getStatus()).isEqualTo(TicketValidationStatusEnum.VALID);
            assertThat(result.getValidationMethod()).isEqualTo(TicketValidationMethod.MANUAL);

            // Ticket.status transitions to VALIDATED
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatusEnum.VALIDATED);
        }

        @Test
        @DisplayName("ticket not found — throws TicketNotFoundException")
        void ticketNotFound_throwsException() {
            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.validateTicketManually(validatorId, ticketId))
                    .isInstanceOf(TicketNotFoundException.class);
        }

        @Test
        @DisplayName("CANCELLED ticket — throws InvalidBusinessStateException")
        void cancelledTicket_throwsException() {
            purchasedTicket.setStatus(TicketStatusEnum.CANCELLED);

            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(purchasedTicket));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);

            assertThatThrownBy(() -> service.validateTicketManually(validatorId, ticketId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("cancelled");

            verify(ticketRepository, never()).save(any());
        }
    }

    // ── DoubleValidationPrevention ────────────────────────────────────────────

    @Nested
    @DisplayName("DoubleValidationPrevention")
    class DoubleValidationPrevention {

        @Test
        @DisplayName("second scan when ticket.status = VALIDATED → throws immediately (fast path)")
        void alreadyValidatedTicket_throwsImmediately() {
            // Ticket is already VALIDATED (status written after first scan)
            purchasedTicket.setStatus(TicketStatusEnum.VALIDATED);

            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(purchasedTicket));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);

            assertThatThrownBy(() -> service.validateTicketManually(validatorId, ticketId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("already been validated");

            // ticketRepository.save never called — the guard fires before any DB writes
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("second scan on PURCHASED ticket with prior VALID validation → INVALID result, no status change")
        void purchasedWithPriorValidScan_producesInvalid() {
            // Ticket still shows PURCHASED (status hasn't been flushed to DB yet in concurrent scenario)
            // but already has a VALID validation in the list
            TicketValidation priorScan = new TicketValidation();
            priorScan.setId(UUID.randomUUID());
            priorScan.setStatus(TicketValidationStatusEnum.VALID);
            purchasedTicket.setValidations(new ArrayList<>(List.of(priorScan)));
            // status still PURCHASED (concurrent edge case)
            purchasedTicket.setStatus(TicketStatusEnum.PURCHASED);

            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(purchasedTicket));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);
            when(ticketValidationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TicketValidation result = service.validateTicketManually(validatorId, ticketId);

            // Result is INVALID — duplicate scan
            assertThat(result.getStatus()).isEqualTo(TicketValidationStatusEnum.INVALID);

            // Ticket.status NOT changed on duplicate scan
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("first scan creates VALID validation and writes VALIDATED status to ticket")
        void firstScan_createsValidAndWritesStatus() {
            // Clean ticket: PURCHASED status, no prior validations
            purchasedTicket.setValidations(new ArrayList<>());

            when(userRepository.findById(validatorId)).thenReturn(Optional.of(validator));
            when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(purchasedTicket));
            doNothing().when(authorizationService).requireOrganizerOrStaffAccess(validatorId, event);
            when(ticketValidationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(ticketRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TicketValidation result = service.validateTicketManually(validatorId, ticketId);

            assertThat(result.getStatus()).isEqualTo(TicketValidationStatusEnum.VALID);

            // ticketRepository.save called exactly once with VALIDATED status
            ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
            verify(ticketRepository, times(1)).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TicketStatusEnum.VALIDATED);
        }
    }
}