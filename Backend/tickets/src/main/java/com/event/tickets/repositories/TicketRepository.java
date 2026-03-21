package com.event.tickets.repositories;

import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    int countByTicketTypeId(UUID ticketTypeId);

    /**
     * H-06 / H-07 FIX: Counts only non-CANCELLED tickets for a ticket type.
     * Used by purchaseTickets() and updateTicketType() to check capacity.
     */
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.ticketType.id = :ticketTypeId AND t.status <> :excludedStatus")
    int countActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    /**
     * FIX D-3 (BUG D-3): Counts non-cancelled tickets for a ticket type where a
     * discount was actually applied (discountApplied > 0).
     *
     * WHY THIS IS NEEDED:
     * The post-sales guard in DiscountServiceImpl.updateDiscount() previously used
     * countActiveByTicketTypeId() which counts ALL active tickets — even full-price
     * ones where discountApplied = 0. This was over-restrictive:
     *
     *   Scenario: Organizer sells 50 full-price tickets (discountApplied=0).
     *   Then adds a 20% discount. Tries to change the discount value from 20% to 25%.
     *   OLD: countActiveByTicketTypeId() returns 50 → BLOCKED.
     *   NEW: countByTicketTypeIdAndDiscountApplied() returns 0 → ALLOWED.
     *
     * The real risk the guard protects against: tickets that were purchased UNDER
     * this discount have their pricePaid/discountApplied amounts stored per-ticket.
     * If the discount definition (type or value) changes, those stored amounts
     * would be inconsistent with the current discount record. That is the actual
     * data integrity concern — not full-price tickets.
     *
     * QUERY: Counts tickets for a given ticket type where:
     *   - status is not CANCELLED (only active tickets matter)
     *   - discountApplied > 0 (ticket was purchased with a discount)
     *
     * Called by: DiscountServiceImpl.updateDiscount() post-sales guard.
     */
    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE t.ticketType.id = :ticketTypeId
        AND t.status <> :excludedStatus
        AND t.discountApplied > :zero
        """)
    int countDiscountedActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus,
            @Param("zero") BigDecimal zero
    );

    @Query("""
        SELECT COUNT(t) FROM Ticket t
        WHERE t.ticketType.event.id = :eventId
        AND t.status <> :excludedStatus
        """)
    int countActiveTicketsByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    int countByTicketTypeIdAndPurchaserId(UUID ticketTypeId, UUID purchaserId);

    Page<Ticket> findByPurchaserId(UUID purchaserId, Pageable pageable);

    Optional<Ticket> findByIdAndPurchaserId(UUID id, UUID purchaserId);

    @Query("""
        SELECT t FROM Ticket t
        JOIN FETCH t.purchaser
        WHERE t.ticketType.id = :ticketTypeId
        AND t.status <> :excludedStatus
        """)
    List<Ticket> findActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    @Query("SELECT t FROM Ticket t WHERE t.originalPrice IS NULL OR t.discountApplied IS NULL OR t.pricePaid IS NULL")
    List<Ticket> findTicketsMissingPricingData();

    @Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus " +
            "WHERE t.ticketType.event.id = :eventId AND t.status = :currentStatus")
    int bulkUpdateStatusByEventId(
            @Param("eventId") UUID eventId,
            @Param("currentStatus") TicketStatusEnum currentStatus,
            @Param("newStatus") TicketStatusEnum newStatus
    );

    @Query("""
        SELECT DISTINCT t.purchaser.email, t.purchaser.name FROM Ticket t
        WHERE t.ticketType.event.id = :eventId
        AND t.status <> :excludedStatus
        """)
    List<Object[]> findDistinctPurchasersByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    @Query("""
        SELECT t.purchaser.name,
               t.purchaser.email,
               t.ticketType.name,
               t.status,
               t.createdAt,
               COUNT(tv.id)
        FROM Ticket t
        LEFT JOIN TicketValidation tv ON tv.ticket = t
        WHERE t.ticketType.event.id = :eventId
        AND t.status <> :excludedStatus
        GROUP BY t.id,
                 t.purchaser.name,
                 t.purchaser.email,
                 t.ticketType.name,
                 t.status,
                 t.createdAt
        ORDER BY t.createdAt DESC
        """)
    List<Object[]> findAttendeeReportByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    @Query("""
        SELECT t.ticketType.id AS ticketTypeId,
               t.ticketType.name AS ticketTypeName,
               COUNT(t) AS totalSold,
               SUM(t.pricePaid) AS totalRevenue
        FROM Ticket t
        WHERE t.ticketType.event.id = :eventId
        AND t.status <> :excludedStatus
        GROUP BY t.ticketType.id, t.ticketType.name
        """)
    List<Object[]> findSalesStatsByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );
}