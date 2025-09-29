package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.ErrorDto;
import com.event.tickets.exceptions.EventNotFoundException;
import com.event.tickets.exceptions.EventUpdateException;
import com.event.tickets.exceptions.QrCodeGenerationException;
import com.event.tickets.exceptions.QrCodeNotFoundException;
import com.event.tickets.exceptions.TicketNotFoundException;
import com.event.tickets.exceptions.TicketTypeNotFoundException;
import com.event.tickets.exceptions.TicketsSoldOutException;
import com.event.tickets.exceptions.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

  // ============= 400 BAD REQUEST =============

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorDto> handleValidationException(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    log.error("Validation error", ex);

    BindingResult bindingResult = ex.getBindingResult();
    List<FieldError> fieldErrors = bindingResult.getFieldErrors();
    String errorMessage = fieldErrors.isEmpty() ? "Validation failed" :
        fieldErrors.get(0).getDefaultMessage();

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Validation Error");
    errorDto.setMessage(errorMessage);
    errorDto.setStatusCode(400);
    errorDto.setStatusDescription("BAD REQUEST - Invalid input data");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 400, "Validation failed");

    errorDto.setPossibleCauses(Arrays.asList(
        "CLIENT ISSUE: Missing required fields in request body",
        "CLIENT ISSUE: Invalid data format (e.g., invalid email, negative numbers)",
        "CLIENT ISSUE: Field values outside allowed range (e.g., quantity > 10)",
        "CLIENT ISSUE: Invalid date format (expected: YYYY-MM-DDTHH:mm:ss)",
        "CLIENT ISSUE: Invalid UUID format in URL or body",
        "CLIENT ISSUE: Request body is malformed JSON",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Check all required fields are present in request body",
        "Validate data formats match API specification",
        "Ensure numeric values are within allowed ranges (quantity: 1-10)",
        "Use proper date format: 2025-12-15T09:00:00",
        "Verify UUIDs are properly formatted",
        "Check JSON syntax is valid",
        "See API documentation for exact field requirements"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({TicketsSoldOutException.class, EventUpdateException.class,
                    TicketNotFoundException.class, TicketTypeNotFoundException.class,
                    EventNotFoundException.class, UserNotFoundException.class})
  public ResponseEntity<ErrorDto> handleBusinessLogicExceptions(
      Exception ex, HttpServletRequest request) {
    log.error("Business logic error", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Business Logic Error");
    errorDto.setMessage(ex.getMessage());
    errorDto.setStatusCode(400);
    errorDto.setStatusDescription("BAD REQUEST - Business rule violation");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 400, ex.getClass().getSimpleName());

    if (ex instanceof TicketsSoldOutException) {
      errorDto.setPossibleCauses(Arrays.asList(
          "CLIENT ISSUE: All tickets for this type have been sold",
          "CLIENT ISSUE: Requested quantity exceeds available tickets",
          "TIMING ISSUE: Another user purchased the last tickets before your request",
          "ENDPOINT ANALYSIS: " + endpointAnalysis
      ));
      errorDto.setSolutions(Arrays.asList(
          "Try purchasing fewer tickets",
          "Check other ticket types for the same event",
          "Contact event organizer for more tickets",
          "Refresh event data and check availability"
      ));
    } else if (ex instanceof EventNotFoundException) {
      errorDto.setPossibleCauses(Arrays.asList(
          "CLIENT ISSUE: Event ID doesn't exist in database",
          "CLIENT ISSUE: Event belongs to different organizer (permission issue)",
          "DATA ISSUE: Event has been deleted",
          "CLIENT ISSUE: Incorrect UUID format in URL",
          "ENDPOINT ANALYSIS: " + endpointAnalysis
      ));
      errorDto.setSolutions(Arrays.asList(
          "Verify the event ID is correct",
          "Check if you have permission to access this event",
          "Use the correct event UUID from event listing API",
          "Ensure you're using your own event IDs (for organizer endpoints)"
      ));
    } else {
      errorDto.setPossibleCauses(Arrays.asList(
          "CLIENT ISSUE: Resource not found in database",
          "CLIENT ISSUE: Access to resource not permitted",
          "DATA ISSUE: Resource has been deleted or modified",
          "CLIENT ISSUE: Incorrect resource ID in URL",
          "ENDPOINT ANALYSIS: " + endpointAnalysis
      ));
      errorDto.setSolutions(Arrays.asList(
          "Verify the resource ID is correct",
          "Check your permissions for this resource",
          "Refresh your data and try again",
          "Use correct UUIDs from listing APIs"
      ));
    }

    return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
  }

  // ============= 401 UNAUTHORIZED =============

  @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
  public ResponseEntity<ErrorDto> handleAuthenticationException(
      Exception ex, HttpServletRequest request) {
    log.error("Authentication error", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Authentication Failed");
    errorDto.setMessage("Invalid or missing authentication credentials");
    errorDto.setStatusCode(401);
    errorDto.setStatusDescription("UNAUTHORIZED - Authentication required");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 401, "Authentication failed");

    errorDto.setPossibleCauses(Arrays.asList(
        "CLIENT ISSUE: Missing Authorization header in request",
        "CLIENT ISSUE: Invalid or expired JWT token",
        "CLIENT ISSUE: Token format is incorrect (should be 'Bearer <token>')",
        "SERVER ISSUE: Keycloak server is not running or unreachable",
        "CLIENT ISSUE: Token was issued by wrong issuer/realm",
        "SERVER ISSUE: System clock skew causing token validation failure",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Add 'Authorization: Bearer <your-token>' header",
        "Get a new token from Keycloak: POST /realms/event-ticket-platform/protocol/openid-connect/token",
        "Check token format: Authorization: Bearer eyJhbGciOiJSUzI1NiIs...",
        "Verify Keycloak is running on http://localhost:9090",
        "Ensure token was obtained from correct realm: event-ticket-platform",
        "Synchronize system time if token appears valid"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.UNAUTHORIZED);
  }

  // ============= 403 FORBIDDEN =============

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorDto> handleAccessDeniedException(
      AccessDeniedException ex, HttpServletRequest request) {
    log.error("Access denied", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Access Denied");
    errorDto.setMessage("Insufficient permissions for this operation");
    errorDto.setStatusCode(403);
    errorDto.setStatusDescription("FORBIDDEN - Insufficient permissions");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 403, "Access denied");

    errorDto.setPossibleCauses(Arrays.asList(
        "CLIENT ISSUE: User doesn't have required role (ORGANIZER, ATTENDEE, or STAFF)",
        "CLIENT ISSUE: Token is valid but lacks necessary permissions",
        "CLIENT ISSUE: Trying to access another user's resources",
        "SERVER ISSUE: Role mapping not configured correctly in Keycloak",
        "CLIENT ISSUE: Token missing required scopes or claims",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Check user has correct role in Keycloak (ORGANIZER/ATTENDEE/STAFF)",
        "Verify role mapping is configured in Keycloak client",
        "Ensure you're accessing your own resources",
        "Contact admin to assign proper roles",
        "Get new token with correct scopes",
        "Check endpoint documentation for required roles"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.FORBIDDEN);
  }

  // ============= 404 NOT FOUND =============

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorDto> handleNoHandlerFoundException(
      NoHandlerFoundException ex, HttpServletRequest request) {
    log.error("Endpoint not found", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Endpoint Not Found");
    errorDto.setMessage("The requested API endpoint does not exist");
    errorDto.setStatusCode(404);
    errorDto.setStatusDescription("NOT FOUND - Endpoint does not exist");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 404, "Endpoint not found");

    errorDto.setPossibleCauses(Arrays.asList(
        "CLIENT ISSUE: Incorrect API endpoint URL",
        "CLIENT ISSUE: Typo in the endpoint path",
        "CLIENT ISSUE: Missing path variables (e.g., {eventId}, {ticketTypeId})",
        "CLIENT ISSUE: Wrong HTTP method (GET vs POST vs PUT vs DELETE)",
        "CLIENT ISSUE: API version mismatch in URL path",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Check API documentation for correct endpoint",
        "Verify all path variables are included",
        "Ensure correct HTTP method is used",
        "Base URL should be: http://localhost:8081/api/v1/",
        "Example: POST /api/v1/events/{eventId}/ticket-types/{ticketTypeId}/tickets",
        "Double-check UUID format in URL path"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
  }

  // ============= 405 METHOD NOT ALLOWED =============

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorDto> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    log.error("Method not allowed", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Method Not Allowed");
    errorDto.setMessage("HTTP method not supported for this endpoint");
    errorDto.setStatusCode(405);
    errorDto.setStatusDescription("METHOD NOT ALLOWED - Wrong HTTP method");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 405, "Method not allowed");

    errorDto.setPossibleCauses(Arrays.asList(
        "CLIENT ISSUE: Using wrong HTTP method (e.g., GET instead of POST)",
        "CLIENT ISSUE: Endpoint only supports specific methods",
        "CLIENT ISSUE: Typo in HTTP method name",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Check API documentation for correct HTTP method",
        "Purchase tickets: POST (not GET)",
        "Get data: GET (not POST)",
        "Update data: PUT (not POST)",
        "Delete data: DELETE (not GET)",
        "Verify method in API documentation"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.METHOD_NOT_ALLOWED);
  }

  // ============= 500 INTERNAL SERVER ERROR =============

  @ExceptionHandler({QrCodeGenerationException.class, QrCodeNotFoundException.class,
                    DataIntegrityViolationException.class})
  public ResponseEntity<ErrorDto> handleInternalServerError(
      Exception ex, HttpServletRequest request) {
    log.error("Internal server error", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Internal Server Error");
    errorDto.setMessage("An unexpected error occurred on the server");
    errorDto.setStatusCode(500);
    errorDto.setStatusDescription("INTERNAL SERVER ERROR - Server-side issue");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 500, ex.getClass().getSimpleName());

    errorDto.setPossibleCauses(Arrays.asList(
        "SERVER ISSUE: Database connection issues",
        "SERVER ISSUE: QR code generation service failure",
        "SERVER ISSUE: Data constraint violations in database",
        "SERVER ISSUE: External service (Keycloak) unavailable",
        "CODE ISSUE: Application configuration errors",
        "SERVER ISSUE: Insufficient server resources",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Check if PostgreSQL database is running on localhost:5432",
        "Verify Keycloak is running on http://localhost:9090",
        "Check application logs for detailed error information",
        "Ensure database schema is up to date",
        "Restart the application if persistent",
        "Contact system administrator if problem persists"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // ============= GENERIC EXCEPTION HANDLER =============

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorDto> handleGenericException(
      Exception ex, HttpServletRequest request) {
    log.error("Unexpected error", ex);

    ErrorDto errorDto = new ErrorDto();
    errorDto.setError("Unexpected Error");
    errorDto.setMessage("An unexpected error occurred: " + ex.getMessage());
    errorDto.setStatusCode(500);
    errorDto.setStatusDescription("INTERNAL SERVER ERROR - Unexpected issue");
    errorDto.setTimestamp(LocalDateTime.now().toString());
    errorDto.setPath(request.getRequestURI());

    // API Endpoint-specific error analysis
    String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 500, "Unexpected error");

    errorDto.setPossibleCauses(Arrays.asList(
        "CODE ISSUE: Unhandled application error",
        "CODE ISSUE: Programming bug in the code",
        "DATA ISSUE: Unexpected data condition",
        "SERVER ISSUE: Resource exhaustion (memory, disk space)",
        "ENDPOINT ANALYSIS: " + endpointAnalysis
    ));
    errorDto.setSolutions(Arrays.asList(
        "Check application logs for stack trace",
        "Try the request again after a moment",
        "Contact system administrator",
        "Report this error with exact steps to reproduce",
        "Check if this is a known issue in the codebase"
    ));

    return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  // ============= ENDPOINT ANALYSIS HELPER =============

  private String analyzeEndpointError(String path, int statusCode, String errorType) {
    if (path.contains("/ticket-types/") && path.contains("/tickets")) {
      // Ticket Purchase Endpoint
      if (statusCode == 400) {
        return "Ticket Purchase API - Common Issues: Invalid quantity (1-10), malformed request body, sold out tickets";
      } else if (statusCode == 401) {
        return "Ticket Purchase API - Requires ATTENDEE role with valid Bearer token";
      } else if (statusCode == 403) {
        return "Ticket Purchase API - User must have ATTENDEE role, not ORGANIZER or STAFF";
      } else if (statusCode == 404) {
        return "Ticket Purchase API - Check eventId and ticketTypeId in URL are valid UUIDs";
      } else if (statusCode == 500) {
        return "Ticket Purchase API - Likely QR code generation or database issue";
      }
    } else if (path.contains("/events") && path.contains("/ticket-types") && !path.contains("/tickets")) {
      // Ticket Type Management
      if (statusCode == 401 || statusCode == 403) {
        return "Ticket Type Management - Requires ORGANIZER role and access to own events only";
      } else if (statusCode == 404) {
        return "Ticket Type Management - Check eventId and ticketTypeId belong to authenticated organizer";
      }
    } else if (path.startsWith("/api/v1/events")) {
      // Event Management
      if (statusCode == 401 || statusCode == 403) {
        return "Event Management - Requires ORGANIZER role for create/update/delete operations";
      } else if (statusCode == 400) {
        return "Event Management - Check date formats, required fields, and business rules";
      }
    } else if (path.startsWith("/api/v1/published-events")) {
      // Published Events
      if (statusCode == 401 || statusCode == 403) {
        return "Published Events - Requires ATTENDEE role for browsing events";
      }
    } else if (path.startsWith("/api/v1/tickets")) {
      // User Tickets
      if (statusCode == 401 || statusCode == 403) {
        return "User Tickets - Requires ATTENDEE role to view own tickets";
      } else if (statusCode == 404) {
        return "User Tickets - Ticket not found or doesn't belong to authenticated user";
      }
    } else if (path.startsWith("/api/v1/ticket-validations")) {
      // Ticket Validation
      if (statusCode == 401 || statusCode == 403) {
        return "Ticket Validation - Requires STAFF role for validation operations";
      } else if (statusCode == 400) {
        return "Ticket Validation - Check validation method (MANUAL/QR_SCAN) and correct ID type";
      }
    }

    return "General API issue - Check HTTP method, URL format, authentication, and request body";
  }
}
