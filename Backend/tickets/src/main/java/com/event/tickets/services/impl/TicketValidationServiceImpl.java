package com.event.tickets.services.impl;

import com.event.tickets.domain.entities.Event;
import com.event.tickets.domain.entities.AuditAction;
import com.event.tickets.domain.entities.AuditLog;
import com.event.tickets.domain.entities.QrCode;
import com.event.tickets.domain.entities.QrCodeStatusEnum;
import com.event.tickets.domain.entities.Ticket;
import com.event.tickets.domain.entities.TicketValidation;
import com.event.tickets.domain.entities.TicketValidationMethod;
import com.event.tickets.domain.entities.TicketValidationStatusEnum;
import com.event.tickets.domain.entities.User;
import com.event.tickets.services.SystemUserProvider;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.repositories.AuditLogRepository;
import com.event.tickets.repositories.EventRepository;
import com.event.tickets.repositories.QrCodeRepository;
import com.event.tickets.repositories.TicketRepository;
import com.event.tickets.repositories.TicketValidationRepository;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.TicketValidationService;
import com.event.tickets.services.AuditLogService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.event.tickets.util.RequestUtil.extractClientIp;
import static com.event.tickets.util.RequestUtil.extractUserAgent;

/**
 * Ticket Validation Service Implementation
 *
 * This service handles ticket validation operations with centralized authorization.
 * All authorization is delegated to AuthorizationService - this service contains
 * NO authorization logic, only business logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketValidationServiceImpl implements TicketValidationService {

  private final QrCodeRepository qrCodeRepository;
  private final TicketValidationRepository ticketValidationRepository;
  private final TicketRepository ticketRepository;
  private final EventRepository eventRepository;
  private final AuthorizationService authorizationService;
  private final UserRepository userRepository;
  private final AuditLogRepository auditLogRepository;
  private final SystemUserProvider systemUserProvider;
  private final AuditLogService auditLogService;

  @Override
  public TicketValidation validateTicketByQrCode(UUID userId, UUID qrCodeId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    
    try {
      QrCode qrCode = qrCodeRepository.findByIdAndStatus(qrCodeId, QrCodeStatusEnum.ACTIVE)
          .orElseThrow(() -> new QrCodeNotFoundException(
              String.format("QR Code with ID %s was not found", qrCodeId)
          ));

      Ticket ticket = qrCode.getTicket();
      Event event = ticket.getTicketType().getEvent();

      // Centralized authorization: user must be organizer or staff
      authorizationService.requireOrganizerOrStaffAccess(userId, event);

      return validateTicket(ticket, TicketValidationMethod.QR_SCAN);
      
    } catch (QrCodeNotFoundException e) {
      // Emit failed ticket validation audit event
      emitFailedTicketValidation(user, null, "QR_CODE_NOT_FOUND: " + e.getMessage(), "QR_SCAN");
      throw e;
    }
  }

  @Override
  public TicketValidation validateTicketManually(UUID userId, UUID ticketId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    
    try {
      Ticket ticket = ticketRepository.findById(ticketId)
          .orElseThrow(TicketNotFoundException::new);

      Event event = ticket.getTicketType().getEvent();

      // Centralized authorization: user must be organizer or staff
      authorizationService.requireOrganizerOrStaffAccess(userId, event);

      return validateTicket(ticket, TicketValidationMethod.MANUAL);
      
    } catch (TicketNotFoundException e) {
      // Emit failed ticket validation audit event
      emitFailedTicketValidation(user, null, "TICKET_NOT_FOUND: " + e.getMessage(), "MANUAL");
      throw e;
    }
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

  /**
   * Emits an audit event for failed ticket validations.
   *
   * @param user The user attempting validation
   * @param ticket The ticket (may be null if not found)
   * @param reason The failure reason
   * @param method The validation method used
   */
  private void emitFailedTicketValidation(User user, Ticket ticket, String reason, String method) {
    try {
      if (user == null) {
        user = systemUserProvider.getSystemUser();
      }
      AuditLog auditLog = AuditLog.builder()
          .action(AuditAction.FAILED_TICKET_VALIDATION)
          .actor(user)
          .targetUser(user)
          .event(ticket != null ? ticket.getTicketType().getEvent() : null)
          .resourceType("TICKET")
          .resourceId(ticket != null ? ticket.getId() : null)
          .details("method=" + method + ",reason=" + reason)
          .ipAddress(extractClientIp(getCurrentRequest()))
          .userAgent(extractUserAgent(getCurrentRequest()))
          .build();
      auditLogService.saveAuditLog(auditLog);
    } catch (Exception e) {
      log.error("Failed to emit failed ticket validation audit event: userId={}, error={}", 
          user != null ? user.getId() : "null", e.getMessage());
      // Audit failures should not break the main flow
    }
  }

  /**
   * Gets the current HTTP request.
   *
   * @return HttpServletRequest
   */
  private jakarta.servlet.http.HttpServletRequest getCurrentRequest() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }
}
