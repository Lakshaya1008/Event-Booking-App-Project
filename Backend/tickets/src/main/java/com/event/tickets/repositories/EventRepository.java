package com.event.tickets.repositories;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    Page<Event> findByStatus(EventStatusEnum status, Pageable pageable);

    /**
     * FIX-AZ1: Replaces event.getStaff().stream().anyMatch() in AuthorizationServiceImpl.
     *
     * Before: Loading the full staff collection into JPA session to check a single membership.
     * After:  A single COUNT EXISTS query — returns immediately, no entities loaded into memory.
     *
     * This is called on every ticket validation and every staff-gated API call.
     * The performance difference is significant for events with large staff lists.
     */
    @Query("SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s WHERE e.id = :eventId AND s.id = :userId")
    boolean isStaffMember(@Param("eventId") UUID eventId, @Param("userId") UUID userId);
}