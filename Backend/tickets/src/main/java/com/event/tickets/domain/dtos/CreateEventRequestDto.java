package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.EventStatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateEventRequestDto {

    @NotBlank(message = "Event name is required")
    @Size(max = 200, message = "Event name must not exceed 200 characters")
    private String name;

    @NotNull(message = "Event start date is required")
    private LocalDateTime start;

    @NotNull(message = "Event end date is required")
    private LocalDateTime end;

    @NotBlank(message = "Venue information is required")
    @Size(max = 500, message = "Venue must not exceed 500 characters")
    private String venue;

    /** Optional — if null, tickets are purchasable from event creation. */
    private LocalDateTime salesStart;

    /** Optional — if null, tickets are purchasable until the event starts. */
    private LocalDateTime salesEnd;

    @Min(value = 1, message = "maxCapacity must be at least 1 if provided")
    private Integer maxCapacity;

    @NotEmpty(message = "At least one ticket type is required")
    @Valid
    private List<CreateTicketTypeRequestDto> ticketTypes;

    @AssertTrue(message = "Event end date must be after start date")
    public boolean isEndAfterStart() {
        if (start == null || end == null) return true; // individual @NotNull handles nulls
        return end.isAfter(start);
    }

    @AssertTrue(message = "Sales end date must be after sales start date")
    public boolean isSalesEndAfterSalesStart() {
        if (salesStart == null || salesEnd == null) return true;
        return salesEnd.isAfter(salesStart);
    }
}