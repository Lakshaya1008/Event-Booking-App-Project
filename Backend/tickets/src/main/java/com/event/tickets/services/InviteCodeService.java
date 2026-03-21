package com.event.tickets.services;

import com.event.tickets.domain.dtos.InviteCodeResponseDto;
import com.event.tickets.domain.dtos.RedeemInviteCodeResponseDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InviteCodeService {

    InviteCodeResponseDto generateInviteCode(UUID creatorId, String roleName,
                                             UUID eventId, int expirationHours);

    RedeemInviteCodeResponseDto redeemInviteCode(UUID userId, String code);

    /**
     * FIX I-2: isAdmin parameter added.
     *
     * BEFORE: revokeInviteCode() called keycloakAdminService.userHasRole(revokerId, "ADMIN")
     * internally — a live Keycloak API call on every revoke request just to determine
     * whether the revoker is an admin. Spring Security has already verified this via
     * the JWT before the controller method executes.
     *
     * AFTER: The controller passes isAdmin derived from the JWT claim (hasRole(jwt, "ADMIN")).
     * The service uses this value directly — no Keycloak round-trip for the ownership check.
     *
     * Security model unchanged: the @PreAuthorize on the endpoint still requires
     * ADMIN or ORGANIZER. The isAdmin flag only determines whether the ownership
     * check (revoker must be the code creator) is skipped for admins.
     */
    void revokeInviteCode(UUID revokerId, UUID codeId, String reason, boolean isAdmin);

    InviteCodeResponseDto getInviteCode(UUID codeId);

    Page<InviteCodeResponseDto> listInviteCodesByCreator(UUID creatorId, Pageable pageable);

    Page<InviteCodeResponseDto> listInviteCodesByEvent(UUID eventId, Pageable pageable);

    Page<InviteCodeResponseDto> listAllInviteCodes(Pageable pageable);

    int markExpiredCodes();
}