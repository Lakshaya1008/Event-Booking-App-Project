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

    int countByTicketTypeEventId(UUID eventId);

    /**
     * FIX #8: Count only non-CANCELLED tickets for an event.
     *
     * The original countByTicketTypeEventId() counted ALL tickets including CANCELLED.
     * This meant:
     * - deleteEventForOrganizer() was blocked even after all tickets had been
     *   bulk-cancelled (which is the required flow before deletion).
     * - maxCapacity checks after cancellations were overly restrictive.
     *
     * This query excludes tickets with the given status (pass TicketStatusEnum.CANCELLED).
     * Used for both the event-delete guard and maxCapacity enforcement.
     */
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

    @Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus " +
            "WHERE t.ticketType.event.id = :eventId AND t.status = :currentStatus")
    int bulkUpdateStatusByEventId(
            @Param("eventId") UUID eventId,
            @Param("currentStatus") TicketStatusEnum currentStatus,
            @Param("newStatus") TicketStatusEnum newStatus
    );
}