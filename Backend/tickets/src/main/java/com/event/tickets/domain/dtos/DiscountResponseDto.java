package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for discount information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscountResponseDto {

  private UUID id;
  private UUID ticketTypeId;
  private String ticketTypeName;
  private DiscountType discountType;
  private BigDecimal value;
  private LocalDateTime validFrom;
  private LocalDateTime validTo;
  private Boolean active;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
