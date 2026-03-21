package com.event.tickets.controllers;

import static com.event.tickets.util.JwtUtil.parseUserId;

import com.event.tickets.domain.dtos.GenerateInviteCodeRequestDto;
import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeRequestDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto;
import com.event.tickets.services.AuthorizationService;
import com.event.tickets.services.InviteCodeService;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FIXES APPLIED IN THIS VERSION:
 *
 * FIX I-2 — revokeInviteCode() now passes isAdmin to the service.
 *   BEFORE: The service called keycloakAdminService.userHasRole() internally — a live
 *   Keycloak API call just to check if the revoker is an admin, despite Spring Security
 *   having already verified the JWT.
 *   AFTER: hasRole(jwt, "ADMIN") is evaluated here from the already-verified JWT and
 *   passed as a boolean parameter to inviteCodeService.revokeInviteCode().
 *
 * FIX I-4 — validateInviteCreation() STAFF eventId guard consolidated.
 *   BEFORE: The null eventId guard for STAFF ran in two separate places — inside the
 *   organizer block and again in a standalone STAFF block. Correct but fragile.
 *   AFTER: Single STAFF block covers both organizers and admins. The organizer block
 *   only checks role restrictions (organizer → STAFF only, no other roles).
 *
 * FIX I-6 — default revoke reason changed to "No reason provided".
 *   BEFORE: "Revoked by creator" — misleading when an admin revokes someone else's code.
 *   AFTER: "No reason provided" — neutral and accurate for any revoker.
 *
 * Previously applied fixes preserved:
 *   H-08 FIX: ADMIN sees ALL codes; ORGANIZER sees only their own.
 *   H-09 FIX: hasRole() reads realm_access.roles — ADMIN bypass works correctly.
 *   C-08 FIX: hasRole() reads realm_access.roles, not the non-existent "roles" top-level claim.
 */
@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
@Slf4j
public class InviteCodeController {

    private final InviteCodeService inviteCodeService;
    private final AuthorizationService authorizationService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<InviteCodeResponseDto> generateInviteCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateInviteCodeRequestDto request
    ) {
        UUID creatorId = parseUserId(jwt);
        String roleName = request.getRoleName();
        UUID eventId = request.getEventId();

        log.info("User '{}' generating invite code for role '{}', event '{}'", creatorId, roleName, eventId);

        validateInviteCreation(jwt, roleName, eventId, creatorId);

        InviteCodeResponseDto response = inviteCodeService.generateInviteCode(
                creatorId, roleName, eventId, request.getExpirationHours());

        log.info("Generated invite code '{}' for role '{}'", response.getCode(), roleName);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/redeem")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RedeemInviteCodeResponseDto> redeemInviteCode(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RedeemInviteCodeRequestDto request
    ) {
        UUID userId = parseUserId(jwt);
        log.info("User '{}' redeeming invite code '{}'", userId, request.getCode());
        RedeemInviteCodeResponseDto response = inviteCodeService.redeemInviteCode(userId, request.getCode());
        log.info("User '{}' redeemed invite code, assigned role '{}'", userId, response.getRoleAssigned());
        return ResponseEntity.ok(response);
    }

    /**
     * FIX I-2: isAdmin derived from JWT and passed to service.
     * FIX I-6: Default reason changed from "Revoked by creator" to "No reason provided".
     */
    @DeleteMapping("/{codeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<Void> revokeInviteCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID codeId,
            @RequestParam(required = false, defaultValue = "No reason provided") String reason
    ) {
        UUID revokerId = parseUserId(jwt);
        // FIX I-2: derive from already-verified JWT — no Keycloak round-trip in the service
        boolean isAdmin = hasRole(jwt, "ADMIN");
        log.info("User '{}' (admin={}) revoking invite code '{}', reason: {}", revokerId, isAdmin, codeId, reason);
        inviteCodeService.revokeInviteCode(revokerId, codeId, reason, isAdmin);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<Page<InviteCodeResponseDto>> listInviteCodes(
            @AuthenticationPrincipal Jwt jwt,
            Pageable pageable
    ) {
        UUID creatorId = parseUserId(jwt);
        Page<InviteCodeResponseDto> response;
        if (hasRole(jwt, "ADMIN")) {
            response = inviteCodeService.listAllInviteCodes(pageable);
        } else {
            response = inviteCodeService.listInviteCodesByCreator(creatorId, pageable);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<Page<InviteCodeResponseDto>> listEventInviteCodes(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID eventId,
            Pageable pageable
    ) {
        UUID userId = parseUserId(jwt);
        if (!hasRole(jwt, "ADMIN")) {
            authorizationService.requireOrganizerAccess(userId, eventId);
        }
        Page<InviteCodeResponseDto> response = inviteCodeService.listInviteCodesByEvent(eventId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * FIX I-4: validateInviteCreation() STAFF guard consolidated.
     *
     * BEFORE: The null-eventId guard for STAFF was checked in TWO places:
     *   1. Inside the organizer block (organizer generating STAFF without eventId)
     *   2. In a standalone STAFF block at the bottom (catches admin too)
     * This was logically correct but duplicated and fragile — reordering blocks
     * or adding a new role path could silently break the guarantee.
     *
     * AFTER: Single block structure:
     *   - ORGANIZER block: enforces role restrictions only (STAFF is the only allowed role).
     *     Does NOT check eventId — that is the STAFF block's job.
     *   - STAFF block: enforces eventId required for ALL callers (organizer or admin).
     *   - ADMIN-only block: ensures only admins can create ADMIN role invites.
     * Each concern is handled in exactly one place.
     */
    private void validateInviteCreation(Jwt jwt, String roleName, UUID eventId, UUID creatorId) {
        boolean isAdmin = hasRole(jwt, "ADMIN");
        boolean isOrganizer = hasRole(jwt, "ORGANIZER");

        // Organizer role restriction: organizers can only generate STAFF invites.
        // (eventId null for STAFF is caught separately in the STAFF block below.)
        if (isOrganizer && !isAdmin) {
            if (!"STAFF".equals(roleName)) {
                throw new IllegalArgumentException(
                        "Organizers can only create STAFF invites. Contact an ADMIN for other roles.");
            }
            // Verify organizer owns this event (service layer also validates, this is a fast pre-check)
            if (eventId != null) {
                authorizationService.requireOrganizerAccess(creatorId, eventId);
            }
        }

        // FIX I-4: Single STAFF eventId guard — covers both organizer and admin callers.
        if ("STAFF".equals(roleName)) {
            if (eventId == null) {
                throw new IllegalArgumentException("Event ID is required for STAFF invites");
            }
        } else {
            // Non-STAFF roles must not include an eventId
            if (eventId != null) {
                throw new IllegalArgumentException("Event ID should only be provided for STAFF invites");
            }
        }

        // Only admins can create ADMIN role invites
        if ("ADMIN".equals(roleName) && !isAdmin) {
            throw new IllegalArgumentException("Only ADMINs can create ADMIN role invites");
        }
    }

    /**
     * C-08 FIX (preserved): Reads realm_access.roles — the actual Keycloak JWT claim.
     * Keycloak puts roles in realm_access.roles (nested map), not at the top level.
     */
    private boolean hasRole(Jwt jwt, String role) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return false;
        Object roles = realmAccess.get("roles");
        if (roles instanceof Collection<?> roleList) {
            return roleList.contains(role);
        }
        return false;
    }
}