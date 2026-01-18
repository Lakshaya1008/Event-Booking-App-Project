package com.event.tickets.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Invite Code Entity
 *
 * Represents a single-use, time-bound invite code for role assignment.
 *
 * Lifecycle:
 * 1. PENDING - Code generated, not yet redeemed
 * 2. REDEEMED - Code used successfully
 * 3. EXPIRED - Code past expiration time
 * 4. REVOKED - Code manually revoked before use
 *
 * Audit Trail:
 * - createdBy: User who generated the code
 * - createdAt: When code was generated
 * - redeemedBy: User who redeemed the code
 * - redeemedAt: When code was redeemed
 */
@Entity
@Table(name = "invite_codes")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteCode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true, length = 32)
  private String code;

  @Column(name = "role_name", nullable = false, length = 50)
  private String roleName;

  @ManyToOne
  @JoinColumn(name = "event_id")
  private Event event; // Null for global roles, set for event-staff invites

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private InviteCodeStatus status;

  @ManyToOne
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  @ManyToOne
  @JoinColumn(name = "redeemed_by")
  private User redeemedBy;

  @Column(name = "redeemed_at")
  private LocalDateTime redeemedAt;

  @Column(name = "revoked_at")
  private LocalDateTime revokedAt;

  @Column(name = "revoked_reason")
  private String revokedReason;

  @Version
  @Column(name = "version")
  private Long version;

  /**
   * Checks if the invite code is valid for redemption.
   *
   * @return true if code is PENDING and not expired
   */
  public boolean isValid() {
    return status == InviteCodeStatus.PENDING
        && LocalDateTime.now().isBefore(expiresAt);
  }

  /**
   * Marks the invite code as expired if past expiration time.
   */
  public void checkAndMarkExpired() {
    if (status == InviteCodeStatus.PENDING
        && LocalDateTime.now().isAfter(expiresAt)) {
      status = InviteCodeStatus.EXPIRED;
    }
  }
}
