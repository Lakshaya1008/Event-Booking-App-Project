package com.event.tickets.repositories;

import com.event.tickets.domain.entities.TicketType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

  @Query("SELECT tt FROM TicketType tt WHERE tt.id = :id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<TicketType> findByIdWithLock(@Param("id") UUID id);

  // Find ticket type by ID and event ID (for authorization checks)
  Optional<TicketType> findByIdAndEventId(UUID ticketTypeId, UUID eventId);
}
