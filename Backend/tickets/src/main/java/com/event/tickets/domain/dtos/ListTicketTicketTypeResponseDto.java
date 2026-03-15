package com.event.tickets.domain.dtos;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** FIX #15: price changed from Double to BigDecimal. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListTicketTicketTypeResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
}