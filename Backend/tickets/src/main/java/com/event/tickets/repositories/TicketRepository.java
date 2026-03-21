package com.event.tickets.repositories;

import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketStatusEnum;
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

    @Query("SELECT COUNT(t) FROM Ticket t " +
            "WHERE t.ticketType.id = :ticketTypeId " +
            "AND t.status != :excludedStatus")
    int countActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    int countByTicketTypeEventId(UUID eventId);

    @Query("SELECT COUNT(t) FROM Ticket t " +
            "WHERE t.ticketType.event.id = :eventId " +
            "AND t.status != :excludedStatus")
    int countActiveTicketsByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    int countByTicketTypeIdAndPurchaserId(UUID ticketTypeId, UUID purchaserId);

    Page<Ticket> findByPurchaserId(UUID purchaserId, Pageable pageable);

    Optional<Ticket> findByIdAndPurchaserId(UUID id, UUID purchaserId);

    List<Ticket> findByTicketTypeEventId(UUID eventId);

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

    /**
     * FIX-E2: Aggregate sales stats per ticket type for getSalesDashboard().
     *
     * Returns one row per ticket type with pre-computed SUM/COUNT —
     * no Ticket entities loaded into memory regardless of how many tickets exist.
     *
     * Result columns per row:
     *   [0] UUID          ticketTypeId
     *   [1] Long          soldCount         (non-cancelled tickets)
     *   [2] BigDecimal    sumOriginalPrice  (revenue before discount)
     *   [3] BigDecimal    sumDiscountApplied
     *   [4] BigDecimal    sumPricePaid      (final revenue)
     */
    @Query("SELECT t.ticketType.id, " +
            "       COUNT(t), " +
            "       SUM(COALESCE(t.originalPrice,  t.ticketType.price)), " +
            "       SUM(COALESCE(t.discountApplied, 0)), " +
            "       SUM(COALESCE(t.pricePaid,       t.ticketType.price)) " +
            "FROM Ticket t " +
            "WHERE t.ticketType.event.id = :eventId " +
            "  AND t.status != :excludedStatus " +
            "GROUP BY t.ticketType.id")
    List<Object[]> findSalesStatsByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    /**
     * FIX-E3: Attendee report projection for getAttendeesReport().
     *
     * Returns one row per non-cancelled ticket with only the fields
     * needed for the report — no Ticket or User entities in memory.
     *
     * Result columns per row:
     *   [0] String            purchaser name
     *   [1] String            purchaser email
     *   [2] String            ticket type name
     *   [3] TicketStatusEnum  ticket status
     *   [4] LocalDateTime     purchase date (createdAt)
     *   [5] Long              validation count
     */
    @Query("SELECT p.name, p.email, tt.name, t.status, t.createdAt, SIZE(t.validations) " +
            "FROM Ticket t " +
            "JOIN t.purchaser p " +
            "JOIN t.ticketType tt " +
            "WHERE tt.event.id = :eventId " +
            "  AND t.status != :excludedStatus " +
            "ORDER BY p.name ASC")
    List<Object[]> findAttendeeReportByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    /**
     * FIX-E4: Returns distinct purchaser (email, name) pairs for cancellation emails.
     *
     * Uses DISTINCT on purchaser ID so each person gets exactly one email
     * even if they bought multiple tickets for the event.
     * Returns only the two fields needed — no entity loading.
     *
     * Result columns per row:
     *   [0] String  purchaser email
     *   [1] String  purchaser name
     */
    @Query("SELECT DISTINCT p.email, p.name " +
            "FROM Ticket t " +
            "JOIN t.purchaser p " +
            "WHERE t.ticketType.event.id = :eventId")
    List<Object[]> findDistinctPurchasersByEventId(@Param("eventId") UUID eventId);
}