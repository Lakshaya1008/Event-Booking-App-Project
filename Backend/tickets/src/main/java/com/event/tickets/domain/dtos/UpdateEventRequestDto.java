package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.EventStatusEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateEventRequestDto {

    // ID sourced from URL path parameter — optional in body
    private UUID id;

    @NotBlank(message = "Event name is required")
    @Size(max = 200, message = "Event name must not exceed 200 characters")
    private String name;

    private LocalDateTime start;

    private LocalDateTime end;

    @NotBlank(message = "Venue information is required")
    @Size(max = 500, message = "Venue must not exceed 500 characters")
    private String venue;

    private LocalDateTime salesStart;

    private LocalDateTime salesEnd;

    @NotNull(message = "Event status must be provided")
    private EventStatusEnum status;

    @Min(value = 1, message = "maxCapacity must be at least 1 if provided")
    private Integer maxCapacity;

    @NotEmpty(message = "At least one ticket type is required")
    @Valid
    private List<UpdateTicketTypeRequestDto> ticketTypes;
}