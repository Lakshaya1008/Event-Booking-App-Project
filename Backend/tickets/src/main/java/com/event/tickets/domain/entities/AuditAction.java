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

    // Approval Gate Operations
    USER_APPROVED,
    USER_REJECTED,
    APPROVAL_GATE_VIOLATION,

    // Role Management
    ROLE_ASSIGNED,
    ROLE_REVOKED,

    // Admin Promotion (high-severity — ADMIN granted via invite code)
    ADMIN_ROLE_GRANTED_VIA_INVITE,

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
    EVENT_CANCELLED,

    // Ticket Operations
    TICKET_VALIDATED,
    TICKET_PURCHASED,
    TICKET_CANCELLED_BY_EVENT,

    // Organizer self-purchase (flagged for audit visibility)
    ORGANIZER_SELF_PURCHASE,

    // QR Code Operations (READ-ONLY EXPORTS)
    QR_CODE_VIEWED,
    QR_CODE_DOWNLOADED_PNG,
    QR_CODE_DOWNLOADED_PDF,

    // Report Exports (READ-ONLY)
    SALES_REPORT_EXPORTED,

    // Validation Failures
    FAILED_TICKET_VALIDATION,
    FAILED_INVITE_REDEMPTION
}
