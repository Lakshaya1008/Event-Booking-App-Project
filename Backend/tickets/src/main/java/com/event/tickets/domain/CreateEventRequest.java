package com.event.tickets.domain;

import com.event.tickets.domain.entities.EventStatusEnum;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateEventRequest {

  private String name;
  private LocalDateTime start;
  private LocalDateTime end;
  private String venue;
  private LocalDateTime salesStart;
  private LocalDateTime salesEnd;
  private EventStatusEnum status;
  private List<CreateTicketTypeRequest> ticketTypes = new ArrayList<>();
}
