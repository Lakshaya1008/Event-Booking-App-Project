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

/**
 * Repository for managing {@link Discount} entities.
 *
 * <p>Provides queries for finding active discounts and enforcing business rules.
 */
@Repository
public interface DiscountRepository extends JpaRepository<Discount, UUID> {

  /**
   * Finds the currently active and valid discount for a ticket type.
   *
   * <p>A discount is considered active if:
   * <ul>
   *   <li>active flag is true</li>
   *   <li>current time is within validity period (validFrom <= now < validTo)</li>
   * </ul>
   *
   * <p>Due to unique index constraint, there can only be ONE active discount
   * per ticket type at a time.
   *
   * @param ticketTypeId ID of the ticket type
   * @param now Current timestamp
   * @return Optional containing the active discount, or empty if none found
   */
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

  /**
   * Finds all discounts (active and inactive) for a ticket type.
   *
   * @param ticketTypeId ID of the ticket type
   * @return List of discounts, ordered by created_at DESC
   */
  @Query("""
      SELECT d FROM Discount d 
      WHERE d.ticketType.id = :ticketTypeId 
      ORDER BY d.createdAt DESC
      """)
  List<Discount> findAllByTicketTypeId(@Param("ticketTypeId") UUID ticketTypeId);

  /**
   * Finds all currently active discounts for an event.
   * Useful for organizer dashboard showing all active discounts.
   *
   * @param eventId ID of the event
   * @param now Current timestamp
   * @return List of active discounts
   */
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

  /**
   * Checks if there's already an active discount for a ticket type.
   * Used for validation before creating/activating a new discount.
   *
   * @param ticketTypeId ID of the ticket type
   * @return true if an active discount exists
   */
  @Query("""
      SELECT COUNT(d) > 0 FROM Discount d 
      WHERE d.ticketType.id = :ticketTypeId 
      AND d.active = true
      """)
  boolean existsActiveDiscountForTicketType(@Param("ticketTypeId") UUID ticketTypeId);

  /**
   * Finds all discounts created by a specific organizer.
   * Useful for organizer's discount management dashboard.
   *
   * @param organizerId UUID of the organizer
   * @return List of discounts created by the organizer
   */
  List<Discount> findByCreatedByOrderByCreatedAtDesc(UUID organizerId);
}
