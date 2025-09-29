package com.event.tickets.domain.dtos;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateTicketTypeResponseDto {

  private UUID id;
  private String name;
  private Double price;
  private String description;
  private Integer totalAvailable;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

}
