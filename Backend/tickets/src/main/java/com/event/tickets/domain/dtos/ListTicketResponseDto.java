package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.TicketStatusEnum;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListTicketResponseDto {
  private UUID id;
  private TicketStatusEnum status;
  private ListTicketTicketTypeResponseDto ticketType;
}
