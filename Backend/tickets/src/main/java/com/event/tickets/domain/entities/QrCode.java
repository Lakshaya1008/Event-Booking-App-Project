package com.event.tickets.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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
 * FIXES APPLIED:
 *
 * FIX-QR1 (BUG 5-3) — qr_value column changed from VARCHAR(1000) to TEXT (@Lob).
 *
 *   ROOT CAUSE:
 *   QrCodeServiceImpl.generateQrCode() stores a base64-encoded PNG image in qr_value.
 *   A 300×300 QR code PNG is ~8–15KB binary = ~11,000–20,000 characters when base64-encoded.
 *   The original @Column(length = 1000) defined a VARCHAR(1000) column in PostgreSQL.
 *   JPA/Hibernate silently truncated the value at 1000 characters on every save.
 *   When getQrCodeImageForUserAndTicket() later called Base64.getDecoder().decode(qrCode.getValue()),
 *   the truncated base64 string caused an IllegalArgumentException — caught and re-thrown as
 *   QrCodeNotFoundException. Every legacy QR download attempt silently failed.
 *
 *   FIX: @Lob annotation maps the column to PostgreSQL TEXT (unbounded).
 *   The length = 1000 constraint is removed entirely.
 *
 *   MIGRATION NOTE:
 *   Requires a schema migration to change the column type:
 *     ALTER TABLE qr_codes ALTER COLUMN qr_value TYPE TEXT;
 *   Existing truncated rows will need to be regenerated — they are invalid base64.
 *   Add a V5 migration: ALTER TABLE + mark existing rows for re-generation
 *   (set status = 'EXPIRED' on all existing rows so they are regenerated on next access).
 */
@Entity
@Table(name = "qr_codes")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QrCode {

    /**
     * ID is set manually by QrCodeServiceImpl before save.
     * This UUID is encoded into the QR image — it is what the scanner reads back.
     * Never save a QrCode without calling qrCode.setId() first.
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private QrCodeStatusEnum status;

    /**
     * FIX-QR1: @Lob maps to PostgreSQL TEXT — unbounded.
     *
     * Stores a base64-encoded PNG of the QR code image.
     * A 300×300 QR PNG base64-encodes to ~11,000–20,000 characters.
     * The previous VARCHAR(1000) silently truncated every image on save.
     */
    @Lob
    @Column(name = "qr_value", nullable = false)
    private String value;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QrCode qrCode = (QrCode) o;
        return Objects.equals(id, qrCode.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}