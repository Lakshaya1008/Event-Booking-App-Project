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
 * Repository slice test for DiscountRepository.
 *
 * Uses @DataJpaTest which spins up an in-memory H2 database.
 * No Spring MVC / Security context loaded — fast and focused.
 *
 * Covers FIX #6: existsActiveDiscountForTicketType must check validTo > :now
 * so expired discounts (active=true, validTo in past) do NOT block new discounts.
 *
 * To use H2 for these tests, add to pom.xml (test scope only):
 *   <dependency>
 *     <groupId>com.h2database</groupId>
 *     <artifactId>h2</artifactId>
 *     <scope>test</scope>
 *   </dependency>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("DiscountRepository")
class DiscountRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DiscountRepository discountRepository;

    private TicketType ticketType;

    @BeforeEach
    void setUp() {
        // Persist minimal required entities
        User organizer = new User();
        organizer.setId(UUID.randomUUID());
        organizer.setName("Organizer");
        organizer.setEmail("org@test.com");
        organizer.setApprovalStatus(ApprovalStatus.APPROVED);
        em.persist(organizer);

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setName("Test Event");
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
    @DisplayName("FIX #6 — expired discount (active=true, validTo in past) returns false")
    void expiredDiscountDoesNotBlockNewOne() {
        // Discount that has active=true but expired yesterday
        Discount expired = new Discount();
        expired.setId(UUID.randomUUID());
        expired.setTicketType(ticketType);
        expired.setDiscountType(DiscountType.PERCENTAGE);
        expired.setValue(new BigDecimal("10"));
        expired.setValidFrom(LocalDateTime.now().minusDays(10));
        expired.setValidTo(LocalDateTime.now().minusDays(1)); // EXPIRED
        expired.setActive(true); // active flag still true — this was the bug
        expired.setCreatedBy(UUID.randomUUID());
        em.persistAndFlush(expired);

        // FIX #6: now() is passed — query checks validTo > now
        boolean exists = discountRepository.existsActiveDiscountForTicketType(
                ticketType.getId(), LocalDateTime.now());

        // Must be false because validTo is in the past
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("truly active discount (valid, active=true) returns true")
    void activeDiscountReturnsTrue() {
        Discount active = new Discount();
        active.setId(UUID.randomUUID());
        active.setTicketType(ticketType);
        active.setDiscountType(DiscountType.PERCENTAGE);
        active.setValue(new BigDecimal("10"));
        active.setValidFrom(LocalDateTime.now().minusDays(1));
        active.setValidTo(LocalDateTime.now().plusDays(30)); // valid
        active.setActive(true);
        active.setCreatedBy(UUID.randomUUID());
        em.persistAndFlush(active);

        boolean exists = discountRepository.existsActiveDiscountForTicketType(
                ticketType.getId(), LocalDateTime.now());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("inactive discount (active=false) returns false")
    void inactiveDiscountReturnsFalse() {
        Discount inactive = new Discount();
        inactive.setId(UUID.randomUUID());
        inactive.setTicketType(ticketType);
        inactive.setDiscountType(DiscountType.PERCENTAGE);
        inactive.setValue(new BigDecimal("10"));
        inactive.setValidFrom(LocalDateTime.now().minusDays(1));
        inactive.setValidTo(LocalDateTime.now().plusDays(30));
        inactive.setActive(false); // explicitly inactive
        inactive.setCreatedBy(UUID.randomUUID());
        em.persistAndFlush(inactive);

        boolean exists = discountRepository.existsActiveDiscountForTicketType(
                ticketType.getId(), LocalDateTime.now());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("findActiveDiscount returns only currently valid discount")
    void findActiveDiscountReturnsCurrentOne() {
        // Create two discounts: one expired, one active
        Discount expired = buildDiscount(
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1), true);
        Discount active = buildDiscount(
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusDays(30), true);
        em.persist(expired);
        em.persistAndFlush(active);

        Optional<Discount> result = discountRepository.findActiveDiscount(
                ticketType.getId(), LocalDateTime.now());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(active.getId());
    }

    @Test
    @DisplayName("findActiveDiscount returns empty when no active discounts exist")
    void findActiveDiscountReturnsEmptyWhenNone() {
        Optional<Discount> result = discountRepository.findActiveDiscount(
                ticketType.getId(), LocalDateTime.now());

        assertThat(result).isEmpty();
    }

    private Discount buildDiscount(LocalDateTime from, LocalDateTime to, boolean active) {
        Discount d = new Discount();
        d.setId(UUID.randomUUID());
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

