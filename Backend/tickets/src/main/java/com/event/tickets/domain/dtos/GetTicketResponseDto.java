package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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