package com.event.tickets.repositories;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

    Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

    Page<Event> findByStatus(EventStatusEnum status, Pageable pageable);

    @Query(value = "SELECT * FROM events WHERE " +
            "status = 'PUBLISHED' AND " +
            "to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(venue, '')) " +
            "@@ plainto_tsquery('english', :searchTerm)",
            countQuery = "SELECT count(*) FROM events WHERE " +
                    "status = 'PUBLISHED' AND " +
                    "to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(venue, '')) " +
                    "@@ plainto_tsquery('english', :searchTerm)",
            nativeQuery = true)
    Page<Event> searchEvents(@Param("searchTerm") String searchTerm, Pageable pageable);

    Optional<Event> findByIdAndStatus(UUID id, EventStatusEnum status);

    /**
     * FIX-AZ1 (from previous audit): EXISTS query for staff membership check.
     * Replaces event.getStaff().stream().anyMatch() which loaded the full collection.
     */
    @Query("SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s WHERE e.id = :eventId AND s.id = :userId")
    boolean isStaffMember(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    /**
     * FIX-E5: Finds PUBLISHED events whose end date is in the past.
     * Used by the auto-complete scheduler in EventServiceImpl to transition
     * these events to COMPLETED status.
     */
    List<Event> findByStatusAndEndBefore(EventStatusEnum status, LocalDateTime cutoff);
}