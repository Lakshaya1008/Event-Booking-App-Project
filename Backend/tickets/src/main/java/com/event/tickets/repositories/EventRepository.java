package com.event.tickets.repositories;

import com.event.tickets.domain.dtos.StaffMemberDto;
import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.EventStatusEnum;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithLock(@Param("id") UUID id);

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

    @Query("SELECT e FROM Event e WHERE e.status = :status AND e.end < :before")
    List<Event> findByStatusAndEndBefore(
            @Param("status") EventStatusEnum status,
            @Param("before") java.time.LocalDateTime before
    );

    /**
     * FIX S-5 (BUG S-5): EXISTS-style staff membership check.
     *
     * BEFORE: isStaffAssignedToEvent() in EventStaffServiceImpl loaded the full
     * event.staff @ManyToMany collection just to call anyMatch(s -> s.getId().equals(userId)).
     * For an event with 50 staff members, 50 full User entities were loaded to answer
     * a yes/no question.
     *
     * AFTER: Single COUNT query against the user_staffing_events join table.
     * Returns true if a row exists for (event_id, user_id) — zero entities loaded.
     *
     * Also used by AuthorizationServiceImpl.isStaff() (from the Feature 6 fix) so
     * every staff authorization check at ticket validation time is also efficient.
     */
    @Query("""
        SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s
        WHERE e.id = :eventId AND s.id = :userId
        """)
    boolean isStaffMember(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId
    );

    /**
     * FIX S-4 (BUG S-4): Projection query — returns only id, name, email from the
     * staff collection. Zero full User entities loaded into the JPA session.
     *
     * BEFORE: listEventStaff() called event.getStaff() which lazy-loaded all User
     * entities (all columns) just to map three fields. For 50 staff members, 50
     * complete User rows were fetched when only 3 columns were needed per row.
     *
     * AFTER: JPQL JOIN directly into the staff association, selecting only the three
     * fields needed for StaffMemberDto. Spring Data JPA constructs the DTOs directly
     * via the constructor expression NEW.
     *
     * Note: StaffMemberDto must have a matching (UUID, String, String) constructor.
     * It already does — see StaffMemberDto.java.
     *
     * FIX S-1 (BUG S-1): This query also removes one of the redundant DB loads in the
     * assign/remove flow — the controller now gets the full updated staff list from
     * the service return value rather than calling listEventStaff() separately.
     */
    @Query("""
        SELECT new com.event.tickets.domain.dtos.StaffMemberDto(s.id, s.name, s.email)
        FROM Event e JOIN e.staff s
        WHERE e.id = :eventId
        ORDER BY s.name
        """)
    List<StaffMemberDto> findStaffByEventId(@Param("eventId") UUID eventId);

    /**
     * FIX S-8 (BUG S-8): Returns only the event name — no full entity load.
     *
     * BEFORE: getEventName() in EventStaffServiceImpl called eventRepository.findById()
     * and then accessed .getName() — loading all event columns just for a single string.
     *
     * AFTER: JPQL SELECT of only the name field. The controller no longer calls
     * getEventName() separately — the service includes it in the returned DTO.
     * This query is retained for any other caller who genuinely needs only the name.
     */
    @Query("SELECT e.name FROM Event e WHERE e.id = :eventId")
    Optional<String> findEventNameById(@Param("eventId") UUID eventId);
}