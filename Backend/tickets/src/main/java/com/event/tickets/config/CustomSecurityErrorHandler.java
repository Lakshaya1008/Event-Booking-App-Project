package com.event.tickets.config;

import com.event.tickets.domain.dtos.ErrorDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;

@Component
@Slf4j
public class CustomSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException {
        log.error("Authentication error: {}", authException.getMessage());

        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("Authentication Failed");
        errorDto.setMessage("Authentication is required to access this resource");
        errorDto.setStatusCode(401);
        errorDto.setStatusDescription("UNAUTHORIZED - Authentication required");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());

        // API Endpoint-specific analysis for 401 errors
        String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 401);

        errorDto.setPossibleCauses(Arrays.asList(
            "CLIENT ISSUE: Missing Authorization header in request",
            "CLIENT ISSUE: Invalid or expired JWT token",
            "CLIENT ISSUE: Token format is incorrect (should be 'Bearer <token>')",
            "CLIENT ISSUE: Token contains invalid claims or signature",
            "SERVER ISSUE: Keycloak server is not running or unreachable",
            "CLIENT ISSUE: Token was issued by wrong issuer/realm",
            "SERVER ISSUE: JWT validation configuration error",
            "ENDPOINT ANALYSIS: " + endpointAnalysis
        ));
        errorDto.setSolutions(Arrays.asList(
            "Add 'Authorization: Bearer <your-token>' header to your request",
            "Get a fresh token from Keycloak: POST http://localhost:9090/realms/event-ticket-platform/protocol/openid-connect/token",
            "Check token format: 'Authorization: Bearer eyJhbGciOiJSUzI1NiIs...'",
            "Verify your token hasn't expired (check exp claim)",
            "Ensure Keycloak is running on http://localhost:9090",
            "Confirm token was obtained from correct realm: event-ticket-platform",
            "Try getting a new token with proper client_id and credentials"
        ));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorDto));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                      AccessDeniedException accessDeniedException) throws IOException {
        log.error("Access denied: {}", accessDeniedException.getMessage());

        ErrorDto errorDto = new ErrorDto();
        errorDto.setError("Access Denied");
        errorDto.setMessage("You don't have permission to access this resource");
        errorDto.setStatusCode(403);
        errorDto.setStatusDescription("FORBIDDEN - Insufficient permissions");
        errorDto.setTimestamp(LocalDateTime.now().toString());
        errorDto.setPath(request.getRequestURI());

        // API Endpoint-specific analysis for 403 errors
        String endpointAnalysis = analyzeEndpointError(request.getRequestURI(), 403);

        errorDto.setPossibleCauses(Arrays.asList(
            "CLIENT ISSUE: User doesn't have required role (ORGANIZER, ATTENDEE, or STAFF)",
            "CLIENT ISSUE: Valid token but insufficient permissions for this endpoint",
            "CLIENT ISSUE: Trying to access another user's private resources",
            "CLIENT ISSUE: User role not properly configured in Keycloak",
            "SERVER ISSUE: Role mapping configuration error in application",
            "CLIENT ISSUE: Token missing required scopes or role claims",
            "ENDPOINT ANALYSIS: " + endpointAnalysis
        ));
        errorDto.setSolutions(Arrays.asList(
            "Verify your user has the correct role in Keycloak admin console",
            "Check role mapping is configured in Keycloak client settings",
            "Ensure you're only accessing your own resources",
            "Contact administrator to assign proper roles (ORGANIZER/ATTENDEE/STAFF)",
            "Get a new token after role assignment",
            "Verify endpoint documentation for required permissions"
        ));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorDto));
    }

    private String analyzeEndpointError(String path, int statusCode) {
        if (path.contains("/ticket-types/") && path.contains("/tickets")) {
            // Ticket Purchase Endpoint
            if (statusCode == 401) {
                return "Ticket Purchase API - This endpoint requires ATTENDEE role. Your token is missing or invalid.";
            } else if (statusCode == 403) {
                return "Ticket Purchase API - You need ATTENDEE role to purchase tickets. ORGANIZER and STAFF roles cannot purchase tickets.";
            }
        } else if (path.contains("/events") && path.contains("/ticket-types") && !path.contains("/tickets")) {
            // Ticket Type Management
            if (statusCode == 401) {
                return "Ticket Type Management - This endpoint requires ORGANIZER role. Your token is missing or invalid.";
            } else if (statusCode == 403) {
                return "Ticket Type Management - You need ORGANIZER role to manage ticket types. Only event organizers can create/update/delete ticket types.";
            }
        } else if (path.startsWith("/api/v1/events") && !path.contains("/published-events")) {
            // Event Management
            if (statusCode == 401) {
                return "Event Management - This endpoint requires ORGANIZER role. Your token is missing or invalid.";
            } else if (statusCode == 403) {
                return "Event Management - You need ORGANIZER role to manage events. Only organizers can create/update/delete events.";
            }
        } else if (path.startsWith("/api/v1/published-events")) {
            // Published Events Browsing
            if (statusCode == 401) {
                return "Published Events API - This endpoint requires ATTENDEE role. Your token is missing or invalid.";
            } else if (statusCode == 403) {
                return "Published Events API - You need ATTENDEE role to browse published events.";
            }
        } else if (path.startsWith("/api/v1/tickets")) {
            // User Tickets
            if (statusCode == 401) {
                return "User Tickets API - This endpoint requires ATTENDEE role. Your token is missing or invalid.";
            } else if (statusCode == 403) {
                return "User Tickets API - You need ATTENDEE role to view tickets. You can only view your own tickets.";
            }
        } else if (path.startsWith("/api/v1/ticket-validations")) {
            // Ticket Validation
            if (statusCode == 401) {
                return "Ticket Validation API - This endpoint requires STAFF role. Your token is missing or invalid.";
            } else if (statusCode == 403) {
                return "Ticket Validation API - You need STAFF role to validate tickets. Only staff members can scan and validate tickets.";
            }
        }

        return "General API Authentication Issue - Check your token and user role assignments in Keycloak.";
    }
}
