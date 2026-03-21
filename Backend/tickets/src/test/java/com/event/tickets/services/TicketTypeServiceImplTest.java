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
 * CHANGES FROM PREVIOUS VERSION:
 *
 * TEST-TT1 — purchaseTickets tests now call 4-arg overload (userId, eventId, ticketTypeId, qty).
 *   The 3-arg overload no longer exists on the interface (FIX-TT3 / BUG 5-1).
 *   @Mock EventRepository added — needed by the 4-arg overload.
 *
 * TEST-TT2 — createTicketType: new tests for CANCELLED/COMPLETED event guard (FIX-TT1 / BUG 4-1).
 *
 * TEST-TT3 — deleteTicketType: now tests countActiveByTicketTypeId (COUNT query) not
 *   collection iteration. Removed the old collection-based stub (FIX-TT2 / BUG 4-2).
 *
 * TEST-TT4 — purchaseTickets happy path updated: event now mocked via eventRepository.findById().
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TicketTypeServiceImpl")
class TicketTypeServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;    // TEST-TT1: new — needed by 4-arg overload
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private AuthorizationService authorizationService;
    @Mock private DiscountService discountService;
    @Mock private EmailService emailService;
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

        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    // ── purchaseTickets (4-arg only now) ─────────────────────────────────────

    @Nested
    @DisplayName("purchaseTickets — 4-arg (eventId required)")
    class PurchaseTickets {

        @BeforeEach
        void mockHappyPath() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));          // TEST-TT1
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED)).thenReturn(0);
            when(discountService.findActiveDiscount(ticketTypeId)).thenReturn(Optional.empty());
            when(authorizationService.isOrganizer(userId, event)).thenReturn(false);
            when(ticketRepository.save(any())).thenAnswer(inv -> {
                Ticket t = inv.getArgument(0);
                t.setId(UUID.randomUUID());
                return t;
            });
            QrCode mockQrCode = new QrCode();
            mockQrCode.setId(UUID.randomUUID());
            when(qrCodeService.generateQrCode(any())).thenReturn(mockQrCode);
        }

        @Test
        @DisplayName("happy path — creates tickets with correct pricing")
        void happyPath_createsTwoTickets() {
            List<Ticket> result = service.purchaseTickets(userId, eventId, ticketTypeId, 2);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getPricePaid()).isEqualByComparingTo("50.00");
            assertThat(result.get(0).getOriginalPrice()).isEqualByComparingTo("50.00");
            assertThat(result.get(0).getDiscountApplied()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("throws when ticket type does not belong to the given event (cross-event guard)")
        void throwsWhenCrossEventPurchase() {
            UUID differentEventId = UUID.randomUUID();
            Event differentEvent = new Event();
            differentEvent.setId(differentEventId);
            // ticket type belongs to eventId, but caller sends differentEventId
            when(eventRepository.findById(differentEventId)).thenReturn(Optional.of(differentEvent));
            when(ticketTypeRepository.findByIdWithLock(ticketTypeId)).thenReturn(Optional.of(ticketType));

            assertThatThrownBy(() -> service.purchaseTickets(userId, differentEventId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("does not belong to the specified event");
        }

        @Test
        @DisplayName("throws when event is CANCELLED")
        void throwsWhenEventCancelled() {
            event.setStatus(EventStatusEnum.CANCELLED);

            assertThatThrownBy(() -> service.purchaseTickets(userId, eventId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("cancelled");
        }

        @Test
        @DisplayName("throws when sales have not started")
        void throwsWhenSalesNotStarted() {
            event.setSalesStart(LocalDateTime.now().plusDays(1));

            assertThatThrownBy(() -> service.purchaseTickets(userId, eventId, ticketTypeId, 1))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("not started");
        }

        @Test
        @DisplayName("throws when ticket type sold out (active count check)")
        void throwsWhenTicketTypeSoldOut() {
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(98);

            assertThatThrownBy(() -> service.purchaseTickets(userId, eventId, ticketTypeId, 5))
                    .isInstanceOf(TicketsSoldOutException.class);
        }

        @Test
        @DisplayName("quantity 11 is rejected before any DB call")
        void quantity11Rejected() {
            assertThatThrownBy(() -> service.purchaseTickets(userId, eventId, ticketTypeId, 11))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("between 1 and 10");

            // No DB calls made — fast fail
            verify(userRepository, never()).findById(any());
        }
    }

    // ── createTicketType — status guard ──────────────────────────────────────

    @Nested
    @DisplayName("createTicketType — FIX-TT1 event status guard")
    class CreateTicketType {

        @Test
        @DisplayName("CANCELLED event — throws InvalidBusinessStateException")
        void cancelledEvent_blocked() {
            event.setStatus(EventStatusEnum.CANCELLED);
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            com.event.tickets.domain.CreateTicketTypeRequest req = new com.event.tickets.domain.CreateTicketTypeRequest();
            req.setName("VIP");
            req.setPrice(new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.createTicketType(userId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("COMPLETED event — throws InvalidBusinessStateException")
        void completedEvent_blocked() {
            event.setStatus(EventStatusEnum.COMPLETED);
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            com.event.tickets.domain.CreateTicketTypeRequest req = new com.event.tickets.domain.CreateTicketTypeRequest();
            req.setName("VIP");
            req.setPrice(new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.createTicketType(userId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("DRAFT event — ticket type creation allowed")
        void draftEvent_allowed() {
            event.setStatus(EventStatusEnum.DRAFT);
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketTypeRepository.save(any())).thenReturn(ticketType);

            com.event.tickets.domain.CreateTicketTypeRequest req = new com.event.tickets.domain.CreateTicketTypeRequest();
            req.setName("General");
            req.setPrice(new BigDecimal("50.00"));

            assertThatCode(() -> service.createTicketType(userId, eventId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PUBLISHED event — ticket type creation allowed")
        void publishedEvent_allowed() {
            event.setStatus(EventStatusEnum.PUBLISHED);
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketTypeRepository.save(any())).thenReturn(ticketType);

            com.event.tickets.domain.CreateTicketTypeRequest req = new com.event.tickets.domain.CreateTicketTypeRequest();
            req.setName("General");
            req.setPrice(new BigDecimal("50.00"));

            assertThatCode(() -> service.createTicketType(userId, eventId, req))
                    .doesNotThrowAnyException();
        }
    }

    // ── deleteTicketType — COUNT query ────────────────────────────────────────

    @Nested
    @DisplayName("deleteTicketType — FIX-TT2 COUNT query, not collection load")
    class DeleteTicketType {

        @Test
        @DisplayName("throws when active tickets exist — uses countActiveByTicketTypeId")
        void throwsWhenActiveTicketsExist() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            // FIX-TT2: service now calls countActiveByTicketTypeId, not collection iteration
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(3);

            assertThatThrownBy(() -> service.deleteTicketType(userId, eventId, ticketTypeId))
                    .isInstanceOf(TicketTypeDeleteNotAllowedException.class)
                    .hasMessageContaining("active (non-cancelled)");
        }

        @Test
        @DisplayName("allows deletion when countActiveByTicketTypeId returns 0")
        void allowsDeletionWhenNoActiveTickets() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);

            assertThatCode(() -> service.deleteTicketType(userId, eventId, ticketTypeId))
                    .doesNotThrowAnyException();
            verify(ticketTypeRepository).delete(ticketType);
        }
    }

    // ── updateTicketType ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateTicketType — totalAvailable guard")
    class UpdateTicketType {

        @Test
        @DisplayName("throws when totalAvailable reduced below active sold count")
        void throwsWhenReducedBelowActiveSold() {
            doNothing().when(authorizationService).requireOrganizerAccess(userId, eventId);
            when(ticketTypeRepository.findByIdAndEventId(ticketTypeId, eventId))
                    .thenReturn(Optional.of(ticketType));
            when(ticketRepository.countActiveByTicketTypeId(ticketTypeId, TicketStatusEnum.CANCELLED))
                    .thenReturn(80);

            com.event.tickets.domain.UpdateTicketTypeRequest req = new com.event.tickets.domain.UpdateTicketTypeRequest();
            req.setId(ticketTypeId);
            req.setName("General");
            req.setPrice(new BigDecimal("50.00"));
            req.setTotalAvailable(70);

            assertThatThrownBy(() -> service.updateTicketType(userId, eventId, ticketTypeId, req))
                    .isInstanceOf(InvalidBusinessStateException.class);
        }
    }
}