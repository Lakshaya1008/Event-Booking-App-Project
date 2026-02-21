package com.event.tickets.scheduler;

import com.event.tickets.repositories.InviteCodeRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invite Code Expiry Scheduler
 *
 * WHY THIS EXISTS:
 * InviteCodeRepository.markExpiredCodes() and findExpiredCodes() were built but
 * never wired to a scheduler, so PENDING invite codes past their expiresAt date
 * stayed in PENDING state forever. The table would accumulate thousands of stale
 * records that showed as PENDING in admin lists but were actually unusable.
 *
 * Expiry IS checked at redemption time (InviteCode.isValid()), so no security
 * hole existed — but the data was visually misleading and wasted storage.
 *
 * SCHEDULE: runs every 15 minutes. Invite codes are typically valid for 24-48 h,
 * so 15-minute granularity is more than precise enough. Adjust via:
 *   app.scheduler.invite-expiry-cron in application.properties
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InviteCodeExpiryScheduler {

    private final InviteCodeRepository inviteCodeRepository;

    /**
     * Marks all PENDING invite codes whose expiresAt is in the past as EXPIRED.
     * Uses a single bulk UPDATE query — no per-row round trips.
     *
     * Runs every 15 minutes. First execution 5 minutes after startup
     * (initialDelay gives the app time to fully start before the first run).
     */
    @Scheduled(
            fixedRateString  = "${app.scheduler.invite-expiry-rate-ms:900000}",   // 15 min
            initialDelayString = "${app.scheduler.invite-expiry-delay-ms:300000}" // 5 min after startup
    )
    @Transactional
    public void expireStaleInviteCodes() {
        LocalDateTime now = LocalDateTime.now();

        try {
            int expired = inviteCodeRepository.markExpiredCodes(now);

            if (expired > 0) {
                log.info("Invite code expiry run: marked {} code(s) as EXPIRED", expired);
            } else {
                log.debug("Invite code expiry run: no codes to expire");
            }

        } catch (Exception e) {
            // Log and swallow — a failed expiry run should never crash the application.
            // The codes will just be caught at redemption time as usual.
            log.error("Invite code expiry scheduler failed: {}", e.getMessage(), e);
        }
    }
}
