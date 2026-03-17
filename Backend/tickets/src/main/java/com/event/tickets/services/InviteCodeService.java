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

    void revokeInviteCode(UUID revokerId, UUID codeId, String reason);

    InviteCodeResponseDto getInviteCode(UUID codeId);

    Page<InviteCodeResponseDto> listInviteCodesByCreator(UUID creatorId, Pageable pageable);

    Page<InviteCodeResponseDto> listInviteCodesByEvent(UUID eventId, Pageable pageable);

    /**
     * FIX ISSUE 21: Added to interface.
     * InviteCodeServiceImpl.listAllInviteCodes() existed without @Override and was not
     * declared in this interface. The InviteCodeController injects InviteCodeService (the interface),
     * so calling listAllInviteCodes() through the interface would fail at runtime with
     * a NoSuchMethodException / compilation error depending on how the controller references it.
     */
    Page<InviteCodeResponseDto> listAllInviteCodes(Pageable pageable);

    int markExpiredCodes();
}