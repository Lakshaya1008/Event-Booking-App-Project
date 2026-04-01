package com.event.tickets.repositories;

import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketValidationRepository extends JpaRepository<TicketValidation, UUID> {

    Page<TicketValidation> findByTicketTicketTypeEventId(UUID eventId, Pageable pageable);

    List<TicketValidation> findByTicketId(UUID ticketId);

    boolean existsByTicketIdAndStatus(UUID ticketId, TicketValidationStatusEnum status);
}