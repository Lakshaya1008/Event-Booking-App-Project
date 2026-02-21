package com.event.tickets.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Rate Limiting Filter
 *
 * FIX applied: authenticated requests are now keyed on the JWT subject (userId),
 * not on IP address alone.
 *
 * WHY THIS MATTERS:
 * - The old code keyed every bucket on IP. Behind NAT or a cloud load balancer,
 *   every user in the same office or CDN cluster shared ONE bucket.
 * - A single heavy user could exhaust the bucket for everyone behind that IP.
 * - Worse, auth endpoints (login/register) were also IP-keyed, so a busy
 *   office network could trigger the 10-req/min auth limit for all users.
 *
 * NEW STRATEGY:
 * - Auth endpoints (login, register, refresh, logout): IP-keyed, 10 req/min.
 *   IP is correct here because the user has no token yet.
 * - Authenticated endpoints: USER-keyed (JWT sub claim), 300 req/min per user.
 *   Falls back to IP-keyed if no JWT is present (shouldn't happen but safe).
 * - Unauthenticated non-auth endpoints (actuator, etc.): IP-keyed, 60 req/min.
 *
 * NOTE: Buckets are in-memory (ConcurrentHashMap). They reset on restart.
 * This is acceptable for the current deployment. If you move to multi-instance,
 * replace with Redis-backed Bucket4j (bucket4j-redis dependency + RedisClient).
 */
@Component
@Slf4j
public class RateLimitingFilter implements Filter {

    // Keyed by IP — for unauthenticated / auth endpoints
    private final Map<String, Bucket> ipBuckets      = new ConcurrentHashMap<>();
    private final Map<String, Bucket> authBuckets    = new ConcurrentHashMap<>();

    // Keyed by userId — for authenticated endpoints
    private final Map<String, Bucket> userBuckets    = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIP(httpRequest);
        String path     = httpRequest.getRequestURI();

        Bucket bucket;

        if (isAuthEndpoint(path)) {
            // Strict IP-based limit for login / register — no token exists yet
            bucket = authBuckets.computeIfAbsent(clientIp, k -> createAuthBucket());

        } else {
            // For every other endpoint, try to key on the authenticated user
            String userId = extractUserIdFromJwt(httpRequest);

            if (userId != null) {
                // Authenticated request — key on userId so users don't share limits
                bucket = userBuckets.computeIfAbsent(userId, k -> createAuthenticatedUserBucket());
            } else {
                // Unauthenticated non-auth endpoint (actuator health, etc.) — IP-keyed
                bucket = ipBuckets.computeIfAbsent(clientIp, k -> createPublicBucket());
            }
        }

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: path={}, ip={}", path, clientIp);
            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"Too Many Requests\"," +
                            "\"message\":\"Rate limit exceeded. Please slow down and try again.\"," +
                            "\"statusCode\":429}"
            );
        }
    }

    // ─── bucket factories ──────────────────────────────────────────────────────

    /** 10 requests per minute — auth endpoints (login, register, refresh, logout) */
    private Bucket createAuthBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    /** 300 requests per minute per authenticated user — generous but bounded */
    private Bucket createAuthenticatedUserBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(300, Refill.intervally(300, Duration.ofMinutes(1))))
                .build();
    }

    /** 60 requests per minute per IP — unauthenticated public endpoints */
    private Bucket createPublicBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1))))
                .build();
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private boolean isAuthEndpoint(String path) {
        return path.contains("/auth/login")
                || path.contains("/auth/register")
                || path.contains("/auth/refresh")
                || path.contains("/auth/logout")
                || path.contains("/token");
    }

    /**
     * Extracts the JWT subject (userId) from the Authorization header WITHOUT
     * full signature validation — this is intentional. We only need the subject
     * to build a stable bucket key. Spring Security has already validated the
     * signature earlier in the filter chain, so we are not trusting this value
     * for access control — only for rate-limiting bucket selection.
     *
     * Returns null if no valid Bearer token is present.
     */
    private String extractUserIdFromJwt(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null;
            }

            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            // Decode payload (base64url, no padding)
            byte[] payloadBytes = Base64.getUrlDecoder().decode(
                    padBase64(parts[1])
            );
            String payload = new String(payloadBytes);

            // Extract "sub" claim with a simple string search (no JSON lib needed)
            int subIdx = payload.indexOf("\"sub\"");
            if (subIdx == -1) return null;

            int colonIdx = payload.indexOf(':', subIdx);
            if (colonIdx == -1) return null;

            int startQuote = payload.indexOf('"', colonIdx + 1);
            if (startQuote == -1) return null;

            int endQuote = payload.indexOf('"', startQuote + 1);
            if (endQuote == -1) return null;

            return payload.substring(startQuote + 1, endQuote);

        } catch (Exception e) {
            // Any decode failure → fall back to IP-based bucket
            return null;
        }
    }

    private String padBase64(String base64) {
        int padding = 4 - (base64.length() % 4);
        if (padding < 4) {
            base64 = base64 + "=".repeat(padding);
        }
        return base64;
    }

    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
