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
 * Runs AFTER application context is fully initialized (including schema updates).
 * This component is IDEMPOTENT — safe to run multiple times.
 *
 * FIX #15 follow-up: TicketType.price is now BigDecimal (was Double).
 * The original line 66 called BigDecimal.valueOf(ticket.getTicketType().getPrice())
 * which compiled when getPrice() returned Double, but fails when it returns BigDecimal
 * because BigDecimal.valueOf(BigDecimal) does not exist.
 * Fixed to use ticket.getTicketType().getPrice() directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class DataInitializer implements ApplicationRunner {

    private final TicketRepository ticketRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting ticket pricing data initialization...");

        try {
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

                if (ticket.getPricePaid() == null) {
                    if (ticket.getTicketType() != null && ticket.getTicketType().getPrice() != null) {
                        // FIX #15 follow-up: getPrice() now returns BigDecimal directly.
                        // The old code used BigDecimal.valueOf(getPrice()) which worked when
                        // getPrice() returned Double but is a compile error for BigDecimal.
                        BigDecimal priceFromType = ticket.getTicketType().getPrice();
                        ticket.setPricePaid(priceFromType);
                        updated = true;
                    } else {
                        log.warn("Cannot backfill pricePaid for ticket {} - ticket type or price is null",
                                ticket.getId());
                        continue;
                    }
                }

                if (ticket.getOriginalPrice() == null) {
                    ticket.setOriginalPrice(ticket.getPricePaid());
                    updated = true;
                }

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
            // Don't throw — let application start even if backfill fails
        }
    }
}