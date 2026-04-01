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
            List<Ticket> ticketsNeedingUpdate = ticketRepository.findTicketsMissingPricingData();

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

            // L-08 FIX: only save tickets that were actually updated (not skipped via continue).
            // saveAll(ticketsNeedingUpdate) was issuing UPDATE for every ticket in the list,
            // including ones skipped when ticketType/price was null.
            List<Ticket> actuallyUpdated = ticketsNeedingUpdate.stream()
                    .filter(t -> t.getPricePaid() != null && t.getOriginalPrice() != null
                            && t.getDiscountApplied() != null)
                    .toList();
            ticketRepository.saveAll(actuallyUpdated);
            log.info("Successfully backfilled pricing data for {} tickets", updatedCount);

        } catch (Exception ex) {
            log.error("Failed to initialize ticket pricing data", ex);
            // Don't throw — let application start even if backfill fails
        }
    }
}