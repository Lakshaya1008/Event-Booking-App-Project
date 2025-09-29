package com.event.tickets.repositories;

import com.event.tickets.domain.entities.Ticket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

  int countByTicketTypeId(UUID ticketTypeId);

  Page<Ticket> findByPurchaserId(UUID purchaserId, Pageable pageable);

  Optional<Ticket> findByIdAndPurchaserId(UUID id, UUID purchaserId);
}
