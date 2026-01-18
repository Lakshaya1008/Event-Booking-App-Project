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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Audit Log Entity
 *
 * IMMUTABLE audit trail for all sensitive operations.
 *
 * Security:
 * - Append-only (no updates or deletes)
 * - Read-only after creation
 * - Complete audit trail for compliance
 */
@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "action", nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  private AuditAction action;

  @ManyToOne
  @JoinColumn(name = "actor_id", nullable = false)
  private User actor;

  @ManyToOne
  @JoinColumn(name = "target_user_id")
  private User targetUser;

  @ManyToOne
  @JoinColumn(name = "event_id")
  private Event event;

  @Column(name = "resource_type", length = 50)
  private String resourceType;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "details", length = 1000)
  private String details;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;
}
