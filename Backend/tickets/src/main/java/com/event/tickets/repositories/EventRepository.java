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

    @Query("""
        SELECT COUNT(s) > 0 FROM Event e JOIN e.staff s
        WHERE e.id = :eventId AND s.id = :userId
        """)
    boolean isStaffMember(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId
    );

    @Query("""
        SELECT new com.event.tickets.domain.dtos.StaffMemberDto(s.id, s.name, s.email)
        FROM Event e JOIN e.staff s
        WHERE e.id = :eventId
        ORDER BY s.name
        """)
    List<StaffMemberDto> findStaffByEventId(@Param("eventId") UUID eventId);

    @Query("SELECT e.name FROM Event e WHERE e.id = :eventId")
    Optional<String> findEventNameById(@Param("eventId") UUID eventId);
}