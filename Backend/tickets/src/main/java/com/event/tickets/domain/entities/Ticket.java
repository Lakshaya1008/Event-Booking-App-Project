package com.event.tickets.domain.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

@Entity
@Table(name = "tickets")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  private TicketStatusEnum status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "ticket_type_id")
  private TicketType ticketType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "purchaser_id")
  private User purchaser;

  /**
   * Original base price of the ticket type at time of purchase (before discount).
   * Stored for historical accuracy and audit trail.
   * Nullable for backward compatibility with existing tickets (will be backfilled).
   */
  @Column(name = "original_price", precision = 10, scale = 2)
  private BigDecimal originalPrice;

  /**
   * Final price paid by customer after discount applied.
   * This is the actual amount charged.
   * For legacy tickets without pricing info, defaults to ticket type price.
   * TEMPORARILY NULLABLE to allow schema migration on existing data.
   */
  @Column(name = "price_paid", precision = 10, scale = 2)
  private BigDecimal pricePaid;

  /**
   * Amount discounted from original price (0 if no discount applied).
   * Formula: discountApplied = originalPrice - pricePaid
   * Stored explicitly for audit and reporting purposes.
   * Nullable for backward compatibility (will be backfilled as 0).
   */
  @Column(name = "discount_applied", precision = 10, scale = 2)
  @Builder.Default
  private BigDecimal discountApplied = BigDecimal.ZERO;

  @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
  @Builder.Default
  private List<TicketValidation> validations = new ArrayList<>();

  @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL)
  @Builder.Default
  private List<QrCode> qrCodes = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", updatable = false, nullable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Ticket ticket = (Ticket) o;
    return Objects.equals(id, ticket.id) && status == ticket.status && Objects.equals(createdAt,
        ticket.createdAt) && Objects.equals(updatedAt, ticket.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, status, createdAt, updatedAt);
  }
}
