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
@lombok.RequiredArgsConstructor
public class CustomSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.error("Authentication error: {}", authException.getMessage());

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

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorDto));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.error("Access denied: {}", accessDeniedException.getMessage());

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

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorDto));
    }

}