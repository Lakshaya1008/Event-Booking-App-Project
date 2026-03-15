package com.event.tickets.domain.dtos;

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
public class GetEventDetailsTicketTypesResponseDto {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}