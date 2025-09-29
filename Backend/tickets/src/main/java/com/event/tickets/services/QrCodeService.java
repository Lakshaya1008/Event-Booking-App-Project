package com.event.tickets.services;

import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.Ticket;
import java.util.UUID;

public interface QrCodeService {

  QrCode generateQrCode(Ticket ticket);

  byte[] getQrCodeImageForUserAndTicket(UUID userId, UUID ticketId);
}
