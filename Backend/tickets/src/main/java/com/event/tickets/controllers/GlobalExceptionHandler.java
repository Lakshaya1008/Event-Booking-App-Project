package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.ErrorDto;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.EventUpdateException;
import com.event.tickets.exceptions.InvalidInviteCodeException;
import com.event.tickets.exceptions.InviteCodeNotFoundException;
import com.event.tickets.exceptions.KeycloakOperationException;
import com.event.tickets.exceptions.KeycloakUserCreationException;
import com.event.tickets.exceptions.QrCodeGenerationException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.RegistrationException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.TicketsSoldOutException;
import com.event.tickets.exceptions.UserNotFoundException;
import com.event.tickets.exceptions.EmailAlreadyInUseException;
import com.event.tickets.exceptions.InvalidBusinessStateException;
import com.event.tickets.exceptions.InvalidInputException;
import com.event.tickets.exceptions.SystemUserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    // ============= 400 BAD REQUEST — VALIDATION =============

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error on {}: {}", request.getRequestURI(), ex.getBindingResult().getErrorCount());

        BindingResult bindingResult = ex.getBindingResult();
        List<FieldError> fieldErrors = bindingResult.getFieldErrors();

        List<String> allValidationErrors = fieldErrors.stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());

        String message = fieldErrors.isEmpty()
                ? "Validation failed"
                : "Validation failed on " + fieldErrors.size() + " field(s). See validationErrors for details.";

        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("VALIDATION_ERROR");
        errorDto.setMessage(message);
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Validation failed");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setValidationErrors(allValidationErrors);
        errorDto.setPossibleCauses(Arrays.asList(
                "Missing required fields in request body",
                "Invalid data format (e.g. invalid email, weak password)",
                "Field values outside allowed range",
                "Invalid date format — expected: YYYY-MM-DDTHH:mm:ss",
                "Invalid UUID format in path or body"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Fix ALL fields listed in the validationErrors array",
                "Check each field's format requirements",
                "Ensure numeric values are in allowed ranges"
        ));

        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<String> allErrors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.toList());

        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("CONSTRAINT_VIOLATION");
        errorDto.setMessage("Validation constraint(s) violated. See validationErrors for details.");
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Constraint violation");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setValidationErrors(allErrors);
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("INVALID_ARGUMENT");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Invalid argument");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorDto> handleInvalidInputException(
            InvalidInputException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("INVALID_INPUT");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Invalid input");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    // ============= 400 BAD REQUEST — BUSINESS LOGIC (individual handlers for clarity) =============

    @ExceptionHandler(TicketsSoldOutException.class)
    public ResponseEntity<ErrorDto> handleTicketsSoldOutException(
            TicketsSoldOutException ex, HttpServletRequest request) {
        log.info("Tickets sold out: {}", request.getRequestURI());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("TICKETS_SOLD_OUT");
        errorDto.setMessage(ex.getMessage() != null ? ex.getMessage()
                : "All tickets for this type are sold out.");
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Tickets unavailable");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "All tickets for this ticket type have been purchased",
                "Requested quantity exceeds remaining availability",
                "Another user purchased the last tickets moments before your request"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Try purchasing fewer tickets",
                "Check other ticket types for this event",
                "Contact the event organizer to increase availability"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorDto> handleEventNotFoundException(
            EventNotFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("EVENT_NOT_FOUND");
        errorDto.setMessage(sanitizeErrorMessage(ex.getMessage()));
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - Event does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "The event ID in the URL does not exist",
                "The event belongs to a different organizer (permission denied)",
                "The event has been deleted"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Verify the event ID is correct",
                "Use GET /api/v1/events to list your events",
                "Ensure you are the organizer of this event"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorDto> handleTicketNotFoundException(
            TicketNotFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("TICKET_NOT_FOUND");
        errorDto.setMessage(sanitizeErrorMessage(ex.getMessage()));
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - Ticket does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "The ticket ID does not exist",
                "This ticket belongs to a different user"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Use GET /api/v1/tickets to list your tickets",
                "Ensure the ticket ID is correct"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(TicketTypeNotFoundException.class)
    public ResponseEntity<ErrorDto> handleTicketTypeNotFoundException(
            TicketTypeNotFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("TICKET_TYPE_NOT_FOUND");
        errorDto.setMessage(sanitizeErrorMessage(ex.getMessage()));
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - Ticket type does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorDto> handleUserNotFoundException(
            UserNotFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("USER_NOT_FOUND");
        errorDto.setMessage(sanitizeErrorMessage(ex.getMessage()));
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - User does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EventUpdateException.class)
    public ResponseEntity<ErrorDto> handleEventUpdateException(
            EventUpdateException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("EVENT_UPDATE_ERROR");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Event update not allowed");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    // ============= INVITE CODE ERRORS =============

    @ExceptionHandler(InviteCodeNotFoundException.class)
    public ResponseEntity<ErrorDto> handleInviteCodeNotFoundException(
            InviteCodeNotFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("INVITE_CODE_NOT_FOUND");
        errorDto.setMessage("The invite code you provided does not exist.");
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - Invite code not found");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "The invite code was typed incorrectly",
                "The invite code has never been generated"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Double-check the invite code — format is XXXX-XXXX-XXXX-XXXX",
                "Ask the person who invited you to resend the code"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidInviteCodeException.class)
    public ResponseEntity<ErrorDto> handleInvalidInviteCodeException(
            InvalidInviteCodeException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("INVALID_INVITE_CODE");
        // "already been redeemed" / "has expired" / "has been revoked"
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(400);
        errorDto.setStatusDescription("BAD REQUEST - Invite code cannot be used");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Invite code has already been used (REDEEMED)",
                "Invite code has expired",
                "Invite code was revoked by the creator"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Ask for a new invite code",
                "Register without an invite code to get a standard ATTENDEE account"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    // ============= REGISTRATION ERRORS =============

    @ExceptionHandler(EmailAlreadyInUseException.class)
    public ResponseEntity<ErrorDto> handleEmailAlreadyInUseException(
            EmailAlreadyInUseException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("EMAIL_ALREADY_REGISTERED");
        errorDto.setMessage("An account with this email address already exists.");
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Email already registered");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "You have already registered with this email address",
                "Someone else is using this email"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Log in instead — go to Keycloak and obtain a token",
                "Use password reset if you forgot your credentials",
                "Register with a different email address"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ErrorDto> handleRegistrationException(
            RegistrationException ex, HttpServletRequest request) {
        log.error("Registration failed: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("REGISTRATION_FAILED");
        errorDto.setMessage(sanitizeErrorMessage(ex.getMessage()));
        errorDto.setStatusCode(422);
        errorDto.setStatusDescription("UNPROCESSABLE ENTITY - Registration could not be completed");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Keycloak server may be temporarily unavailable",
                "Role assignment failed in Keycloak",
                "Database error while saving user record",
                "Staff event assignment failed"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Try again in a few seconds",
                "If using an invite code, verify the invite code is valid",
                "Contact the administrator if the problem persists"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(KeycloakUserCreationException.class)
    public ResponseEntity<ErrorDto> handleKeycloakUserCreationException(
            KeycloakUserCreationException ex, HttpServletRequest request) {
        log.error("Keycloak user creation failed: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("KEYCLOAK_USER_CREATION_FAILED");
        errorDto.setMessage("Failed to create your account. This may indicate the email is already registered in the authentication system.");
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Account creation failed");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Email is already registered in the authentication system",
                "Keycloak server is unavailable"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Try logging in — your account may already exist",
                "Try a different email address",
                "Contact the administrator if the problem persists"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    // ============= 401 UNAUTHORIZED =============

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorDto> handleAuthenticationException(
            Exception ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", request.getRequestURI());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("AUTHENTICATION_FAILED");
        errorDto.setMessage("Invalid or missing authentication token. Please obtain a new token from Keycloak.");
        errorDto.setStatusCode(401);
        errorDto.setStatusDescription("UNAUTHORIZED - Authentication required");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Missing Authorization header",
                "JWT token is expired or malformed",
                "Token issuer/realm does not match server configuration",
                "Authentication service is temporarily unavailable"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Set header: Authorization: Bearer <token>",
                "Request a fresh access token from Keycloak",
                "Verify token was issued for the expected realm and client"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.UNAUTHORIZED);
    }

    // ============= 403 FORBIDDEN =============

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("ACCESS_DENIED");
        errorDto.setMessage("You are not authorized to perform this action.");
        errorDto.setStatusCode(403);
        errorDto.setStatusDescription("FORBIDDEN - You are not authorized to perform this action");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Authenticated user does not have required role",
                "Resource belongs to another user or organizer",
                "Account approval status blocks access"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Use an account with the required role for this endpoint",
                "Ensure you are accessing only your own resources",
                "Contact an administrator if you need elevated access"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.FORBIDDEN);
    }

    // ============= 404 NOT FOUND =============

    @ExceptionHandler(QrCodeNotFoundException.class)
    public ResponseEntity<ErrorDto> handleQrCodeNotFoundException(
            QrCodeNotFoundException ex, HttpServletRequest request) {
        log.warn("QR code not found: {}", request.getRequestURI());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("QR_CODE_NOT_FOUND");
        errorDto.setMessage("QR code not found or is no longer active.");
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - QR code does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "The ticket was cancelled — QR code deactivated",
                "Invalid QR code ID"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Check the ticket status is PURCHASED (not CANCELLED)",
                "Re-download the QR code from: GET /api/v1/tickets/{ticketId}/qr-codes/png"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorDto> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("ENDPOINT_NOT_FOUND");
        errorDto.setMessage("The requested API endpoint does not exist: " + request.getMethod() + " " + request.getRequestURI());
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - Endpoint does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Incorrect API path — typo in URL",
                "Missing path variables (e.g. {eventId})",
                "Wrong HTTP method (GET vs POST vs PUT vs DELETE)"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    // ============= 405 METHOD NOT ALLOWED =============

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDto> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("METHOD_NOT_ALLOWED");
        errorDto.setMessage("HTTP method " + request.getMethod() + " is not supported for this endpoint.");
        errorDto.setStatusCode(405);
        errorDto.setStatusDescription("METHOD NOT ALLOWED");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.METHOD_NOT_ALLOWED);
    }

    // ============= 409 CONFLICT =============

    @ExceptionHandler(InvalidBusinessStateException.class)
    public ResponseEntity<ErrorDto> handleInvalidBusinessStateException(
            InvalidBusinessStateException ex, HttpServletRequest request) {
        log.warn("Business state conflict: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("BUSINESS_RULE_VIOLATION");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Business rule violation");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.event.tickets.exceptions.InvalidApprovalStateException.class)
    public ResponseEntity<ErrorDto> handleInvalidApprovalStateException(
            com.event.tickets.exceptions.InvalidApprovalStateException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("INVALID_APPROVAL_STATE");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Invalid approval state transition");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Attempting to approve a user who is already APPROVED",
                "Attempting to reject a user who is already REJECTED",
                "Only PENDING users can be approved or rejected"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.event.tickets.exceptions.TicketTypeDeleteNotAllowedException.class)
    public ResponseEntity<ErrorDto> handleTicketTypeDeleteNotAllowedException(
            com.event.tickets.exceptions.TicketTypeDeleteNotAllowedException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("TICKET_TYPE_DELETE_NOT_ALLOWED");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Cannot delete ticket type with active sold tickets");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setSolutions(Arrays.asList(
                "Set totalAvailable to 0 to stop new sales without deleting",
                "Cancel the event first to cancel all tickets, then delete the ticket type"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.event.tickets.exceptions.DiscountAlreadyExistsException.class)
    public ResponseEntity<ErrorDto> handleDiscountAlreadyExistsException(
            com.event.tickets.exceptions.DiscountAlreadyExistsException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("DISCOUNT_ALREADY_EXISTS");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Active discount already exists");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setSolutions(Arrays.asList(
                "Deactivate the existing discount before creating a new one",
                "Update the existing discount using PUT instead"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    /**
     * Handles optimistic locking failures — two concurrent requests modified the same
     * entity (Event, TicketType, or InviteCode) and one of them lost the race.
     *
     * @Version on these entities means Hibernate adds "WHERE version = ?" to every UPDATE.
     * When two transactions load version=5 and both try to save, the first commit succeeds
     * (version becomes 6), the second commit finds version=6 ≠ 5 and throws this exception.
     *
     * Returns 409 CONFLICT — not 500. The client should retry the request.
     * Without this handler, the exception bubbles to the catch-all and returns a raw 500.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDto> handleOptimisticLockingFailureException(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        log.warn("Optimistic locking conflict at {}: {}", request.getRequestURI(), ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("CONCURRENT_MODIFICATION");
        errorDto.setMessage("This resource was modified by another request at the same time. Please retry your request.");
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Concurrent modification detected");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "Two requests modified the same event, ticket type, or invite code simultaneously",
                "A background process updated this resource while your request was in progress"
        ));
        errorDto.setSolutions(Arrays.asList(
                "Retry your request — the conflict is transient",
                "Fetch the latest version of the resource before modifying it"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrityViolationException(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", request.getRequestURI());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("DATA_CONFLICT");
        errorDto.setMessage("The request conflicts with existing data. A resource with the same unique identifier may already exist.");
        errorDto.setStatusCode(409);
        errorDto.setStatusDescription("CONFLICT - Data integrity violation");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.CONFLICT);
    }

    // ============= 500 INTERNAL SERVER ERROR =============

    @ExceptionHandler({QrCodeGenerationException.class, KeycloakOperationException.class})
    public ResponseEntity<ErrorDto> handleInternalServerError(
            Exception ex, HttpServletRequest request) {
        log.error("Internal server error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("INTERNAL_SERVER_ERROR");
        errorDto.setMessage(sanitizeErrorMessage(ex.getMessage()));
        errorDto.setStatusCode(500);
        errorDto.setStatusDescription("INTERNAL SERVER ERROR");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        errorDto.setPossibleCauses(Arrays.asList(
                "QR code generation failure",
                "Keycloak Admin API unavailable",
                "Check application logs for detailed error"
        ));
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(SystemUserNotFoundException.class)
    public ResponseEntity<ErrorDto> handleSystemUserNotFoundException(
            SystemUserNotFoundException ex, HttpServletRequest request) {
        log.error("System user not found: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("SYSTEM_CONFIGURATION_ERROR");
        errorDto.setMessage("A required system resource is missing. Please contact the administrator.");
        errorDto.setStatusCode(500);
        errorDto.setStatusDescription("INTERNAL SERVER ERROR - System configuration error");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({com.event.tickets.exceptions.KeycloakUserDeletionException.class,
            com.event.tickets.exceptions.KeycloakUserUpdateException.class})
    public ResponseEntity<ErrorDto> handleKeycloakUserOperationException(
            RuntimeException ex, HttpServletRequest request) {
        log.error("Keycloak user operation failed: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("KEYCLOAK_OPERATION_FAILED");
        errorDto.setMessage("A user management operation failed. Please check Keycloak connectivity and try again.");
        errorDto.setStatusCode(500);
        errorDto.setStatusDescription("INTERNAL SERVER ERROR - Keycloak operation failed");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(com.event.tickets.exceptions.ReportGenerationException.class)
    public ResponseEntity<ErrorDto> handleReportGenerationException(
            com.event.tickets.exceptions.ReportGenerationException ex, HttpServletRequest request) {
        log.error("Report generation failed: {}", ex.getMessage());
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("REPORT_GENERATION_FAILED");
        errorDto.setMessage("Failed to generate the report. Please try again.");
        errorDto.setStatusCode(500);
        errorDto.setStatusDescription("INTERNAL SERVER ERROR - Report generation failed");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(com.event.tickets.exceptions.DiscountNotFoundException.class)
    public ResponseEntity<ErrorDto> handleDiscountNotFoundException(
            com.event.tickets.exceptions.DiscountNotFoundException ex, HttpServletRequest request) {
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("DISCOUNT_NOT_FOUND");
        errorDto.setMessage(ex.getMessage());
        errorDto.setStatusCode(404);
        errorDto.setStatusDescription("NOT FOUND - Discount does not exist");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    // ============= CATCH-ALL =============

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("UNEXPECTED_ERROR");
        errorDto.setMessage("An unexpected error occurred. Please try again or contact support.");
        errorDto.setStatusCode(500);
        errorDto.setStatusDescription("INTERNAL SERVER ERROR");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ============= HELPER =============

    private String sanitizeErrorMessage(String message) {
        if (message == null) return "An error occurred";
        if ("dev".equals(activeProfile) || "local".equals(activeProfile)) return message;

        String sanitized = message;
        sanitized = sanitized.replaceAll("(?i)user with id '[a-f0-9-]{36}'", "user");
        sanitized = sanitized.replaceAll("(?i)event with id '[a-f0-9-]{36}'", "event");
        sanitized = sanitized.replaceAll("(?i)at [a-z0-9._]+\\([^)]+\\)", "");
        sanitized = sanitized.replaceAll("(?i)SQL.*?;", "database query");
        if (sanitized.length() > 300) sanitized = sanitized.substring(0, 300) + "...";
        return sanitized;
    }
}