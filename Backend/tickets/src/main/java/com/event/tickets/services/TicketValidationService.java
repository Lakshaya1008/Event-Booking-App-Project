package com.event.tickets.services;

import com.event.tickets.domain.entities.TicketValidation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TicketValidationService {
  TicketValidation validateTicketByQrCode(UUID qrCodeId);
  TicketValidation validateTicketManually(UUID ticketId);

  // Staff listing operations
  Page<TicketValidation> listValidationsForEvent(UUID eventId, Pageable pageable);
  List<TicketValidation> getValidationsByTicket(UUID ticketId);
}
