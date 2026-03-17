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
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventServiceImpl")
class EventServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EventRepository eventRepository;
    @Mock private AuthorizationService authorizationService;
    @Mock private TicketRepository ticketRepository;
    @Mock private EmailService emailService;

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
    }

    // ── deleteEventForOrganizer ───────────────────────────────────────────────

    @Nested
    @DisplayName("deleteEventForOrganizer — FIX #8")
    class DeleteEvent {

        @Test
        @DisplayName("blocks deletion when active (non-cancelled) tickets exist")
        void blocksWhenActiveTicketsExist() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            // FIX #8: uses countActiveTicketsByEventId, not countByTicketTypeEventId
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(5);

            assertThatThrownBy(() -> service.deleteEventForOrganizer(organizerId, eventId))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("active ticket");
        }

        @Test
        @DisplayName("allows deletion when all tickets are CANCELLED (FIX #8 — was previously blocked)")
        void allowsWhenAllTicketsCancelled() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            // FIX #8: 0 active tickets (all are CANCELLED) — should allow deletion
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
        @DisplayName("FIX #3 — blocks removing ticket type that has sold tickets")
        void blocksRemovingTicketTypeWithSoldTickets() {
            // Add a sold ticket to the type
            Ticket soldTicket = new Ticket();
            soldTicket.setId(UUID.randomUUID());
            ticketType.setTickets(new ArrayList<>(List.of(soldTicket)));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countByTicketTypeEventId(eventId)).thenReturn(1);

            // Request omits the ticketType — trying to remove it
            UpdateEventRequest req = new UpdateEventRequest();
            req.setId(eventId);
            req.setName("Winter Gala Updated");
            req.setVenue("Grand Hall");
            req.setStatus(EventStatusEnum.PUBLISHED);
            req.setTicketTypes(List.of()); // empty — trying to remove the type

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("Cannot remove ticket type");
        }

        @Test
        @DisplayName("FIX #16 — blocks reducing maxCapacity below active sold count")
        void blocksReducingMaxCapacityBelowSoldCount() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countByTicketTypeEventId(eventId)).thenReturn(0);
            // FIX #16: uses countActiveTicketsByEventId
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(80); // 80 non-cancelled tickets sold

            UpdateEventRequest req = buildRequest();
            req.setMaxCapacity(50); // trying to reduce to 50 when 80 sold

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("maxCapacity");
        }

        @Test
        @DisplayName("bulk cancels tickets and sends emails when event transitions to CANCELLED")
        void cancelsBulkAndSendsEmails() {
            // Set up a purchased ticket with a purchaser
            User purchaser = new User();
            purchaser.setId(UUID.randomUUID());
            purchaser.setEmail("buyer@test.com");
            purchaser.setName("Dave");

            Ticket ticket = new Ticket();
            ticket.setId(UUID.randomUUID());
            ticket.setPurchaser(purchaser);
            ticketType.setTickets(List.of(ticket));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countByTicketTypeEventId(eventId)).thenReturn(0);
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(ticketRepository.bulkUpdateStatusByEventId(eventId,
                    TicketStatusEnum.PURCHASED, TicketStatusEnum.CANCELLED)).thenReturn(1);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = buildRequest();
            req.setStatus(EventStatusEnum.CANCELLED); // transition to cancelled

            service.updateEventForOrganizer(organizerId, eventId, req);

            verify(ticketRepository).bulkUpdateStatusByEventId(
                    eventId, TicketStatusEnum.PURCHASED, TicketStatusEnum.CANCELLED);
            // Email sent to unique ticket holder
            verify(emailService).sendEventCancellationEmail(
                    "buyer@test.com", "Dave", "Winter Gala");
        }

        @Test
        @DisplayName("sends only one cancellation email per purchaser (deduplication)")
        void deduplicatesCancellationEmails() {
            User purchaser = new User();
            purchaser.setId(UUID.randomUUID());
            purchaser.setEmail("buyer@test.com");
            purchaser.setName("Dave");

            // Same purchaser holds 3 tickets
            List<Ticket> tickets = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                Ticket t = new Ticket();
                t.setId(UUID.randomUUID());
                t.setPurchaser(purchaser);
                tickets.add(t);
            }
            ticketType.setTickets(tickets);

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countByTicketTypeEventId(eventId)).thenReturn(0);
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(ticketRepository.bulkUpdateStatusByEventId(any(), any(), any())).thenReturn(3);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = buildRequest();
            req.setStatus(EventStatusEnum.CANCELLED);

            service.updateEventForOrganizer(organizerId, eventId, req);

            // Only ONE email despite 3 tickets
            verify(emailService, times(1)).sendEventCancellationEmail(
                    "buyer@test.com", "Dave", "Winter Gala");
        }

        @Test
        @DisplayName("throws EventUpdateException when IDs don't match")
        void throwsWhenIdMismatch() {
            UpdateEventRequest req = buildRequest();
            req.setId(UUID.randomUUID()); // different ID

            assertThatThrownBy(() ->
                    service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(EventUpdateException.class)
                    .hasMessageContaining("Cannot update the ID");
        }
    }

    // ── getSalesDashboard ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSalesDashboard — FIX #5 BigDecimal")
    class SalesDashboard {

        @Test
        @DisplayName("returns BigDecimal revenue totals, not doubles")
        void returnsBigDecimalRevenue() {
            User buyer = new User();
            buyer.setId(UUID.randomUUID());

            Ticket t = new Ticket();
            t.setId(UUID.randomUUID());
            t.setPurchaser(buyer);
            t.setOriginalPrice(new BigDecimal("100.00"));
            t.setPricePaid(new BigDecimal("80.00"));
            t.setDiscountApplied(new BigDecimal("20.00"));
            ticketType.setTickets(List.of(t));

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            assertThat(dashboard.get("totalTicketsSold")).isEqualTo(1);
            assertThat(dashboard.get("totalRevenueFinal"))
                    .isInstanceOf(BigDecimal.class);
            assertThat((BigDecimal) dashboard.get("totalRevenueFinal"))
                    .isEqualByComparingTo("80.00");
            assertThat((BigDecimal) dashboard.get("totalRevenueBeforeDiscount"))
                    .isEqualByComparingTo("100.00");
            assertThat((BigDecimal) dashboard.get("totalDiscountGiven"))
                    .isEqualByComparingTo("20.00");
        }

        @Test
        @DisplayName("returns zero totals when no tickets sold")
        void returnsZeroWhenNoTickets() {
            ticketType.setTickets(new ArrayList<>());

            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            assertThat(dashboard.get("totalTicketsSold")).isEqualTo(0);
            assertThat((BigDecimal) dashboard.get("totalRevenueFinal"))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}

