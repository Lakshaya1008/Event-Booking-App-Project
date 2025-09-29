package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.repositories.QrCodeRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketValidationRepository;
import com.event.tickets.services.TicketValidationService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

  private final QrCodeRepository qrCodeRepository;
  private final TicketValidationRepository ticketValidationRepository;
  private final TicketRepository ticketRepository;

  @Override
  public TicketValidation validateTicketByQrCode(UUID qrCodeId) {
    QrCode qrCode = qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE)
        .orElseThrow(() -> new QrCodeNotFoundException(
            String.format(
                "QR Code with ID %s was not found", qrCodeId
            )
        ));

    Ticket ticket = qrCode.getTicket();

    return validateTicket(ticket, TicketValidationMethod.QR_SCAN);
  }

  private TicketValidation validateTicket(Ticket ticket,
      TicketValidationMethod ticketValidationMethod) {
    TicketValidation ticketValidation = new TicketValidation();
    ticketValidation.setTicket(ticket);
    ticketValidation.setValidationMethod(ticketValidationMethod);

    TicketValidationStatusEnum ticketValidationStatus = ticket.getValidations().stream()
        .filter(v -> TicketValidationStatusEnum.VALID.equals(v.getStatus()))
        .findFirst()
        .map(v -> TicketValidationStatusEnum.INVALID)
        .orElse(TicketValidationStatusEnum.VALID);

    ticketValidation.setStatus(ticketValidationStatus);

    return ticketValidationRepository.save(ticketValidation);
  }

  @Override
  public TicketValidation validateTicketManually(UUID ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId)
        .orElseThrow(TicketNotFoundException::new);
    return validateTicket(ticket, TicketValidationMethod.MANUAL);
  }

  // Staff listing operations
  @Override
  public Page<TicketValidation> listValidationsForEvent(UUID eventId, Pageable pageable) {
    return ticketValidationRepository.findByTicketTicketTypeEventId(eventId, pageable);
  }

  @Override
  public List<TicketValidation> getValidationsByTicket(UUID ticketId) {
    return ticketValidationRepository.findByTicketId(ticketId);
  }
}
