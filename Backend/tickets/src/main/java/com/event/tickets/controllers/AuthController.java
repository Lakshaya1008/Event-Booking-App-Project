package com.event.tickets.controllers;

import com.event.tickets.domain.dtos.RegisterRequestDto;
import com.event.tickets.domain.dtos.RegisterResponseDto;
import com.event.tickets.services.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 *
 * Handles public authentication endpoints including user registration.
 * All endpoints are public (no authentication required).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

  private final RegistrationService registrationService;

  /**
   * Registers a new user via invite code or as ATTENDEE (no invite).
   *
   * This endpoint is PUBLIC and does not require authentication.
   * It creates a Keycloak user and local database record with PENDING approval status.
   *
   * Registration Flow:
   * - If inviteCode provided: Validate invite and assign role from invite
   * - If no inviteCode: Assign ATTENDEE role
   * - Always creates user with approval_status = PENDING
   * - No JWT token issued (user must wait for approval)
   *
   * @param request Registration request with optional invite code
   * @return Registration response with confirmation details
   */
  @PostMapping("/register")
  public ResponseEntity<RegisterResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
    log.info("Registration attempt: email={}, inviteCode={}", 
        request.getEmail(), 
        request.getInviteCode() != null ? "PROVIDED" : "NONE");

    try {
      RegisterResponseDto response = registrationService.register(request);
      
      log.info("Registration successful: email={}, role={}, requiresApproval={}", 
          response.getEmail(), 
          response.getAssignedRole(), 
          response.isRequiresApproval());

      return ResponseEntity.status(HttpStatus.CREATED).body(response);
      
    } catch (Exception e) {
      log.error("Registration failed: email={}, error={}", request.getEmail(), e.getMessage());
      throw e; // Let GlobalExceptionHandler handle the response
    }
  }
}
