package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketValidationMethod;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketValidationRequestDto {

    @NotNull(message = "Ticket or QR code ID is required")
    private UUID id;

    @NotNull(message = "Validation method is required (MANUAL or QR_SCAN)")
    private TicketValidationMethod method;
}