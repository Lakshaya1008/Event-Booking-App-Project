package com.event.tickets.config;

import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.repositories.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * One-time data initializer to backfill pricing columns for existing tickets.
 *
 * <p>Runs AFTER application context is fully initialized (including schema updates) to ensure
 * all columns exist before data migration:
 * <ul>
 *   <li>originalPrice: set to pricePaid if available, otherwise fetch from ticket type</li>
 *   <li>discountApplied: set to 0 (no discount for old tickets)</li>
 *   <li>pricePaid: if null, set to ticket type price</li>
 * </ul>
 *
 * <p>This component is IDEMPOTENT - safe to run multiple times.
 * Uses ApplicationRunner instead of @PostConstruct to run after schema is ready.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1) // Run early but after context initialization
public class DataInitializer implements ApplicationRunner {

  private final TicketRepository ticketRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    log.info("Starting ticket pricing data initialization...");

    try {
      // Find all tickets where pricing data is missing
      List<Ticket> ticketsNeedingUpdate = ticketRepository.findAll().stream()
          .filter(ticket -> ticket.getOriginalPrice() == null
              || ticket.getDiscountApplied() == null
              || ticket.getPricePaid() == null)
          .toList();

      if (ticketsNeedingUpdate.isEmpty()) {
        log.info("No tickets require pricing backfill. All data is up-to-date.");
        return;
      }

      log.info("Found {} tickets requiring pricing backfill", ticketsNeedingUpdate.size());

      int updatedCount = 0;
      for (Ticket ticket : ticketsNeedingUpdate) {
        boolean updated = false;

        // If pricePaid is null, get from ticket type
        if (ticket.getPricePaid() == null) {
          if (ticket.getTicketType() != null && ticket.getTicketType().getPrice() != null) {
            // Convert Double to BigDecimal safely
            BigDecimal priceFromType = BigDecimal.valueOf(ticket.getTicketType().getPrice());
            ticket.setPricePaid(priceFromType);
            updated = true;
          } else {
            log.warn("Cannot backfill pricePaid for ticket {} - ticket type or price is null",
                ticket.getId());
            continue;
          }
        }

        // For legacy tickets without originalPrice, assume no discount was applied
        if (ticket.getOriginalPrice() == null) {
          ticket.setOriginalPrice(ticket.getPricePaid());
          updated = true;
        }

        // For legacy tickets without discountApplied, set to zero
        if (ticket.getDiscountApplied() == null) {
          ticket.setDiscountApplied(BigDecimal.ZERO);
          updated = true;
        }

        if (updated) {
          updatedCount++;
        }
      }

      ticketRepository.saveAll(ticketsNeedingUpdate);

      log.info("Successfully backfilled pricing data for {} tickets", updatedCount);

    } catch (Exception ex) {
      log.error("Failed to initialize ticket pricing data", ex);
      // Don't throw exception - let application start even if backfill fails
    }
  }
}
