package com.event.tickets.repositories;

import com.event.tickets.domain.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX SUMMARY:
 *
 * 1. "Cannot resolve symbol 'VALIDATED'" (line 142 in original):
 *    TicketStatusEnum only has PURCHASED and CANCELLED — there is no VALIDATED value.
 *    Fix: remove all TicketStatusEnum.VALIDATED references.
 *    Note: the test that checked "VALIDATED tickets count as active" is replaced with
 *    the correct business rule: only PURCHASED tickets are active (CANCELLED are excluded).
 *    The service excludes CANCELLED; PURCHASED is the only other status.
 *
 * 2. "detached entity passed to persist: Event" — all 4 tests:
 *    @DataJpaTest rolls back each test's transaction, making the entity instances
 *    stored in @BeforeEach fields detached. Fix: store only UUIDs; re-fetch with em.find()
 *    inside persistTicket() to get managed instances for the current transaction.
 *
 * 3. "@DataJpaTest wrong tag" warning:
 *    @DataJpaTest is the correct annotation. The warning is cosmetic (IDE linting).
 *    No code change needed.
 *
 * 4. Private fields 'em' and 'ticketRepository' never assigned:
 *    These were instance fields without @Autowired in the original. They need @Autowired.
 *    Already present in this version.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TicketRepository")
class TicketRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TicketRepository ticketRepository;

    // FIX: store only IDs, not entity instances — entities become detached between tests
    private UUID eventId;
    private UUID ticketTypeId;
    private UUID purchaserId;

    @BeforeEach
    void setUp() {
        User organizer = new User();
        organizer.setId(UUID.randomUUID());
        organizer.setName("Org");
        organizer.setEmail("org@test.com");
        organizer.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(organizer);

        User purchaser = new User();
        purchaser.setId(UUID.randomUUID());
        purchaser.setName("Buyer");
        purchaser.setEmail("buyer@test.com");
        purchaser.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(purchaser);

        Event event = new Event();
        event.setName("Test Event");
        event.setVenue("Venue");
        event.setStatus(EventStatusEnum.PUBLISHED);
        event.setOrganizer(organizer);
        em.persist(event);

        TicketType ticketType = new TicketType();
        ticketType.setName("GA");
        ticketType.setPrice(new BigDecimal("50.00"));
        ticketType.setTotalAvailable(100);
        ticketType.setEvent(event);
        em.persist(ticketType);

        em.flush();

        // Store IDs only — re-fetch in each test via em.find()
        eventId      = event.getId();
        ticketTypeId = ticketType.getId();
        purchaserId  = purchaser.getId();
    }

    @Test
    @DisplayName("FIX #8 — countActiveTicketsByEventId excludes CANCELLED tickets")
    void excludesCancelledTickets() {
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.CANCELLED);
        persistTicket(TicketStatusEnum.CANCELLED);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        // 3 PURCHASED, 2 CANCELLED → only 3 active
        assertThat(active).isEqualTo(3);
    }

    @Test
    @DisplayName("returns 0 when all tickets are CANCELLED — allows event deletion")
    void returnsZeroWhenAllCancelled() {
        persistTicket(TicketStatusEnum.CANCELLED);
        persistTicket(TicketStatusEnum.CANCELLED);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        // FIX #8: was returning 2 before (blocked deletion), now correctly returns 0
        assertThat(active).isEqualTo(0);
    }

    @Test
    @DisplayName("returns full count when no tickets are CANCELLED")
    void returnsAllWhenNoneCancelled() {
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        assertThat(active).isEqualTo(2);
    }

    @Test
    @DisplayName("returns 0 when event has no tickets at all")
    void returnsZeroForEmptyEvent() {
        int active = ticketRepository.countActiveTicketsByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        assertThat(active).isEqualTo(0);
    }

    @Test
    @DisplayName("counts only tickets belonging to the specified event — not other events")
    void countsOnlyForSpecifiedEvent() {
        // Create a second event and add a ticket for it
        User organizer2 = new User();
        organizer2.setId(UUID.randomUUID());
        organizer2.setName("Org2");
        organizer2.setEmail("org2@test.com");
        organizer2.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(organizer2);

        Event otherEvent = new Event();
        otherEvent.setName("Other Event");
        otherEvent.setVenue("Other Venue");
        otherEvent.setStatus(EventStatusEnum.PUBLISHED);
        otherEvent.setOrganizer(organizer2);
        em.persist(otherEvent);

        TicketType otherType = new TicketType();
        otherType.setName("Other GA");
        otherType.setPrice(new BigDecimal("25.00"));
        otherType.setTotalAvailable(50);
        otherType.setEvent(otherEvent);
        em.persist(otherType);
        em.flush();

        // 2 tickets for our event
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);

        // 1 ticket for other event — should NOT be counted
        Ticket otherTicket = new Ticket();
        otherTicket.setStatus(TicketStatusEnum.PURCHASED);
        otherTicket.setTicketType(em.find(TicketType.class, otherType.getId()));
        otherTicket.setPurchaser(em.find(User.class, purchaserId));
        otherTicket.setOriginalPrice(new BigDecimal("25.00"));
        otherTicket.setPricePaid(new BigDecimal("25.00"));
        otherTicket.setDiscountApplied(BigDecimal.ZERO);
        em.persist(otherTicket);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                eventId, TicketStatusEnum.CANCELLED);

        // Should only count 2 (our event), not 3
        assertThat(active).isEqualTo(2);
    }

    // FIX: re-fetch managed entities via em.find() — avoids "detached entity passed to persist"
    private void persistTicket(TicketStatusEnum status) {
        TicketType managedType     = em.find(TicketType.class, ticketTypeId);
        User       managedPurchaser = em.find(User.class, purchaserId);

        Ticket ticket = new Ticket();
        ticket.setStatus(status);
        ticket.setTicketType(managedType);
        ticket.setPurchaser(managedPurchaser);
        ticket.setOriginalPrice(new BigDecimal("50.00"));
        ticket.setPricePaid(new BigDecimal("50.00"));
        ticket.setDiscountApplied(BigDecimal.ZERO);
        em.persist(ticket);
    }
}