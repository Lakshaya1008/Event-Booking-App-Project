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

    /**
     * Counts ALL tickets for a ticket type regardless of status.
     * Used for: hard sold-count display, audit, and historical records.
     *
     * NOTE: For purchase availability checks and the totalAvailable guard,
     * use countActiveByTicketTypeId() instead — CANCELLED tickets should
     * not permanently consume slots.
     */
    int countByTicketTypeId(UUID ticketTypeId);

    /**
     * H-06 / H-07 FIX: Counts only non-CANCELLED tickets for a ticket type.
     *
     * Previously purchaseTickets() and updateTicketType() both called
     * countByTicketTypeId() which included CANCELLED tickets. This caused:
     *   H-06: If 10 tickets were sold and 3 cancelled, only 7 real slots were
     *         used — but the check saw 10, blocking purchase of the 8th slot
     *         when 13 were available. Cancelled slots were permanently locked.
     *   H-07: updateTicketType() refused to raise totalAvailable back above
     *         the CANCELLED-inclusive count, preventing organizers from
     *         re-opening cancelled capacity.
     */
    @Query("SELECT COUNT(t) FROM Ticket t " +
            "WHERE t.ticketType.id = :ticketTypeId " +
            "AND t.status != :excludedStatus")
    int countActiveByTicketTypeId(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludedStatus") TicketStatusEnum excludedStatus
    );

    int countByTicketTypeEventId(UUID eventId);

    /**
     * Counts non-CANCELLED tickets for an event.
     * Used for: event-level maxCapacity enforcement and event delete guard.
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