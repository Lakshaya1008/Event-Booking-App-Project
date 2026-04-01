package com.event.tickets.repositories;

import com.event.tickets.domain.entities.Discount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID> {

    @Query("""
      SELECT d FROM Discount d
      WHERE d.ticketType.id = :ticketTypeId
      AND d.active = true
      AND d.validFrom <= :now
      AND d.validTo > :now
      """)
    Optional<Discount> findActiveDiscount(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("now") LocalDateTime now
    );

    @Query("""
      SELECT d FROM Discount d
      WHERE d.ticketType.id = :ticketTypeId
      ORDER BY d.createdAt DESC
      """)
    List<Discount> findAllByTicketTypeId(@Param("ticketTypeId") UUID ticketTypeId);

    @Query("""
      SELECT d FROM Discount d
      JOIN d.ticketType tt
      WHERE tt.event.id = :eventId
      AND d.active = true
      AND d.validFrom <= :now
      AND d.validTo > :now
      ORDER BY tt.name, d.validFrom
      """)
    List<Discount> findActiveDiscountsByEventId(
            @Param("eventId") UUID eventId,
            @Param("now") LocalDateTime now
    );

    @Query("""
      SELECT COUNT(d) > 0 FROM Discount d
      WHERE d.ticketType.id = :ticketTypeId
      AND d.active = true
      AND d.validTo > :now
      """)
    boolean existsActiveDiscountForTicketType(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("now") LocalDateTime now
    );

    List<Discount> findByCreatedByOrderByCreatedAtDesc(UUID organizerId);
}