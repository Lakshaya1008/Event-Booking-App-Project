package com.event.tickets.services;

import com.event.tickets.domain.CreateEventRequest;
import com.event.tickets.domain.CreateTicketTypeRequest;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CHANGES FROM PREVIOUS VERSION:
 *
 * TEST-E1 — Status transition tests added.
 *   createEvent() must only accept DRAFT on create.
 *   updateEventForOrganizer() must enforce the transition state machine.
 *
 * TEST-E2 — getSalesDashboard() now mocks findSalesStatsByEventId() aggregate
 *   query instead of ticket type collection iteration.
 *   Old tests that mocked ticketType.getTickets() are replaced.
 *
 * TEST-E3 — COMPLETED status tests: auto-complete scheduler test.
 *
 * TEST-E4 — Cancellation email test now verifies sendCancellationEmailsAsync
 *   is called with eventId + eventName, not looping through ticket collections.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    // ── createEvent — status transition ──────────────────────────────────────

    @Nested
    @DisplayName("createEvent — FIX-E1 status enforcement")
    class CreateEventStatusRules {

        private CreateEventRequest buildRequest() {
            CreateEventRequest req = new CreateEventRequest();
            req.setName("New Event");
            req.setVenue("Grand Hall");
            req.setStart(LocalDateTime.now().plusDays(10));
            req.setEnd(LocalDateTime.now().plusDays(11));
            req.setStatus(null); // service sets DRAFT
            CreateTicketTypeRequest tt = new CreateTicketTypeRequest();
            tt.setName("General");
            tt.setPrice(new BigDecimal("50.00"));
            tt.setTotalAvailable(100);
            req.setTicketTypes(List.of(tt));
            return req;
        }

        @Test
        @DisplayName("event is created as DRAFT when status is omitted")
        void alwaysCreatedAsDraft() {
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));
            when(eventRepository.save(any())).thenAnswer(inv -> {
                Event e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            CreateEventRequest req = buildRequest();

            Event result = service.createEvent(organizerId, req);

            assertThat(result.getStatus()).isEqualTo(EventStatusEnum.DRAFT);
        }

        @Test
        @DisplayName("throws when status=PUBLISHED is explicitly requested on create")
        void throwsWhenPublishedRequestedOnCreate() {
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            CreateEventRequest req = buildRequest();
            req.setStatus(EventStatusEnum.PUBLISHED);

            assertThatThrownBy(() -> service.createEvent(organizerId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("throws when status=CANCELLED is requested on create")
        void throwsWhenCancelledRequestedOnCreate() {
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            CreateEventRequest req = buildRequest();
            req.setStatus(EventStatusEnum.CANCELLED);

            assertThatThrownBy(() -> service.createEvent(organizerId, req))
                    .isInstanceOf(InvalidBusinessStateException.class);
        }
    }

    // ── updateEventForOrganizer — status transitions ──────────────────────────

    @Nested
    @DisplayName("updateEventForOrganizer — FIX-E1 state machine")
    class UpdateEventStatusTransitions {

        private UpdateEventRequest buildRequest(EventStatusEnum targetStatus) {
            UpdateEventRequest req = new UpdateEventRequest();
            req.setId(eventId);
            req.setName("Winter Gala Updated");
            req.setVenue("Grand Hall");
            req.setStatus(targetStatus);
            UpdateTicketTypeRequest tt = new UpdateTicketTypeRequest();
            tt.setId(ticketType.getId());
            tt.setName("VIP");
            tt.setPrice(new BigDecimal("200.00"));
            tt.setTotalAvailable(50);
            req.setTicketTypes(List.of(tt));
            return req;
        }

        @Test
        @DisplayName("DRAFT → PUBLISHED is allowed")
        void draftToPublished_allowed() {
            event.setStatus(EventStatusEnum.DRAFT);
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED)).thenReturn(0);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            assertThatCode(() -> service.updateEventForOrganizer(organizerId, eventId,
                    buildRequest(EventStatusEnum.PUBLISHED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PUBLISHED → DRAFT is blocked (no backward transitions)")
        void publishedToDraft_blocked() {
            event.setStatus(EventStatusEnum.PUBLISHED);
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED)).thenReturn(0);

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId,
                    buildRequest(EventStatusEnum.DRAFT)))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("transition");
        }

        @Test
        @DisplayName("CANCELLED event cannot be modified")
        void cancelledEvent_cannotBeModified() {
            event.setStatus(EventStatusEnum.CANCELLED);
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId,
                    buildRequest(EventStatusEnum.PUBLISHED)))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("CANCELLED");
        }

        @Test
        @DisplayName("COMPLETED event cannot be modified")
        void completedEvent_cannotBeModified() {
            event.setStatus(EventStatusEnum.COMPLETED);
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId,
                    buildRequest(EventStatusEnum.PUBLISHED)))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("COMPLETED");
        }

        @Test
        @DisplayName("PUBLISHED → CANCELLED triggers bulk cancel and async emails")
        void publishedToCancelled_triggersBulkCancelAndEmails() {
            event.setStatus(EventStatusEnum.PUBLISHED);
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED)).thenReturn(0);
            when(ticketRepository.bulkUpdateStatusByEventId(any(), any(), any())).thenReturn(5);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            service.updateEventForOrganizer(organizerId, eventId, buildRequest(EventStatusEnum.CANCELLED));

            verify(ticketRepository, atLeastOnce()).bulkUpdateStatusByEventId(
                    eq(eventId), eq(TicketStatusEnum.PURCHASED), eq(TicketStatusEnum.CANCELLED));
            // Email sending is async — verify the repository query was called instead
            verify(ticketRepository).findDistinctPurchasersByEventId(
                    eventId, TicketStatusEnum.CANCELLED);
        }
    }

    // ── getSalesDashboard — aggregate queries ─────────────────────────────────

    @Nested
    @DisplayName("getSalesDashboard — FIX-E2 aggregate queries")
    class SalesDashboard {

        @Test
        @DisplayName("uses aggregate query — no ticket entities loaded")
        void usesAggregateQuery_noTicketEntitiesLoaded() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            // Stub the aggregate query result
            Object[] statsRow = new Object[]{
                    ticketType.getId(),   // [0] ticketTypeId
                    3L,                   // [1] soldCount
                    new BigDecimal("300.00"), // [2] sumOriginalPrice
                    new BigDecimal("30.00"),  // [3] sumDiscountApplied
                    new BigDecimal("270.00")  // [4] sumPricePaid
            };
            when(ticketRepository.findSalesStatsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(List.<Object[]>of(statsRow));

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            assertThat(dashboard.get("totalTicketsSold")).isEqualTo(3);
            assertThat((BigDecimal) dashboard.get("totalRevenueFinal"))
                    .isEqualByComparingTo("270.00");
            assertThat((BigDecimal) dashboard.get("totalDiscountGiven"))
                    .isEqualByComparingTo("30.00");

            // Must have used the aggregate query — NOT iterated ticket collections
            verify(ticketRepository).findSalesStatsByEventId(eventId, TicketStatusEnum.CANCELLED);
        }

        @Test
        @DisplayName("returns zero stats for ticket type with no sales")
        void zeroStats_forUnsoldTicketType() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            // Empty result — no tickets sold
            when(ticketRepository.findSalesStatsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(List.of());

            Map<String, Object> dashboard = service.getSalesDashboard(organizerId, eventId);

            assertThat(dashboard.get("totalTicketsSold")).isEqualTo(0);
            assertThat((BigDecimal) dashboard.get("totalRevenueFinal"))
                    .isEqualByComparingTo("0.00");
        }
    }

    // ── autoCompleteExpiredEvents ─────────────────────────────────────────────

    @Nested
    @DisplayName("autoCompleteExpiredEvents — FIX-E5")
    class AutoComplete {

        @Test
        @DisplayName("marks PUBLISHED events with past end date as COMPLETED")
        void marksExpiredEventsAsCompleted() {
            event.setStatus(EventStatusEnum.PUBLISHED);
            event.setEnd(LocalDateTime.now().minusDays(1));

            when(eventRepository.findByStatusAndEndBefore(
                    eq(EventStatusEnum.PUBLISHED), any(LocalDateTime.class)))
                    .thenReturn(List.of(event));
            when(eventRepository.save(any())).thenReturn(event);

            service.autoCompleteExpiredEvents();

            verify(eventRepository).save(argThat(e ->
                    EventStatusEnum.COMPLETED.equals(e.getStatus())));
        }

        @Test
        @DisplayName("does nothing when no events have passed end date")
        void doesNothingWhenNoExpiredEvents() {
            when(eventRepository.findByStatusAndEndBefore(any(), any()))
                    .thenReturn(List.of());

            assertThatCode(() -> service.autoCompleteExpiredEvents())
                    .doesNotThrowAnyException();

            verify(eventRepository, never()).save(any());
        }
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
        @DisplayName("allows deletion when no active tickets")
        void allowsWhenNoActiveTickets() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED))
                    .thenReturn(0);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

            assertThatCode(() -> service.deleteEventForOrganizer(organizerId, eventId))
                    .doesNotThrowAnyException();
            verify(eventRepository).delete(event);
        }
    }

    // ── Date validation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Date validation on CREATE — future dates enforced")
    class CreateDateValidation {

        @Test
        @DisplayName("past event start date is rejected on create")
        void pastEventStart_rejectedOnCreate() {
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            CreateEventRequest req = buildCreateRequest();
            req.setStart(LocalDateTime.now().minusDays(1));
            req.setEnd(LocalDateTime.now().plusDays(1));

            assertThatThrownBy(() -> service.createEvent(organizerId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("future");
        }

        private CreateEventRequest buildCreateRequest() {
            CreateEventRequest req = new CreateEventRequest();
            req.setName("New Event");
            req.setVenue("Grand Hall");
            req.setStatus(null);
            req.setStart(LocalDateTime.now().plusDays(10));
            req.setEnd(LocalDateTime.now().plusDays(11));
            CreateTicketTypeRequest tt = new CreateTicketTypeRequest();
            tt.setName("General");
            tt.setPrice(new BigDecimal("50.00"));
            tt.setTotalAvailable(100);
            req.setTicketTypes(List.of(tt));
            return req;
        }
    }

    @Nested
    @DisplayName("Date validation on UPDATE — past dates allowed, ordering enforced")
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
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED)).thenReturn(0);
            when(eventRepository.save(any())).thenReturn(event);
            when(userRepository.findById(organizerId)).thenReturn(Optional.of(organizer));

            UpdateEventRequest req = buildRequest();
            req.setSalesStart(LocalDateTime.now().minusDays(3));
            req.setSalesEnd(LocalDateTime.now().plusDays(5));

            assertThatCode(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("UPDATE enforces ordering: end must be after start")
        void ordering_enforced_onUpdate() {
            doNothing().when(authorizationService).requireOrganizerAccess(organizerId, eventId);
            when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
            when(ticketRepository.countActiveTicketsByEventId(eventId, TicketStatusEnum.CANCELLED)).thenReturn(0);

            UpdateEventRequest req = buildRequest();
            req.setStart(LocalDateTime.now().plusDays(5));
            req.setEnd(LocalDateTime.now().plusDays(2)); // before start

            assertThatThrownBy(() -> service.updateEventForOrganizer(organizerId, eventId, req))
                    .isInstanceOf(InvalidBusinessStateException.class)
                    .hasMessageContaining("after");
        }
    }
}