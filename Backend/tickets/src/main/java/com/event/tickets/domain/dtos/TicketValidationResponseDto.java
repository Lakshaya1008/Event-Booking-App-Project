package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketValidationResponseDto {
    private UUID ticketId;
    private TicketValidationStatusEnum status;

    /** ID of the staff member / organizer who scanned this ticket. */
    private UUID validatedById;

    /** Display name of the scanner — useful for the frontend without a second request. */
    private String validatedByName;

    /** When this validation record was created. */
    private LocalDateTime validatedAt;
}
