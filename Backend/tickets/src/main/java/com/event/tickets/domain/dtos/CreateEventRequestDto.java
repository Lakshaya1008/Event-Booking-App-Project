package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.EventStatusEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
  private String name;

  private LocalDateTime start;

  private LocalDateTime end;

  @NotBlank(message = "Venue information is required")
  private String venue;

  private LocalDateTime salesStart;

  private LocalDateTime salesEnd;

  @NotNull(message = "Event status must be provided")
  private EventStatusEnum status;

  @NotEmpty(message = "At least one ticket type is required")
  @Valid
  private List<CreateTicketTypeRequestDto> ticketTypes;
}
