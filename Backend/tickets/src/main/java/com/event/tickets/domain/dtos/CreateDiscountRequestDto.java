package com.event.tickets.domain.dtos;

import com.event.tickets.domain.entities.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a discount.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDiscountRequestDto {

  @NotNull(message = "Discount type is required")
  private DiscountType discountType;

  @NotNull(message = "Discount value is required")
  @DecimalMin(value = "0.01", message = "Discount value must be positive")
  private BigDecimal value;

  @NotNull(message = "Valid from date is required")
  @FutureOrPresent(message = "Valid from date must be in present or future")
  private LocalDateTime validFrom;

  @NotNull(message = "Valid to date is required")
  @Future(message = "Valid to date must be in the future")
  private LocalDateTime validTo;

  private Boolean active;

  private String description;
}
