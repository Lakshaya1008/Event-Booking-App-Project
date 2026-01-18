package com.event.tickets.domain.entities;

/**
 * Approval Status Enum
 *
 * Represents the approval state of a user account.
 * Used to implement admin approval gate for new registrations.
 *
 * Workflow:
 * 1. User registers via invite code → PENDING
 * 2. Admin approves → APPROVED (full access)
 * 3. Admin rejects → REJECTED (access blocked)
 *
 * Note: Approval status is SEPARATE from Keycloak roles.
 * - Keycloak manages authentication and roles (in JWT)
 * - Backend manages approval status (in database)
 * - Users need BOTH valid role AND approved status to access system
 */
public enum ApprovalStatus {
    /**
     * User account is pending admin approval.
     * User can authenticate with Keycloak but cannot access backend endpoints.
     */
    PENDING,

    /**
     * User account has been approved by an admin.
     * User has full access according to their assigned roles.
     */
    APPROVED,

    /**
     * User account has been rejected by an admin.
     * User cannot access backend endpoints even with valid JWT.
     */
    REJECTED
}
