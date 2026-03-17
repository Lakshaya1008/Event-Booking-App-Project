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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketTypeServiceImpl")
class TicketTypeServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private AuthorizationService authorizationService;
    @Mock private DiscountService discountService;
    @Mock private EmailService emailService;

    @InjectMocks
    private TicketTypeServiceImpl service;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private UUID userId;
    private UUID ticketTypeId;
    private UUID eventId;
    private User user;
    private Event event;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        userId      = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        eventId     = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setName("Alice");
        user.setEmail("alice@test.com");
        user.setApprovalStatus(ApprovalStatus.APPROVED);

        event = new Event();
        event.setId(eventId);
        event.setName("Summer Fest");
        event.setStatus(EventStatusEnum.PUBLISHED);
        event.setMaxCapacity(null); // no venue cap by default

        ticketType = new TicketType();
        ticketType.setId(ticketTypeId);
        ticketType.setName("General Admission");
        ticketType.setPrice(new BigDecimal("50.00"));
        ticketType.setTotalAvailable(100);
        ticketType.setEvent(event);
        ticketType.setTickets(new ArrayList<>());
    }

    // ── purchaseTickets ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("purchaseTickets")
    class PurchaseTickets {

        @BeforeEach
        void mockHappyPath() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(ticketRepository.countByTicketTypeId(ticketTypeId)).thenReturn(0);
            when(discountService.findActiveDiscount(ticketTypeId)).thenReturn(Optional.empty());
            when(authorizationService.isOrganizer(userId, event)).thenReturn(false);
            when(ticketRepository.save(any())).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            doNothing().when(qrCodeService).generateQrCode(any());
            doNothing().when(emailService).sendTicketConfirmationEmail(any(), any(), any(), any(), anyInt(), any());
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
            // FIX #11: verify save is called ONCE per ticket, not twice
            verify(ticketRepository, never()).save(argThat(t -> t.getId() != null));
        }

        @Test
        @DisplayName("applies active discount correctly")
        void appliesDiscount() {
            Discount discount = new Discount();
            discount.setDiscountType(DiscountType.PERCENTAGE);
            discount.setValue(new BigDecimal("20")); // 20% off

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
        @DisplayName("throws TicketsSoldOutException when per-type limit exceeded")
        void throwsWhenTicketTypeSoldOut() {
            // 98 already sold, trying to buy 5 — totalAvailable is 100
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(ticketRepository.countByTicketTypeId(ticketTypeId)).thenReturn(98);

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 5))
                    .isInstanceOf(TicketsSoldOutException.class);
        }

        @Test
        @DisplayName("throws TicketsSoldOutException when event maxCapacity reached")
        void throwsWhenMaxCapacityReached() {
            event.setMaxCapacity(50);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(ticketRepository.countByTicketTypeId(ticketTypeId)).thenReturn(0);
            // FIX #8: uses countActiveTicketsByEventId excluding CANCELLED
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(49); // 49 sold, trying to buy 2

            assertThatThrownBy(() -> service.purchaseTickets(userId, ticketTypeId, 2))
                    .isInstanceOf(TicketsSoldOutException.class)
                    .hasMessageContaining("capacity");
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
    @DisplayName("updateTicketType — FIX #9")
    class UpdateTicketType {

        @Test
        @DisplayName("throws when totalAvailable reduced below sold count")
        void throwsWhenReducedBelowSoldCount() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            // 80 already sold
            when(ticketRepository.countByTicketTypeId(ticketTypeId)).thenReturn(80);

            com.event.tickets.domain.UpdateTicketTypeRequest request =
                    new com.event.tickets.domain.UpdateTicketTypeRequest();
            request.setId(ticketTypeId);
            request.setName("General");
            request.setPrice(new BigDecimal("50.00"));
            request.setTotalAvailable(70); // trying to set below 80 sold

            assertThatThrownBy(() ->
                    service.updateTicketType(userId, eventId, ticketTypeId, request))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("already sold");
        }

        @Test
        @DisplayName("allows update when new totalAvailable is equal to sold count")
        void allowsUpdateEqualToSoldCount() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            when(ticketRepository.countByTicketTypeId(ticketTypeId)).thenReturn(50);
            when(ticketTypeRepository.save(any())).thenReturn(ticketType);

            com.event.tickets.domain.UpdateTicketTypeRequest request =
                    new com.event.tickets.domain.UpdateTicketTypeRequest();
            request.setId(ticketTypeId);
            request.setName("General");
            request.setPrice(new BigDecimal("50.00"));
            request.setTotalAvailable(50); // exactly at sold count — allowed

            assertThatCode(() ->
                    service.updateTicketType(userId, eventId, ticketTypeId, request))
                    .doesNotThrowAnyException();
        }
    }

    // ── deleteTicketType ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteTicketType")
    class DeleteTicketType {

        @Test
        @DisplayName("throws when ticket type has sold tickets")
        void throwsWhenHasSoldTickets() {
            Ticket soldTicket = new Ticket();
            ticketType.setTickets(List.of(soldTicket));

            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() ->
                    service.deleteTicketType(userId, eventId, ticketTypeId))
                    .isInstanceOf(TicketTypeDeleteNotAllowedException.class);
        }

        @Test
        @DisplayName("deletes successfully when no tickets sold")
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
}