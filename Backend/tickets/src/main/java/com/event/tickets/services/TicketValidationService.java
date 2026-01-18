package com.event.tickets.services;

import com.event.tickets.domain.entities.TicketValidation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketValidationService {
  TicketValidation validateTicketByQrCode(UUID userId, UUID qrCodeId);
  TicketValidation validateTicketManually(UUID userId, UUID ticketId);

  // Staff listing operations - now with authorization
  Page<TicketValidation> listValidationsForEvent(UUID userId, UUID eventId, Pageable pageable);
  List<TicketValidation> getValidationsByTicket(UUID userId, UUID ticketId);
}
