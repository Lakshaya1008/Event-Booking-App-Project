package com.event.tickets.services;

import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.TicketTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FIX SUMMARY — all 11 PurchaseTickets tests were failing with:
 *   "Only void methods can doNothing()!"
 *
 * ROOT CAUSE 1: QrCodeService.generateQrCode() returns QrCode (non-void).
 *   doNothing().when(qrCodeService).generateQrCode(any()) is illegal.
 *   Fix: use when(qrCodeService.generateQrCode(any())).thenReturn(new QrCode()) instead.
 *
 * ROOT CAUSE 2: The updated TicketTypeServiceImpl injects AuditLogService and
 *   SystemUserProvider (added for TICKET_PURCHASE_FAILED and TICKET_PURCHASED audit logging).
 *   These were not declared as @Mock fields, so Mockito left them null.
 *   When auditLogService.saveAuditLog() was called inside the audit helper methods
 *   it threw NullPointerException, which crashed every test in the Nested @BeforeEach.
 *
 * FIX ALSO: TicketTypeServiceImplTest.updateTicketType used countByTicketTypeId but
 *   the actual service now calls countActiveByTicketTypeId. Tests were stubbing the
 *   wrong method, so they always saw 0 sold and could not trigger the guard.
 *
 * FIX ALSO: deleteTicketType now checks only ACTIVE (non-cancelled) tickets.
 *   A ticket with no status set defaults to null, which means it's NOT cancelled,
 *   so it counts as active. Tests must explicitly set PURCHASED status.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TicketTypeServiceImpl")
class TicketTypeServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private AuthorizationService authorizationService;
    @Mock private DiscountService discountService;
    @Mock private EmailService emailService;
    // FIX: AuditLogService and SystemUserProvider were missing — caused NPE in every test
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    @InjectMocks
    private TicketTypeServiceImpl service;

    private UUID userId;
    private UUID ticketTypeId;
    private UUID eventId;
    private User user;
    private Event event;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        userId       = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        eventId      = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setName("Alice");
        user.setEmail("alice@test.com");
        user.setApprovalStatus(ApprovalStatus.APPROVED);

        event = new Event();
        event.setId(eventId);
        event.setName("Summer Fest");
        event.setStatus(EventStatusEnum.PUBLISHED);
        event.setMaxCapacity(null);

        ticketType = new TicketType();
        ticketType.setId(ticketTypeId);
        ticketType.setName("General Admission");
        ticketType.setPrice(new BigDecimal("50.00"));
        ticketType.setTotalAvailable(100);
        ticketType.setEvent(event);
        ticketType.setTickets(new ArrayList<>());

        // Prevent NPE in audit helpers — system user needed for failure audits
        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    // ── purchaseTickets ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("purchaseTickets")
    class PurchaseTickets {

        @BeforeEach
        void mockHappyPath() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            // FIX: service now calls countActiveByTicketTypeId, NOT countByTicketTypeId
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(discountService.findActiveDiscount(ticketTypeId)).thenReturn(Optional.empty());
            when(authorizationService.isOrganizer(userId, event)).thenReturn(false);
            when(ticketRepository.save(any())).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            // FIX: generateQrCode returns QrCode (non-void) — must use thenReturn, NOT doNothing()
            QrCode mockQrCode = new QrCode();
            mockQrCode.setId(UUID.randomUUID());
            when(qrCodeService.generateQrCode(any())).thenReturn(mockQrCode);
        }

        @Test
        @DisplayName("happy path — creates tickets with correct pricing")
        void happyPath_createsTwoTickets() {
            List<Ticket> result = service.purchaseTickets(userId, ticketTypeId, 2);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPricePaid()).isEqualByComparingTo("50.00");
            assertThat(result.get(0).getOriginalPrice()).isEqualByComparingTo("50.00");
            assertThat(result.get(0).getDiscountApplied()).isEqualByComparingTo("0.00");
            verify(ticketRepository, times(2)).save(any(Ticket.class));
            verify(qrCodeService, times(2)).generateQrCode(any());
        }

        @Test
        @DisplayName("applies active discount — pricePaid and discountApplied set correctly")
        void appliesDiscount() {
            Discount discount = new Discount();
            discount.setDiscountType(DiscountType.PERCENTAGE);
            discount.setValue(new BigDecimal("20"));

            when(discountService.findActiveDiscount(ticketTypeId)).thenReturn(Optional.of(discount));
            when(discountService.calculateFinalPrice(new BigDecimal("50.00"), discount))
                    .thenReturn(new BigDecimal("40.00"));

            List<Ticket> result = service.purchaseTickets(userId, ticketTypeId, 1);

            assertThat(result.get(0).getPricePaid()).isEqualByComparingTo("40.00");
            assertThat(result.get(0).getDiscountApplied()).isEqualByComparingTo("10.00");
            assertThat(result.get(0).getOriginalPrice()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("throws UserNotFoundException when user not found")
        void throwsWhenUserNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 1))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("throws TicketTypeNotFoundException when ticket type not found")
        void throwsWhenTicketTypeNotFound() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 1))
                    .isInstanceOf(TicketTypeNotFoundException.class);
        }

        @Test
        @DisplayName("throws InvalidBusinessStateException when event is CANCELLED")
        void throwsWhenEventCancelled() {
            event.setStatus(EventStatusEnum.CANCELLED);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("cancelled");
        }

        @Test
        @DisplayName("throws InvalidBusinessStateException when event is DRAFT")
        void throwsWhenEventNotPublished() {
            event.setStatus(EventStatusEnum.DRAFT);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class);
        }

        @Test
        @DisplayName("throws when sales have not started yet")
        void throwsWhenSalesNotStarted() {
            event.setSalesStart(LocalDateTime.now().plusDays(1));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("not started");
        }

        @Test
        @DisplayName("throws when sales have ended")
        void throwsWhenSalesEnded() {
            event.setSalesEnd(LocalDateTime.now().minusDays(1));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("closed");
        }

        @Test
        @DisplayName("H-06 FIX — throws TicketsSoldOutException when per-type ACTIVE limit exceeded")
        void throwsWhenTicketTypeSoldOut() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            // FIX: service uses countActiveByTicketTypeId — must stub THIS method, not countByTicketTypeId
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(98); // 98 active sold, totalAvailable=100, buying 5 → exceeds

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 5))
                    .isInstanceOf(TicketsSoldOutException.class);
        }

        @Test
        @DisplayName("H-06 FIX — cancelled tickets free up slots (cancelled count not blocking)")
        void cancelledSlotsAreFreed() {
            // 50 sold, 20 cancelled → 30 active, totalAvailable=100, buying 5 should succeed
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(30); // only 30 active despite 50 ever sold

            List<Ticket> result = service.purchaseTickets(userId, ticketTypeId, 5);

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("throws TicketsSoldOutException when event maxCapacity reached")
        void throwsWhenMaxCapacityReached() {
            event.setMaxCapacity(50);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(49); // 49 active across all types, buying 2 → exceeds cap of 50

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 2))
                    .isInstanceOf(TicketsSoldOutException.class)
                    .hasMessageContaining("capacity");
        }

        @Test
        @DisplayName("null totalAvailable means unlimited — never throws SOLD_OUT for ticket type")
        void nullTotalAvailableMeansUnlimited() {
            ticketType.setTotalAvailable(null); // unlimited
            // Even with 999 active — should not throw for ticket-type sold-out
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            // countActiveByTicketTypeId not called since totalAvailable is null
            // (no cap check performed)

            List<Ticket> result = service.purchaseTickets(userId, ticketTypeId, 1);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("sends confirmation email after purchase")
        void sendsConfirmationEmail() {
            service.purchaseTickets(userId, ticketTypeId, 1);

            verify(emailService).sendTicketConfirmationEmail(
                    eq("alice@test.com"), eq("Alice"),
                    eq("Summer Fest"), eq("General Admission"),
                    eq(1), any(UUID.class));
        }
    }

    // ── updateTicketType ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTicketType — H-07 FIX uses countActiveByTicketTypeId")
    class UpdateTicketType {

        @Test
        @DisplayName("H-07 FIX — throws when totalAvailable reduced below ACTIVE sold count")
        void throwsWhenReducedBelowActiveSoldCount() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            // FIX: service calls countActiveByTicketTypeId, NOT countByTicketTypeId
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(80);

            com.event.tickets.domain.UpdateTicketTypeRequest request =
                    new com.event.tickets.domain.UpdateTicketTypeRequest();
            request.setId(ticketTypeId);
            request.setName("General");
            request.setPrice(new BigDecimal("50.00"));
            request.setTotalAvailable(70); // below 80 active — should fail

            assertThatThrownBy(() ->
                    service.updateTicketType(userId, eventId, ticketTypeId, request))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("active (non-cancelled) ticket(s) already sold");
        }

        @Test
        @DisplayName("H-07 FIX — allows raising totalAvailable even when cancelled count is high")
        void allowsRaiseWhenCancelledCountIsHigh() {
            // 100 ever sold, 70 cancelled → 30 active. Can set totalAvailable to any value >= 30
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(30); // only 30 truly active
            when(ticketTypeRepository.save(any())).thenReturn(ticketType);

            com.event.tickets.domain.UpdateTicketTypeRequest request =
                    new com.event.tickets.domain.UpdateTicketTypeRequest();
            request.setId(ticketTypeId);
            request.setName("General");
            request.setPrice(new BigDecimal("50.00"));
            request.setTotalAvailable(50); // 50 > 30 active — allowed

            assertThatCode(() ->
                    service.updateTicketType(userId, eventId, ticketTypeId, request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("allows update when new totalAvailable equals active sold count (boundary)")
        void allowsUpdateEqualToActiveSoldCount() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(50);
            when(ticketTypeRepository.save(any())).thenReturn(ticketType);

            com.event.tickets.domain.UpdateTicketTypeRequest request =
                    new com.event.tickets.domain.UpdateTicketTypeRequest();
            request.setId(ticketTypeId);
            request.setName("General");
            request.setPrice(new BigDecimal("50.00"));
            request.setTotalAvailable(50); // exactly at active count — allowed (no more sales)

            assertThatCode(() ->
                    service.updateTicketType(userId, eventId, ticketTypeId, request))
                    .doesNotThrowAnyException();
        }
    }

    // ── deleteTicketType ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTicketType — blocks on active tickets, allows when all cancelled")
    class DeleteTicketType {

        @Test
        @DisplayName("throws when ticket type has PURCHASED tickets")
        void throwsWhenHasActiveSoldTickets() {
            Ticket soldTicket = new Ticket();
            soldTicket.setId(UUID.randomUUID());
            soldTicket.setStatus(TicketStatusEnum.PURCHASED); // explicitly set — NOT null, NOT cancelled
            ticketType.setTickets(List.of(soldTicket));

            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() ->
                    service.deleteTicketType(userId, eventId, ticketTypeId))
                    .isInstanceOf(TicketTypeDeleteNotAllowedException.class)
                    .hasMessageContaining("active (non-cancelled)");
        }

        @Test
        @DisplayName("allows deletion when all tickets are CANCELLED")
        void allowsDeletionWhenAllCancelled() {
            // FIX: the previous test had a ticket with null status — null != CANCELLED so it counted.
            // Now we test with an explicitly CANCELLED ticket — should allow deletion.
            Ticket cancelledTicket = new Ticket();
            cancelledTicket.setId(UUID.randomUUID());
            cancelledTicket.setStatus(TicketStatusEnum.CANCELLED);
            ticketType.setTickets(List.of(cancelledTicket));

            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));

            assertThatCode(() ->
                    service.deleteTicketType(userId, eventId, ticketTypeId))
                    .doesNotThrowAnyException();
            verify(ticketTypeRepository).delete(ticketType);
        }

        @Test
        @DisplayName("deletes successfully when no tickets exist at all")
        void deletesWhenNoTickets() {
            ticketType.setTickets(new ArrayList<>());
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));

            assertThatCode(() ->
                    service.deleteTicketType(userId, eventId, ticketTypeId))
                    .doesNotThrowAnyException();
            verify(ticketTypeRepository).delete(ticketType);
        }
    }

    // ── FIX #5-2 — Maximum quantity validation (max 10 per purchase) ──────────

    @Nested
    @DisplayName("FIX #5-2 — Maximum quantity validation (max 10)")
    class MaxQuantityValidation {

        @BeforeEach
        void mockQrCode() {
            when(ticketRepository.save(any())).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            when(qrCodeService.generateQrCode(any()))
                    .thenAnswer(inv -> {
                        QrCode qr = new QrCode();
                        qr.setId(UUID.randomUUID());
                        qr.setValue("qr-value");
                        qr.setStatus(QrCodeStatusEnum.ACTIVE);
                        return qr;
                    });
        }

        @Test
        @DisplayName("quantity 1-10 is accepted")
        void quantity_1_to_10_accepted() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            for (int qty = 1; qty <= 10; qty++) {
                List<Ticket> tickets = service.purchaseTickets(userId, ticketTypeId, qty);
                assertThat(tickets).hasSize(qty);
            }
        }

        @Test
        @DisplayName("quantity exactly 10 is accepted")
        void quantity_10_exactly_accepted() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            List<Ticket> tickets = service.purchaseTickets(userId, ticketTypeId, 10);

            assertThat(tickets).hasSize(10);
        }

        @Test
        @DisplayName("quantity 11 is rejected")
        void quantity_11_rejected() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 11))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("between 1 and 10")
                    .hasMessageContaining("Cannot purchase 11")
                    .hasMessageContaining("10");
        }

        @Test
        @DisplayName("quantity 0 is rejected")
        void quantity_0_rejected() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 0))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("between 1 and 10");
        }

        @Test
        @DisplayName("quantity negative is rejected")
        void quantity_negative_rejected() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, -5))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("between 1 and 10");
        }

        @Test
        @DisplayName("quantity 100+ is rejected")
        void quantity_large_rejected() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 999))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("between 1 and 10");
        }

        @Test
        @DisplayName("boundary: 9 is accepted, 10 is accepted, 11 is rejected")
        void boundary_testing() {
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // 9 is OK
            List<Ticket> t9 = service.purchaseTickets(userId, ticketTypeId, 9);
            assertThat(t9).hasSize(9);

            // 10 is OK
            List<Ticket> t10 = service.purchaseTickets(userId, ticketTypeId, 10);
            assertThat(t10).hasSize(10);

            // 11 is NOT OK
            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 11))
                    .isInstanceOf(InvalidBusinessStateException.class);
        }
    }
}


