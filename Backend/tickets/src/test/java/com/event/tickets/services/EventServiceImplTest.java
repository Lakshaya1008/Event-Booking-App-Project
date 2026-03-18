package com.event.tickets.services;

import com.event.tickets.domain.UpdateEventRequest;
import com.event.tickets.domain.UpdateTicketTypeRequest;
import com.event.tickets.domain.entities.*;
import com.event.tickets.exceptions.*;
import com.event.tickets.repositories.*;
import com.event.tickets.services.impl.EventServiceImpl;
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
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EventServiceImpl unit tests.
 *
 * CHANGES FROM PREVIOUS VERSION:
 *  - Date validation tests updated for isCreate=true/false split.
 *    The new contract: future-date rules fire on CREATE only.
 *    UPDATE only enforces ordering (end > start etc.).
 *  - Added tests that confirm UPDATE does NOT block a live event
 *    (past salesStart) from being edited.
 *  - AuditLogService and SystemUserProvider mocks were already present.
 *  - TicketStatusEnum.VALIDATED references removed (doesn't exist in enum).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventServiceImpl")
class EventServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private TicketRepository ticketRepository;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    @InjectMocks
    private EventServiceImpl service;

    private UUID organizerId;
    private UUID eventId;
    private User organizer;
    private Event event;
    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        organizerId = UUID.randomUUID();
        eventId     = UUID.randomUUID();

        organizer = new User();
        organizer.setId(organizerId);
        organizer.setName("Carol");
        organizer.setEmail("carol@test.com");

        ticketType = new TicketType();
        ticketType.setId(UUID.randomUUID());
        ticketType.setName("VIP");
        ticketType.setPrice(new BigDecimal("200.00"));
        ticketType.setTotalAvailable(50);
        ticketType.setTickets(new ArrayList<>());

        event = new Event();
        event.setId(eventId);
        event.setName("Winter Gala");
        event.setVenue("Grand Hall");
        event.setStatus(EventStatusEnum.PUBLISHED);
        event.setOrganizer(organizer);
        event.setTicketTypes(new ArrayList<>(List.of(ticketType)));
        ticketType.setEvent(event);

        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    // ── deleteEventForOrganizer ───────────────────────────────────────────────

    @Nested
    @DisplayName("deleteEventForOrganizer")
    class DeleteEvent {

        @Test
        @DisplayName("blocks deletion when active tickets exist")
        void blocksWhenActiveTicketsExist() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(5);

            assertThatThrownBy(() -> service.deleteEventForOrganizer(organizerId, eventId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("active ticket");
        }

        @Test
        @DisplayName("allows deletion when all tickets are CANCELLED")
        void allowsWhenAllTicketsCancelled() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatCode(() -> service.deleteEventForOrganizer(organizerId, eventId))
                    .doesNotThrowAnyException();
            verify(eventRepository).delete(event);
        }
    }

    // ── updateEventForOrganizer ───────────────────────────────────────────────

    @Nested
    @DisplayName("updateEventForOrganizer")
    class UpdateEvent {

        private UpdateEventRequest buildRequest() {
            UpdateEventRequest req = new UpdateEventRequest();
            req.setId(eventId);
            req.setName("Winter Gala Updated");
            req.setVenue("Grand Hall");
            req.setStatus(EventStatusEnum.PUBLISHED);
            req.setMaxCapacity(null);
            UpdateTicketTypeRequest tt = new UpdateTicketTypeRequest();
            tt.setId(ticketType.getId());
            tt.setName("VIP");
            tt.setPrice(new BigDecimal("200.00"));
            tt.setTotalAvailable(50);
            req.setTicketTypes(List.of(tt));
            return req;
        }

        @Test
        @DisplayName("blocks removing ticket type that has active sold tickets")
        void blocksRemovingTicketTypeWithActiveSoldTickets() {
            Ticket soldTicket = new Ticket();
            soldTicket.setId(UUID.randomUUID());
            soldTicket.setStatus(TicketStatusEnum.PURCHASED);
            ticketType.setTickets(new ArrayList<>(List.of(soldTicket)));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);

            UpdateEventRequest req = new UpdateEventRequest();
            req.setId(eventId);
            req.setName("Winter Gala Updated");
            req.setVenue("Grand Hall");
            req.setStatus(EventStatusEnum.PUBLISHED);
            req.setTicketTypes(List.of());

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Cannot remove ticket type");
        }

        @Test
        @DisplayName("allows removing ticket type when all its tickets are CANCELLED")
        void allowsRemovingCancelledOnlyTicketType() {
            Ticket cancelledTicket = new Ticket();
            cancelledTicket.setId(UUID.randomUUID());
            cancelledTicket.setStatus(TicketStatusEnum.CANCELLED);
            ticketType.setTickets(new ArrayList<>(List.of(cancelledTicket)));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = new UpdateEventRequest();
            req.setId(eventId);
            req.setName("Winter Gala Updated");
            req.setVenue("Grand Hall");
            req.setStatus(EventStatusEnum.PUBLISHED);
            req.setTicketTypes(List.of());

            assertThatCode(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("blocks reducing maxCapacity below active sold count")
        void blocksReducingMaxCapacityBelowSoldCount() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(80);

            UpdateEventRequest req = buildRequest();
            req.setMaxCapacity(50);

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("maxCapacity");
        }

        @Test
        @DisplayName("bulk cancels PURCHASED tickets and sends emails when cancelling event")
        void cancelsBulkAndSendsEmails() {
            User purchaser = new User();
            purchaser.setId(UUID.randomUUID());
            purchaser.setEmail("buyer@test.com");
            purchaser.setName("Dave");

            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setStatus(TicketStatusEnum.PURCHASED);
            ticket.setPurchaser(purchaser);
            ticket.setTicketType(ticketType);
            ticketType.setTickets(List.of(ticket));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(ticketRepository.bulkUpdateStatusByEventId(any(), any(), any())).thenReturn(1);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = buildRequest();
            req.setStatus(EventStatusEnum.CANCELLED);

            service.updateEventForOrganizer(organizerId, eventId, req);

            verify(emailService).sendEventCancellationEmail("buyer@test.com", "Dave", "Winter Gala");
        }

        @Test
        @DisplayName("sends one cancellation email per unique purchaser even with multiple tickets")
        void deduplicatesCancellationEmails() {
            User purchaser = new User();
            purchaser.setId(UUID.randomUUID());
            purchaser.setEmail("buyer@test.com");
            purchaser.setName("Dave");

            List<Ticket> tickets = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Ticket t = new Ticket();
                t.setId(UUID.randomUUID());
                t.setStatus(TicketStatusEnum.PURCHASED);
                t.setPurchaser(purchaser);
                t.setTicketType(ticketType);
                tickets.add(t);
            }
            ticketType.setTickets(tickets);

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(ticketRepository.bulkUpdateStatusByEventId(any(), any(), any())).thenReturn(3);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = buildRequest();
            req.setStatus(EventStatusEnum.CANCELLED);

            service.updateEventForOrganizer(organizerId, eventId, req);

            verify(emailService, times(1)).sendEventCancellationEmail(
                    "buyer@test.com", "Dave", "Winter Gala");
        }

        @Test
        @DisplayName("throws EventUpdateException when body ID doesn't match URL eventId")
        void throwsWhenIdMismatch() {
            UpdateEventRequest req = buildRequest();
            req.setId(UUID.randomUUID());

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(EventUpdateException.class)
                    .hasMessageContaining("Cannot update the ID");
        }

        @Test
        @DisplayName("throws EventUpdateException when body ID is null")
        void throwsWhenIdNull() {
            UpdateEventRequest req = buildRequest();
            req.setId(null);

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(EventUpdateException.class)
                    .hasMessageContaining("cannot be null");
        }
    }

    // ── getSalesDashboard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSalesDashboard")
    class SalesDashboard {

        @Test
        @DisplayName("returns BigDecimal revenue totals")
        void returnsBigDecimalRevenue() {
            User buyer = new User();
            buyer.setId(UUID.randomUUID());

            Ticket t = new Ticket();
            t.setId(UUID.randomUUID());
            t.setStatus(TicketStatusEnum.PURCHASED);
            t.setPurchaser(buyer);
            t.setOriginalPrice(new BigDecimal("100.00"));
            t.setPricePaid(new BigDecimal("80.00"));
            t.setDiscountApplied(new BigDecimal("20.00"));
            ticketType.setTickets(List.of(t));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            assertThat(dashboard.get("totalTicketsSold")).isEqualTo(1);
            assertThat((BigDecimal) dashboard.get("totalRevenueFinal"))
                    .isEqualByComparingTo("80.00");
        }

        @Test
        @DisplayName("CANCELLED tickets excluded from sold count and revenue")
        void excludesCancelledFromRevenue() {
            User buyer = new User();
            buyer.setId(UUID.randomUUID());

            Ticket purchased = new Ticket();
            purchased.setId(UUID.randomUUID());
            purchased.setStatus(TicketStatusEnum.PURCHASED);
            purchased.setPurchaser(buyer);
            purchased.setOriginalPrice(new BigDecimal("100.00"));
            purchased.setPricePaid(new BigDecimal("100.00"));
            purchased.setDiscountApplied(BigDecimal.ZERO);

            Ticket cancelled = new Ticket();
            cancelled.setId(UUID.randomUUID());
            cancelled.setStatus(TicketStatusEnum.CANCELLED);
            cancelled.setPurchaser(buyer);
            cancelled.setOriginalPrice(new BigDecimal("100.00"));
            cancelled.setPricePaid(new BigDecimal("100.00"));
            cancelled.setDiscountApplied(BigDecimal.ZERO);

            ticketType.setTickets(List.of(purchased, cancelled));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            assertThat(dashboard.get("totalTicketsSold")).isEqualTo(1);
            assertThat((BigDecimal) dashboard.get("totalRevenueFinal"))
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("remaining is null when totalAvailable is null (unlimited)")
        void returnsNullRemainingForUnlimited() {
            ticketType.setTotalAvailable(null);
            ticketType.setTickets(new ArrayList<>());

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> breakdown =
                    (List<Map<String, Object>>) dashboard.get("ticketTypeBreakdown");
            assertThat(breakdown).hasSize(1);
            assertThat(breakdown.get(0).get("remaining")).isNull();
        }
    }

    // ── Date validation — CREATE enforces future dates ────────────────────────

    @Nested
    @DisplayName("Date validation on CREATE — future dates enforced")
    class CreateDateValidation {

        @Test
        @DisplayName("past event start date is rejected on create")
        void pastEventStart_rejectedOnCreate() {
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            com.event.tickets.domain.CreateEventRequest req = buildCreateRequest();
            req.setStart(LocalDateTime.now().minusDays(1));  // PAST
            req.setEnd(LocalDateTime.now().plusDays(1));

            assertThatThrownBy(() -> service.createEvent(organizerId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("future");
        }

        @Test
        @DisplayName("past sales start date is rejected on create")
        void pastSalesStart_rejectedOnCreate() {
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            com.event.tickets.domain.CreateEventRequest req = buildCreateRequest();
            req.setSalesStart(LocalDateTime.now().minusHours(1));  // PAST
            req.setSalesEnd(LocalDateTime.now().plusHours(1));
            req.setStart(LocalDateTime.now().plusDays(1));
            req.setEnd(LocalDateTime.now().plusDays(2));

            assertThatThrownBy(() -> service.createEvent(organizerId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("future");
        }

        private com.event.tickets.domain.CreateEventRequest buildCreateRequest() {
            com.event.tickets.domain.CreateEventRequest req = new com.event.tickets.domain.CreateEventRequest();
            req.setName("New Event");
            req.setVenue("Grand Hall");
            req.setStatus(EventStatusEnum.DRAFT);
            com.event.tickets.domain.CreateTicketTypeRequest tt = new com.event.tickets.domain.CreateTicketTypeRequest();
            tt.setName("General");
            tt.setPrice(new BigDecimal("50.00"));
            tt.setTotalAvailable(100);
            req.setTicketTypes(List.of(tt));
            return req;
        }
    }

    // ── Date validation — UPDATE does NOT enforce future dates ────────────────

    @Nested
    @DisplayName("Date validation on UPDATE — past dates allowed (live event fix)")
    class UpdateDateValidation {

        private UpdateEventRequest buildRequest() {
            UpdateEventRequest req = new UpdateEventRequest();
            req.setId(eventId);
            req.setName("Test Event");
            req.setVenue("Test Venue");
            req.setStatus(EventStatusEnum.PUBLISHED);
            UpdateTicketTypeRequest tt = new UpdateTicketTypeRequest();
            tt.setId(ticketType.getId());
            tt.setName("General");
            tt.setPrice(new BigDecimal("100.00"));
            tt.setTotalAvailable(100);
            req.setTicketTypes(List.of(tt));
            return req;
        }

        @Test
        @DisplayName("UPDATE with past salesStart is allowed — live event can be edited")
        void pastSalesStart_allowedOnUpdate() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = buildRequest();
            // salesStart in the past — this is a LIVE event being managed
            req.setSalesStart(LocalDateTime.now().minusDays(3));
            req.setSalesEnd(LocalDateTime.now().plusDays(5));
            req.setStart(LocalDateTime.now().plusDays(7));
            req.setEnd(LocalDateTime.now().plusDays(8));

            // Must NOT throw — organizer is editing venue on a live event
            assertThatCode(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("UPDATE still enforces ordering: event end > event start")
        void ordering_stillEnforced_onUpdate() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);

            UpdateEventRequest req = buildRequest();
            req.setStart(LocalDateTime.now().plusDays(5));
            req.setEnd(LocalDateTime.now().plusDays(2));  // END before START — invalid

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("after");
        }

        @Test
        @DisplayName("UPDATE still enforces ordering: salesEnd > salesStart")
        void salesOrdering_stillEnforced_onUpdate() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);

            UpdateEventRequest req = buildRequest();
            req.setSalesStart(LocalDateTime.now().plusHours(5));
            req.setSalesEnd(LocalDateTime.now().plusHours(2));  // before salesStart

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("after");
        }
    }
}