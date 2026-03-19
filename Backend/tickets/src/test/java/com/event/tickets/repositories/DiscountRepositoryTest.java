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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIX SUMMARY — all 5 tests failed with:
 *   "detached entity passed to persist: com.event.tickets.domain.entities.Event"
 *
 * ROOT CAUSE: @BeforeEach uses em.persist() + em.flush(). After flush(), the
 * TestEntityManager's first-level cache is cleared by @DataJpaTest between tests
 * (each test runs in a transaction that rolls back). On the second test, `ticketType`
 * still holds a reference to the `event` entity which is now detached from the new
 * transaction's persistence context. When buildDiscount() calls em.persist(discount)
 * with discount.setTicketType(ticketType), JPA tries to cascade-persist ticketType,
 * which cascades to event — but event is detached, not new, so it throws.
 *
 * FIX: Use em.merge() to re-attach the detached entities at the start of each test,
 * OR re-fetch them using em.find(). The cleanest fix is to call em.find() on the
 * ticketType at the start of each test to get a managed instance in the new transaction.
 *
 * The setUp() method is fine as-is — the issue only surfaces in the individual tests
 * because @DataJpaTest wraps each test in a transaction that rolls back and clears the
 * entity cache. The ticketType field in the test class holds a stale detached reference.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DiscountRepository")
class DiscountRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DiscountRepository discountRepository;

    // Store IDs only — never store entity instances as fields in @DataJpaTest
    // because they become detached between tests
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        User organizer = new User();
        organizer.setId(UUID.randomUUID());
        organizer.setName("Organizer");
        organizer.setEmail("org@test.com");
        organizer.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(organizer);

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

        // FIX: store only the ID — re-fetch the managed entity inside each test
        ticketTypeId = ticketType.getId();
    }

    @Test
    @DisplayName("FIX #6 — expired discount (active=true, validTo in past) returns false")
    void expiredDiscountDoesNotBlockNewOne() {
        // FIX: re-fetch managed instance for this transaction — avoids detached entity error
        TicketType managedTicketType = em.find(TicketType.class, ticketTypeId);

        Discount expired = new Discount();
        expired.setTicketType(managedTicketType);
        expired.setDiscountType(DiscountType.PERCENTAGE);
        expired.setValue(new BigDecimal("10"));
        expired.setValidFrom(LocalDateTime.now().minusDays(10));
        expired.setValidTo(LocalDateTime.now().minusDays(1)); // EXPIRED
        expired.setActive(true); // active flag still true — was the bug
        expired.setCreatedBy(UUID.randomUUID());
        em.persistAndFlush(expired);

        boolean exists = discountRepository.existsActiveDiscountForTicketType(
                ticketTypeId, LocalDateTime.now());

        // Must be false — validTo is in the past
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("truly active discount (valid dates, active=true) returns true")
    void activeDiscountReturnsTrue() {
        TicketType managedTicketType = em.find(TicketType.class, ticketTypeId);

        Discount active = new Discount();
        active.setTicketType(managedTicketType);
        active.setDiscountType(DiscountType.PERCENTAGE);
        active.setValue(new BigDecimal("10"));
        active.setValidFrom(LocalDateTime.now().minusDays(1));
        active.setValidTo(LocalDateTime.now().plusDays(30));
        active.setActive(true);
        active.setCreatedBy(UUID.randomUUID());
        em.persistAndFlush(active);

        boolean exists = discountRepository.existsActiveDiscountForTicketType(
                ticketTypeId, LocalDateTime.now());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("inactive discount (active=false) returns false even within valid dates")
    void inactiveDiscountReturnsFalse() {
        TicketType managedTicketType = em.find(TicketType.class, ticketTypeId);

        Discount inactive = new Discount();
        inactive.setTicketType(managedTicketType);
        inactive.setDiscountType(DiscountType.PERCENTAGE);
        inactive.setValue(new BigDecimal("10"));
        inactive.setValidFrom(LocalDateTime.now().minusDays(1));
        inactive.setValidTo(LocalDateTime.now().plusDays(30));
        inactive.setActive(false); // explicitly inactive
        inactive.setCreatedBy(UUID.randomUUID());
        em.persistAndFlush(inactive);

        boolean exists = discountRepository.existsActiveDiscountForTicketType(
                ticketTypeId, LocalDateTime.now());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("findActiveDiscount returns only the currently valid active discount")
    void findActiveDiscountReturnsCurrentOne() {
        TicketType managedTicketType = em.find(TicketType.class, ticketTypeId);

        Discount expired = buildDiscount(managedTicketType,
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), true);
        Discount active = buildDiscount(managedTicketType,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(30), true);
        em.persist(expired);
        em.persistAndFlush(active);

        Optional<Discount> result = discountRepository.findActiveDiscount(
                ticketTypeId, LocalDateTime.now());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(active.getId());
    }

    @Test
    @DisplayName("findActiveDiscount returns empty when no active discounts exist")
    void findActiveDiscountReturnsEmptyWhenNone() {
        // No discounts persisted for this ticket type
        Optional<Discount> result = discountRepository.findActiveDiscount(
                ticketTypeId, LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findActiveDiscount returns empty when only an inactive discount exists")
    void findActiveDiscountReturnsEmptyForInactiveDiscount() {
        TicketType managedTicketType = em.find(TicketType.class, ticketTypeId);

        Discount inactive = buildDiscount(managedTicketType,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), false);
        em.persistAndFlush(inactive);

        Optional<Discount> result = discountRepository.findActiveDiscount(
                ticketTypeId, LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    private Discount buildDiscount(TicketType ticketType,
                                   LocalDateTime from, LocalDateTime to, boolean active) {
        Discount d = new Discount();
        d.setTicketType(ticketType);
        d.setDiscountType(DiscountType.PERCENTAGE);
        d.setValue(new BigDecimal("10"));
        d.setValidFrom(from);
        d.setValidTo(to);
        d.setActive(active);
        d.setCreatedBy(UUID.randomUUID());
        return d;
    }
}