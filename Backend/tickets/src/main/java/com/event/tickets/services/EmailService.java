package com.event.tickets.services;

import java.util.UUID;

/**
 * Email Service — sends plain-text notification emails for key business events.
 *
 * All methods are fire-and-forget. Email failures are logged but never propagate
 * exceptions to the caller. A failed email must never roll back a business operation.
 */
public interface EmailService {

    /** Sent to user after successful registration (account pending approval). */
    void sendRegistrationEmail(String toEmail, String userName);

    /** Sent to user when admin approves their account. */
    void sendApprovalEmail(String toEmail, String userName);

    /** Sent to user when admin rejects their account. */
    void sendRejectionEmail(String toEmail, String userName, String rejectionReason);

    /** Sent to purchaser after successful ticket purchase. */
    void sendTicketConfirmationEmail(String toEmail, String userName,
                                     String eventName, String ticketType,
                                     int quantity, UUID ticketId);

    /** Sent to each unique ticket holder when an event is cancelled. */
    void sendEventCancellationEmail(String toEmail, String userName, String eventName);
}