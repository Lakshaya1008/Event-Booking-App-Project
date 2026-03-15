package com.event.tickets.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Rate Limiting Filter
 *
 * FIX #13: Replaced ConcurrentHashMap with Caffeine caches that evict entries
 * after 1 hour of inactivity. The original ConcurrentHashMap grew without bound —
 * every unique IP and userId got a permanent entry, causing OOM in long deployments.
 */
@Component
@Slf4j
public class RateLimitingFilter implements Filter {

    // FIX #13: Caffeine cache with 1-hour expiry replaces unbounded ConcurrentHashMap
    private final Cache<String, Bucket> ipBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, Bucket> userBuckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpRequest  = (HttpServletRequest)  request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientIp = getClientIP(httpRequest);
        String path     = httpRequest.getRequestURI();

        Bucket bucket;

        if (isAuthEndpoint(path)) {
            bucket = authBuckets.get(clientIp, k -> createAuthBucket());
        } else {
            String userId = extractUserIdFromJwt(httpRequest);
            if (userId != null) {
                bucket = userBuckets.get(userId, k -> createAuthenticatedUserBucket());
            } else {
                bucket = ipBuckets.get(clientIp, k -> createPublicBucket());
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

    /** 10 req/min for auth endpoints */
    private Bucket createAuthBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                .build();
    }

    /** 300 req/min per authenticated user */
    private Bucket createAuthenticatedUserBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(300, Refill.intervally(300, Duration.ofMinutes(1))))
                .build();
    }

    /** 60 req/min per IP for public endpoints */
    private Bucket createPublicBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(60, Refill.intervally(60, Duration.ofMinutes(1))))
                .build();
    }

    private boolean isAuthEndpoint(String path) {
        return path.contains("/auth/login")
                || path.contains("/auth/register")
                || path.contains("/auth/refresh")
                || path.contains("/auth/logout")
                || path.contains("/token");
    }

    private String extractUserIdFromJwt(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String payload = new String(payloadBytes);
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
            return null;
        }
    }

    private String padBase64(String base64) {
        int padding = 4 - (base64.length() % 4);
        if (padding < 4) base64 = base64 + "=".repeat(padding);
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