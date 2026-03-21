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
 *
 * FIX D-5: createdBy field added.
 *
 *   BEFORE: The Discount entity has a createdBy UUID field (the organizer who created it)
 *   stored for audit purposes. This field was never exposed in the response — an admin
 *   or organizer viewing discount history had no way to know which organizer created a
 *   given discount via the API.
 *
 *   FIX: createdBy is now mapped from Discount.createdBy and returned in all discount
 *   responses. The DiscountMapper @Mapping is added to populate it.
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

    /**
     * FIX D-5: UUID of the organizer who created this discount.
     * Useful for audit trails and admin views showing which organizer set up each discount.
     */
    private UUID createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}