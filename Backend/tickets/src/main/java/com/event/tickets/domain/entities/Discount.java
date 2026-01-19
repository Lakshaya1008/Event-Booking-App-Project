package com.event.tickets.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Represents a discount that can be applied to a ticket type.
 *
 * <p><strong>Business Rules:</strong>
 * <ul>
 *   <li>Only ONE active discount per ticket type at a time (enforced by unique index)</li>
 *   <li>Discounts apply at purchase time only (never retroactive)</li>
 *   <li>Organizer must own both event and ticket type (enforced in service layer)</li>
 *   <li>Discount type: PERCENTAGE (0-100%) or FIXED_AMOUNT (currency)</li>
 * </ul>
 *
 * <p><strong>Design Notes:</strong>
 * <ul>
 *   <li>Using single entity with enum to avoid invalid states (better than nullable fields)</li>
 *   <li>Validity period enforced by CHECK constraint (valid_to > valid_from)</li>
 *   <li>Percentage range enforced by CHECK constraint (0 < value <= 100)</li>
 *   <li>Cascade DELETE when ticket type is deleted (cleanup)</li>
 * </ul>
 */
@Entity
@Table(name = "discounts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * The ticket type this discount applies to.
   * Lazy-loaded to avoid unnecessary joins.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticket_type_id", nullable = false)
  private TicketType ticketType;

  /**
   * Type of discount: PERCENTAGE or FIXED_AMOUNT.
   * Determines how {@link #value} is interpreted.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "discount_type", nullable = false, length = 20)
  private DiscountType discountType;

  /**
   * Discount value:
   * - PERCENTAGE: 0-100 (e.g., 20.00 = 20% off)
   * - FIXED_AMOUNT: currency amount (e.g., 10.00 = $10 off)
   *
   * Must be positive. For PERCENTAGE, must be <= 100 (enforced by CHECK constraint).
   */
  @Column(name = "value", nullable = false, precision = 10, scale = 2)
  private BigDecimal value;

  /**
   * Start date/time when discount becomes valid.
   */
  @Column(name = "valid_from", nullable = false)
  private LocalDateTime validFrom;

  /**
   * End date/time when discount expires.
   * Must be after {@link #validFrom} (enforced by CHECK constraint).
   */
  @Column(name = "valid_to", nullable = false)
  private LocalDateTime validTo;

  /**
   * Whether discount is currently active.
   * Only ONE active discount per ticket type allowed (enforced by unique partial index).
   */
  @Column(name = "active", nullable = false)
  @Builder.Default
  private boolean active = true;

  /**
   * Optional human-readable description of the discount.
   * Example: "Early bird discount", "VIP member special"
   */
  @Column(name = "description", length = 500)
  private String description;

  /**
   * Optional: UUID of organizer who created this discount.
   * Used for audit trail (not a foreign key).
   */
  @Column(name = "created_by")
  private UUID createdBy;

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  /**
   * Checks if discount is currently valid based on current time and active status.
   *
   * @param now Current timestamp
   * @return true if discount is active and within validity period
   */
  public boolean isValidAt(LocalDateTime now) {
    return active
        && !now.isBefore(validFrom)
        && !now.isAfter(validTo);
  }

  /**
   * Calculates final price after applying this discount.
   *
   * @param basePrice Original ticket price
   * @return Final price after discount (never negative)
   */
  public BigDecimal calculateFinalPrice(BigDecimal basePrice) {
    if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Base price must be positive");
    }

    BigDecimal discountAmount;

    if (discountType == DiscountType.PERCENTAGE) {
      // Calculate percentage discount
      discountAmount = basePrice.multiply(value).divide(
          BigDecimal.valueOf(100),
          2,
          java.math.RoundingMode.HALF_UP
      );
    } else {
      // Fixed amount discount
      discountAmount = value;
    }

    BigDecimal finalPrice = basePrice.subtract(discountAmount);

    // Ensure price never goes negative
    return finalPrice.max(BigDecimal.ZERO);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Discount discount = (Discount) o;
    return Objects.equals(id, discount.id)
        && discountType == discount.discountType
        && Objects.equals(value, discount.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, discountType, value);
  }

  @Override
  public String toString() {
    return "Discount{" +
        "id=" + id +
        ", discountType=" + discountType +
        ", value=" + value +
        ", active=" + active +
        ", validFrom=" + validFrom +
        ", validTo=" + validTo +
        '}';
  }
}
