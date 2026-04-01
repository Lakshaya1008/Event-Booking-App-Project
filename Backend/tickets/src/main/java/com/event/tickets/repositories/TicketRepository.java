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

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.ticketType.id = :ticketTypeId AND t.status <> :excludedStatus")
    int countActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

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
        SELECT t.ticketType.id,
               COUNT(t),
               SUM(t.originalPrice),
               SUM(t.discountApplied),
               SUM(t.pricePaid)
        FROM Ticket t
        WHERE t.ticketType.event.id = :eventId
        AND t.status <> :excludedStatus
        GROUP BY t.ticketType.id
        """)
    List<Object[]> findSalesStatsByEventId(
            @Param("eventId") UUID eventId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );
}