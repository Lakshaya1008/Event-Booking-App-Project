package com.event.tickets.repositories;

import com.event.tickets.domain.entities.TicketValidation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketValidationRepository extends JpaRepository<TicketValidation, UUID> {

  // Find validations by event ID through ticket type relationship
  Page<TicketValidation> findByTicketTicketTypeEventId(UUID eventId, Pageable pageable);

  // Find validations by ticket ID
  List<TicketValidation> findByTicketId(UUID ticketId);
}
