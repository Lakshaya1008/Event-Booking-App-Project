package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.QrCodeRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketValidationRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.TicketValidationService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Ticket Validation Service Implementation
 *
 * This service handles ticket validation operations with centralized authorization.
 * All authorization is delegated to AuthorizationService - this service contains
 * NO authorization logic, only business logic.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

  private final QrCodeRepository qrCodeRepository;
  private final TicketValidationRepository ticketValidationRepository;
  private final TicketRepository ticketRepository;
  private final EventRepository eventRepository;
  private final AuthorizationService authorizationService;

  @Override
  public TicketValidation validateTicketByQrCode(UUID userId, UUID qrCodeId) {
    QrCode qrCode = qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE)
        .orElseThrow(() -> new QrCodeNotFoundException(
            String.format(
                "QR Code with ID %s was not found", qrCodeId
            )
        ));

    Ticket ticket = qrCode.getTicket();
    Event event = ticket.getTicketType().getEvent();

    // Centralized authorization: user must be organizer or staff
    authorizationService.requireOrganizerOrStaffAccess(userId, event);

    return validateTicket(ticket, TicketValidationMethod.QR_SCAN);
  }

  @Override
  public TicketValidation validateTicketManually(UUID userId, UUID ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId)
        .orElseThrow(TicketNotFoundException::new);

    Event event = ticket.getTicketType().getEvent();

    // Centralized authorization: user must be organizer or staff
    authorizationService.requireOrganizerOrStaffAccess(userId, event);

    return validateTicket(ticket, TicketValidationMethod.MANUAL);
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

  // Staff listing operations
  @Override
  public Page<TicketValidation> listValidationsForEvent(UUID userId, UUID eventId, Pageable pageable) {
    Event event = eventRepository.findById(eventId)
        .orElseThrow(() -> new EventNotFoundException(
            String.format("Event with ID '%s' not found", eventId)
        ));

    // Centralized authorization: user must be organizer or staff
    authorizationService.requireOrganizerOrStaffAccess(userId, event);

    return ticketValidationRepository.findByTicketTicketTypeEventId(eventId, pageable);
  }

  @Override
  public List<TicketValidation> getValidationsByTicket(UUID userId, UUID ticketId) {
    Ticket ticket = ticketRepository.findById(ticketId)
        .orElseThrow(TicketNotFoundException::new);

    Event event = ticket.getTicketType().getEvent();

    // Centralized authorization: user must be organizer or staff
    authorizationService.requireOrganizerOrStaffAccess(userId, event);

    return ticketValidationRepository.findByTicketId(ticketId);
  }
}
