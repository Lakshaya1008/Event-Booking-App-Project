package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** FIX #15: price changed from Double to BigDecimal. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetTicketResponseDto {
    private UUID id;
    private TicketStatusEnum status;
    private BigDecimal price;
    private String description;
    private String eventName;
    private String eventVenue;
    private LocalDateTime eventStart;
    private LocalDateTime eventEnd;
}