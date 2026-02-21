package com.event.tickets.domain.entities;

/**
 * Audit Action Enum
 *
 * Defines all auditable actions in the system.
 * Immutable - actions are append-only to audit log.
 */
public enum AuditAction {
    // Registration Operations
    REGISTRATION_ATTEMPT,
    REGISTRATION_SUCCESS,
    REGISTRATION_FAILED,

    // Approval Operations
    USER_APPROVED,       // Admin approved a pending user
    USER_REJECTED,       // Admin rejected a pending user

    // Role Management
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    // Event Staff Management
    STAFF_ASSIGNED,
    STAFF_REMOVED,

    // Invite Code Operations
    INVITE_CREATED,
    INVITE_REDEEMED,
    INVITE_REVOKED,

    // Event Operations
    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_DELETED,

    // Ticket Operations
    TICKET_VALIDATED,
    TICKET_PURCHASED,
    TICKET_PURCHASE_BLOCKED,  // Attempted purchase outside sales window or wrong event status

    // QR Code Operations (READ-ONLY EXPORTS)
    QR_CODE_VIEWED,
    QR_CODE_DOWNLOADED_PNG,
    QR_CODE_DOWNLOADED_PDF,

    // Report Exports (READ-ONLY)
    SALES_REPORT_EXPORTED,

    // Approval Gate Operations
    APPROVAL_GATE_VIOLATION,

    // Validation Failures
    FAILED_TICKET_VALIDATION,
    FAILED_INVITE_REDEMPTION
}
