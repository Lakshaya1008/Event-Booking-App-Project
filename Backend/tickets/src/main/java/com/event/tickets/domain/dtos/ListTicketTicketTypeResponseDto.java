package com.event.tickets.domain.dtos;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListTicketTicketTypeResponseDto {
  private UUID id;
  private String name;
  private Double price;

}
