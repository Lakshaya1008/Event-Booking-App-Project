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
 * Repository slice test for TicketRepository.
 *
 * Covers FIX #8: countActiveTicketsByEventId must exclude CANCELLED tickets
 * so that deleteEventForOrganizer allows deletion after event cancellation.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TicketRepository")
class TicketRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TicketRepository ticketRepository;

    private Event event;
    private TicketType ticketType;
    private User purchaser;

    @BeforeEach
    void setUp() {
        User organizer = new User();
        organizer.setId(UUID.randomUUID());
        organizer.setName("Org");
        organizer.setEmail("org@test.com");
        organizer.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(organizer);

        purchaser = new User();
        purchaser.setId(UUID.randomUUID());
        purchaser.setName("Buyer");
        purchaser.setEmail("buyer@test.com");
        purchaser.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(purchaser);

        event = new Event();
        event.setId(UUID.randomUUID());
        event.setName("Event");
        event.setVenue("Venue");
        event.setStatus(EventStatusEnum.PUBLISHED);
        event.setOrganizer(organizer);
        em.persist(event);

        ticketType = new TicketType();
        ticketType.setId(UUID.randomUUID());
        ticketType.setName("GA");
        ticketType.setPrice(new BigDecimal("50.00"));
        ticketType.setTotalAvailable(100);
        ticketType.setEvent(event);
        em.persist(ticketType);

        em.flush();
    }

    @Test
    @DisplayName("FIX #8 — countActiveTicketsByEventId excludes CANCELLED tickets")
    void excludesCancelledTickets() {
        // 3 PURCHASED, 2 CANCELLED
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.CANCELLED);
        persistTicket(TicketStatusEnum.CANCELLED);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                event.getId(), TicketStatusEnum.CANCELLED);

        // Should be 3, not 5
        assertThat(active).isEqualTo(3);
    }

    @Test
    @DisplayName("returns 0 when all tickets are CANCELLED (allows event deletion)")
    void returnsZeroWhenAllCancelled() {
        persistTicket(TicketStatusEnum.CANCELLED);
        persistTicket(TicketStatusEnum.CANCELLED);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                event.getId(), TicketStatusEnum.CANCELLED);

        // FIX #8: was previously returning 2 (blocked deletion), now returns 0
        assertThat(active).isEqualTo(0);
    }

    @Test
    @DisplayName("returns full count when no tickets are CANCELLED")
    void returnsAllWhenNoneCancelled() {
        persistTicket(TicketStatusEnum.PURCHASED);
        persistTicket(TicketStatusEnum.PURCHASED);
        em.flush();

        int active = ticketRepository.countActiveTicketsByEventId(
                event.getId(), TicketStatusEnum.CANCELLED);

        assertThat(active).isEqualTo(2);
    }

    @Test
    @DisplayName("returns 0 when event has no tickets at all")
    void returnsZeroForEmptyEvent() {
        int active = ticketRepository.countActiveTicketsByEventId(
                event.getId(), TicketStatusEnum.CANCELLED);

        assertThat(active).isEqualTo(0);
    }

    private void persistTicket(TicketStatusEnum status) {
        Ticket ticket = new Ticket();
        ticket.setId(UUID.randomUUID());
        ticket.setStatus(status);
        ticket.setTicketType(ticketType);
        ticket.setPurchaser(purchaser);
        ticket.setOriginalPrice(new BigDecimal("50.00"));
        ticket.setPricePaid(new BigDecimal("50.00"));
        ticket.setDiscountApplied(BigDecimal.ZERO);
        em.persist(ticket);
    }
}

