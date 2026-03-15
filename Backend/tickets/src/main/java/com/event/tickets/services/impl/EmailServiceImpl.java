package com.event.tickets.services.impl;

import com.event.tickets.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Email Service using Brevo Transactional REST API.
 *
 * Uses pure HTTPS — no SMTP ports. Works on Render free tier.
 * Free tier: 300 emails/day.
 * RestTemplate is already in spring-boot-starter-web — no extra dependency needed.
 *
 * Setup:
 * 1. Sign up free at brevo.com
 * 2. Settings → API Keys → Generate key
 * 3. Set BREVO_API_KEY environment variable on your server
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.brevo.api-key}")
    private String apiKey;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.mail.enabled:true}")
    private boolean emailEnabled;

    @Override
    public void sendRegistrationEmail(String toEmail, String userName) {
        send(toEmail, userName,
                "Registration received — pending approval",
                "Hello " + userName + ",\n\n"
                        + "Thank you for registering on the Event Booking Platform.\n\n"
                        + "Your account is currently pending admin approval.\n"
                        + "You will receive another email once your account has been reviewed.\n\n"
                        + "This usually takes less than 24 hours.\n\n"
                        + "Regards,\nEvent Booking Platform");
    }

    @Override
    public void sendApprovalEmail(String toEmail, String userName) {
        send(toEmail, userName,
                "Your account has been approved",
                "Hello " + userName + ",\n\n"
                        + "Great news! Your account on the Event Booking Platform has been approved.\n\n"
                        + "You can now log in and start browsing events.\n\n"
                        + "Regards,\nEvent Booking Platform");
    }

    @Override
    public void sendRejectionEmail(String toEmail, String userName, String rejectionReason) {
        send(toEmail, userName,
                "Your account registration was not approved",
                "Hello " + userName + ",\n\n"
                        + "We regret to inform you that your account registration has not been approved.\n\n"
                        + "Reason: " + (rejectionReason != null ? rejectionReason : "No reason provided")
                        + "\n\nIf you believe this is a mistake, please contact support.\n\n"
                        + "Regards,\nEvent Booking Platform");
    }

    @Override
    public void sendTicketConfirmationEmail(String toEmail, String userName,
                                            String eventName, String ticketType,
                                            int quantity, UUID ticketId) {
        send(toEmail, userName,
                "Ticket confirmed — " + eventName,
                "Hello " + userName + ",\n\n"
                        + "Your ticket purchase is confirmed!\n\n"
                        + "Event:       " + eventName + "\n"
                        + "Ticket Type: " + ticketType + "\n"
                        + "Quantity:    " + quantity + "\n"
                        + "Ticket ID:   " + ticketId + "\n\n"
                        + "You can view and download your QR code by logging into your account.\n\n"
                        + "See you at the event!\n\n"
                        + "Regards,\nEvent Booking Platform");
    }

    @Override
    public void sendEventCancellationEmail(String toEmail, String userName, String eventName) {
        send(toEmail, userName,
                "Event cancelled — " + eventName,
                "Hello " + userName + ",\n\n"
                        + "We regret to inform you that the following event has been cancelled:\n\n"
                        + "Event: " + eventName + "\n\n"
                        + "Your ticket has been automatically cancelled.\n"
                        + "If a refund is applicable, it will be processed according to the refund policy.\n\n"
                        + "We apologise for the inconvenience.\n\n"
                        + "Regards,\nEvent Booking Platform");
    }

    // ── core send ─────────────────────────────────────────────────────────────

    private void send(String toEmail, String toName, String subject, String textContent) {
        if (!emailEnabled) {
            log.debug("Email disabled — skipping to={}", toEmail);
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, Object> body = Map.of(
                    "sender",      Map.of("name", fromName, "email", fromEmail),
                    "to",          new Object[]{ Map.of("email", toEmail, "name", toName) },
                    "subject",     subject,
                    "textContent", textContent
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Email sent via Brevo: to={}, subject={}", toEmail, subject);
            } else {
                log.error("Brevo returned non-2xx: status={}, to={}", response.getStatusCode(), toEmail);
            }
        } catch (Exception e) {
            // Never propagate — email failure must not roll back a business operation
            log.error("Failed to send email via Brevo: to={}, error={}", toEmail, e.getMessage());
        }
    }
}