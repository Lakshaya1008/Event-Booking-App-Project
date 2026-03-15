package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Approval DTO
 *
 * FIX #7 follow-up: Added rejectedAt field so the admin approval list
 * endpoint can show when a rejection happened, not just approvedAt.
 * Previously the DTO only had approvedAt, so rejection timestamps were
 * invisible to admins reviewing the list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserApprovalDto {

    private String userId;
    private String name;
    private String email;
    private String approvalStatus;
    private java.util.List<String> roles;
    private LocalDateTime createdAt;
    private String rejectionReason;

    /** Only set when status is APPROVED. */
    private LocalDateTime approvedAt;

    /** FIX #7 follow-up: Only set when status is REJECTED. */
    private LocalDateTime rejectedAt;

    private String approvedByName;
}