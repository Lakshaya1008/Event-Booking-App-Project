package com.event.tickets.repositories;

import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByTicketIdAndTicketPurchaserId(UUID ticketId, UUID ticketPurchaseId);

    Optional<QrCode> findByIdAndStatus(UUID id, QrCodeStatusEnum status);

    /**
     * FIX #4: New query to look up the active QrCode record for a given ticket.
     *
     * ROOT CAUSE of the original bug:
     * generateQrCodePngForViewing/Download/Pdf() generated a new QR image encoding
     * ticket.getId() — but the QrCode entity stored a different random UUID as its
     * own ID. The validator received ticket.getId() from the scan and searched for
     * a QrCode with that ID — finding nothing, because no QrCode.id == ticket.id.
     *
     * FIX: Download/view endpoints now call this method to get the stored QrCode,
     * then encode qrCode.getId() into the image — the exact UUID the validator
     * will receive when the ticket is scanned at the door.
     */
    Optional<QrCode> findByTicketIdAndStatus(UUID ticketId, QrCodeStatusEnum status);
}