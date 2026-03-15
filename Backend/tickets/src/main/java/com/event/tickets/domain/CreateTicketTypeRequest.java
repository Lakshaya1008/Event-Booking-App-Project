package com.event.tickets.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** FIX #15: price changed from Double to BigDecimal for financial precision. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTicketTypeRequest {
    private String name;
    private BigDecimal price;
    private String description;
    private Integer totalAvailable;
}