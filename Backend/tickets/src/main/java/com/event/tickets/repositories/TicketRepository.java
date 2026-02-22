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

    // Count tickets sold for a specific ticket type (used for sold-out check)
    int countByTicketTypeId(UUID ticketTypeId);

    // Count ALL tickets sold across all ticket types for an event (used for event-level cap + delete guard)
    int countByTicketTypeEventId(UUID eventId);

    // List tickets for a purchaser (attendee "my tickets" view)
    Page<Ticket> findByPurchaserId(UUID purchaserId, Pageable pageable);

    // Fetch a single ticket owned by a specific purchaser (ownership check)
    Optional<Ticket> findByIdAndPurchaserId(UUID id, UUID purchaserId);

    // Fetch all tickets for an event across all ticket types (used for event cancellation cascade)
    List<Ticket> findByTicketTypeEventId(UUID eventId);

    /**
     * Bulk-cancel all PURCHASED tickets for an event when the event is cancelled.
     * Uses JPQL UPDATE to avoid loading all ticket entities into memory.
     * Only cancels tickets that are currently PURCHASED — already-cancelled tickets are left alone.
     */
    @Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus WHERE t.ticketType.event.id = :eventId AND t.status = :currentStatus")
    int bulkUpdateStatusByEventId(
            @Param("eventId") UUID eventId,
            @Param("currentStatus") TicketStatusEnum currentStatus,
            @Param("newStatus") TicketStatusEnum newStatus
    );
}
