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

    /**
     * FIX-TV1 (BUG 6-2): EXISTS-style check for prior VALID scan.
     *
     * BEFORE: TicketValidationServiceImpl.validateTicket() called
     * ticket.getValidations().stream().filter(VALID).findFirst() — loading ALL
     * TicketValidation records for the ticket into memory just to check for a prior scan.
     *
     * AFTER: This single EXISTS query replaces the collection load.
     * Spring Data JPA generates: SELECT count(*) > 0 FROM ticket_validations
     * WHERE ticket_id = ? AND status = ?
     *
     * Called in TicketValidationServiceImpl.validateTicket() to determine
     * whether the current scan is the first VALID scan or a duplicate.
     *
     * Returns true if a VALID validation record already exists for this ticket.
     */
    boolean existsByTicketIdAndStatus(UUID ticketId, TicketValidationStatusEnum status);
}