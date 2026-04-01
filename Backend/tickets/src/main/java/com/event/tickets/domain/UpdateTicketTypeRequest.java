package com.event.tickets.domain;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketTypeRequest {
    private UUID id;
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
}