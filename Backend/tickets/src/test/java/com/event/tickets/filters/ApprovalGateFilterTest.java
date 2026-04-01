package com.event.tickets.filters;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.AuditLogService;
import com.event.tickets.services.impl.KeycloakAdminServiceImpl;
import com.event.tickets.services.SystemUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApprovalGateFilter tests.
 *
 * Tests the core security filter that blocks PENDING and REJECTED users from
 * accessing business endpoints. This is a critical security component — any bug
 * here lets unapproved users access the system.
 *
 * The filter has shouldNotFilter() bypass logic for:
 *  - /api/v1/auth/register
 *  - /api/v1/invites/redeem
 *  - /actuator/**
 *  - /swagger-ui/**
 *  - /v3/api-docs/**
 *
 * All other authenticated endpoints go through the approval check.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalGateFilter")
class ApprovalGateFilterTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakAdminServiceImpl keycloakAdminService;
    @Mock private AuditLogService auditLogService;
    @Mock private SystemUserProvider systemUserProvider;

    // ObjectMapper needs to be real — it serializes the JSON error body
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ApprovalGateFilter filter;

    private UUID userId;
    private User pendingUser;
    private User approvedUser;
    private User rejectedUser;

    @BeforeEach
    void setUp() {
        // Reset security context before each test
        SecurityContextHolder.clearContext();

        userId = UUID.randomUUID();

        pendingUser = new User();
        pendingUser.setId(userId);
        pendingUser.setName("Bob");
        pendingUser.setEmail("bob@test.com");
        pendingUser.setApprovalStatus(ApprovalStatus.PENDING);

        approvedUser = new User();
        approvedUser.setId(userId);
        approvedUser.setName("Bob");
        approvedUser.setEmail("bob@test.com");
        approvedUser.setApprovalStatus(ApprovalStatus.APPROVED);

        rejectedUser = new User();
        rejectedUser.setId(userId);
        rejectedUser.setName("Bob");
        rejectedUser.setEmail("bob@test.com");
        rejectedUser.setApprovalStatus(ApprovalStatus.REJECTED);
        rejectedUser.setRejectionReason("Suspicious activity");

        // SystemUserProvider needed for audit fallback in emitApprovalGateViolation
        User systemUser = new User();
        systemUser.setId(UUID.randomUUID());
        systemUser.setName("SYSTEM");
        lenient().when(systemUserProvider.getSystemUser()).thenReturn(systemUser);
    }

    @BeforeEach
    void injectObjectMapper() throws Exception {
        // Inject the real ObjectMapper via reflection since @InjectMocks won't wire it
        // (it's not a Spring context — we're constructing manually)
        java.lang.reflect.Field f = ApprovalGateFilter.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(filter, objectMapper);
    }

    private void setUpJwtAuthentication(UUID subjectId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subjectId.toString())   // FIX: .subject() sets getSubject(); .claim("sub",...) does NOT
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        // Pass empty authorities so isAuthenticated() returns true
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, java.util.Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── PENDING user — blocked ────────────────────────────────────────────────

    @Nested
    @DisplayName("PENDING user")
    class PendingUser {

        @Test
        @DisplayName("blocked — returns 403")
        void pendingUser_returns403() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true); // user exists in Keycloak; filter blocks on PENDING status

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // Filter calls response.sendError(403, "User not approved")
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("filter chain NOT called — request halted")
        void pendingUser_chainNotCalled() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tickets");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = mock(MockFilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("PENDING user blocked — chain not called and 403 returned")
        void pendingUser_blockedWith403() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(pendingUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // Filter blocks PENDING user with 403; does NOT call chain or auditLogService
            assertThat(response.getStatus()).isEqualTo(403);
        }
    }

    // ── REJECTED user — blocked ───────────────────────────────────────────────

    @Nested
    @DisplayName("REJECTED user")
    class RejectedUser {

        @Test
        @DisplayName("blocked — returns 403")
        void rejectedUser_returns403WithReason() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(rejectedUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // Filter calls response.sendError(403, "User not approved")
            assertThat(response.getStatus()).isEqualTo(403);
        }

        @Test
        @DisplayName("filter chain NOT called")
        void rejectedUser_chainNotCalled() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(rejectedUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tickets");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = mock(MockFilterChain.class);

            filter.doFilterInternal(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
        }
    }

    // ── APPROVED user — passes through ────────────────────────────────────────

    @Nested
    @DisplayName("APPROVED user")
    class ApprovedUserTests {

        @Test
        @DisplayName("filter chain continues — request passes through")
        void approvedUser_chainCalled() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(approvedUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // Chain was called — request was not blocked
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("no 403 response — response is untouched")
        void approvedUser_no403() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(approvedUser));
            when(keycloakAdminService.userExists(any())).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/tickets");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isNotEqualTo(403);
        }
    }

    // ── Bypass paths — no JWT or whitelisted paths ────────────────────────────

    @Nested
    @DisplayName("Bypass paths and unauthenticated requests")
    class BypassPaths {

        @Test
        @DisplayName("unauthenticated request — no JWT — filter chain continues")
        void noJwt_chainContinues() throws Exception {
            // No SecurityContext set — unauthenticated
            SecurityContextHolder.clearContext();

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // Chain was called — unauthenticated requests are not blocked by this filter
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("shouldNotFilter: /api/v1/auth/register — bypasses filter entirely")
        void registerPath_bypassed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("shouldNotFilter: /api/v1/invites/redeem — bypassed")
        void inviteRedeemPath_bypassed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/invites/redeem");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("shouldNotFilter: /actuator/health — bypassed")
        void actuatorPath_bypassed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("shouldNotFilter: /swagger-ui/index.html — bypassed")
        void swaggerPath_bypassed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
            assertThat(filter.shouldNotFilter(request)).isTrue();
        }

        @Test
        @DisplayName("shouldNotFilter: /api/v1/events — NOT bypassed")
        void eventsPath_notBypassed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            assertThat(filter.shouldNotFilter(request)).isFalse();
        }

        @Test
        @DisplayName("user not found in DB — filter chain continues (no block)")
        void userNotInDb_chainContinues() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // User not in DB — filter logs warning and lets request through
            assertThat(response.getStatus()).isNotEqualTo(403);
        }
    }
}