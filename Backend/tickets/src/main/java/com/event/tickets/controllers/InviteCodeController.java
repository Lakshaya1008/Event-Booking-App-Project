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

    @DeleteMapping("/{codeId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ORGANIZER')")
    public ResponseEntity<Void> revokeInviteCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID codeId,
            @RequestParam(required = false, defaultValue = "No reason provided") String reason
    ) {
        UUID revokerId = parseUserId(jwt);
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