package com.event.tickets.services;

import com.event.tickets.domain.dtos.RegisterRequestDto;
import com.event.tickets.domain.dtos.RegisterResponseDto;

/**
 * Registration Service - PRODUCTION HARDENED
 *
 * Handles invite-based user registration with transaction safety.
 *
 * ATOMIC TRANSACTION GUARANTEE:
 * - Either ALL steps succeed OR ALL steps rollback
 * - No partial registration states
 * - No orphaned Keycloak users
 * - No double-redemption of invite codes
 *
 * FAILURE SAFETY:
 * - Keycloak creation fails → invite NOT marked used
 * - Role assignment fails → Keycloak user deleted, invite NOT marked used
 * - DB persistence fails → Keycloak user deleted, invite NOT marked used
 * - Staff assignment fails → Full rollback
 *
 * RETRY SAFETY:
 * - Duplicate submissions detected via email uniqueness
 * - Optimistic locking prevents concurrent invite redemption
 * - Idempotent where possible
 */
public interface RegistrationService {

  /**
   * Registers a new user via invite code.
   *
   * TRANSACTION FLOW:
   * 1. Validate invite code (exists, PENDING status, not expired)
   * 2. Check email not already registered
   * 3. Create user in Keycloak
   * 4. Assign role from invite code in Keycloak
   * 5. Create user record in database (status=PENDING)
   * 6. If STAFF role: Assign to event
   * 7. Mark invite code as REDEEMED
   *
   * ROLLBACK ON ANY FAILURE:
   * - Delete Keycloak user if created
   * - Do NOT mark invite as used
   * - Log failure for investigation
   *
   * @param request Registration request with invite code, email, password, name
   * @return Registration response with success details
   * @throws com.event.tickets.exceptions.InvalidInviteCodeException if invite invalid
   * @throws com.event.tickets.exceptions.EmailAlreadyInUseException if email taken
   * @throws com.event.tickets.exceptions.RegistrationException on other failures
   */
  RegisterResponseDto register(RegisterRequestDto request);
}
