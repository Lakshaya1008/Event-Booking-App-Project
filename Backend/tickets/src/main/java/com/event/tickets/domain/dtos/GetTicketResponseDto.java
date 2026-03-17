package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * L-25 FIX: pricePaid, originalPrice, discountApplied added.
 *
 * Previously only the ticket type's base price was exposed. Attendees had no way
 * to see what they actually paid vs the list price, or how much discount was applied.
 * Financial transparency requires showing the actual transaction price.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetTicketResponseDto {
    private UUID id;
    private TicketStatusEnum status;
    private BigDecimal price;          // ticket type base price
    private BigDecimal pricePaid;      // actual amount charged (after discount)
    private BigDecimal originalPrice;  // base price at time of purchase (snapshot)
    private BigDecimal discountApplied; // discount amount (0 if none)
    private String description;
    private String eventName;
    private String eventVenue;
    private LocalDateTime eventStart;
    private LocalDateTime eventEnd;
}