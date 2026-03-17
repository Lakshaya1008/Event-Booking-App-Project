package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketValidationMethod;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * H-03 FIX: @NotNull added to both id and method.
 *
 * Without validation on method, a request body of {} or {"id":"..."} would
 * deserialize method as null. The controller's MANUAL.equals(method) check
 * would fall through to the else-branch and call validateTicketByQrCode with
 * a null id — causing a confusing QrCodeNotFoundException(null) instead of
 * a clear HTTP 400 Bad Request. With @NotNull, Spring returns a proper
 * validation error before the controller body executes.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketValidationRequestDto {

    @NotNull(message = "Ticket or QR code ID is required")
    private UUID id;

    @NotNull(message = "Validation method is required (MANUAL or QR_SCAN)")
    private TicketValidationMethod method;
}