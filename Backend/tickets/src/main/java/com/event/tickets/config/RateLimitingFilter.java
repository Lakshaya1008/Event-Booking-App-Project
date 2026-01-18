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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Rate Limiting Filter
 *
 * Implements token bucket algorithm for rate limiting API requests.
 *
 * Rate Limits:
 * - Public endpoints: 100 requests per minute per IP
 * - Authentication: 10 requests per minute per IP
 * - Authenticated: 1000 requests per minute per IP
 *
 * Uses Bucket4j for token bucket implementation.
 */
@Component
@Slf4j
public class RateLimitingFilter implements Filter {

  private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
  private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String clientIp = getClientIP(httpRequest);
    String path = httpRequest.getRequestURI();

    // Determine rate limit based on endpoint
    Bucket bucket;
    if (path.contains("/auth/") || path.contains("/token")) {
      // Strict limit for authentication endpoints
      bucket = authBuckets.computeIfAbsent(clientIp, k -> createAuthBucket());
    } else {
      // Standard limit for other endpoints
      bucket = ipBuckets.computeIfAbsent(clientIp, k -> createStandardBucket());
    }

    if (bucket.tryConsume(1)) {
      // Request allowed
      chain.doFilter(request, response);
    } else {
      // Rate limit exceeded
      log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
      httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      httpResponse.setContentType("application/json");
      httpResponse.getWriter().write(
          "{\"error\":\"Too Many Requests\"," +
          "\"message\":\"Rate limit exceeded. Please try again later.\"," +
          "\"statusCode\":429}"
      );
    }
  }

  /**
   * Creates bucket for standard API endpoints.
   * Limit: 1000 requests per minute
   */
  private Bucket createStandardBucket() {
    Bandwidth limit = Bandwidth.classic(
        1000,
        Refill.intervally(1000, Duration.ofMinutes(1))
    );
    return Bucket.builder()
        .addLimit(limit)
        .build();
  }

  /**
   * Creates bucket for authentication endpoints.
   * Limit: 10 requests per minute (stricter to prevent brute force)
   */
  private Bucket createAuthBucket() {
    Bandwidth limit = Bandwidth.classic(
        10,
        Refill.intervally(10, Duration.ofMinutes(1))
    );
    return Bucket.builder()
        .addLimit(limit)
        .build();
  }

  /**
   * Extracts client IP address from request.
   * Handles proxy headers (X-Forwarded-For).
   */
  private String getClientIP(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
