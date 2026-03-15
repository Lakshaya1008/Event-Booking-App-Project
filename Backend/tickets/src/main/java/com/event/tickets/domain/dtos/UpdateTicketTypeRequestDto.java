package com.event.tickets.domain.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** FIX #15: price changed from Double to BigDecimal. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTicketTypeRequestDto {

    private UUID id;

    @NotBlank(message = "Ticket type name is required")
    private String name;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be zero or greater")
    private BigDecimal price;

    private String description;

    private Integer totalAvailable;
}