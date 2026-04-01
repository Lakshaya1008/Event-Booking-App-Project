package com.event.tickets.filters;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
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
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UserProvisioningFilter tests.
 *
 * BUG-T1 FIX: The previous version of this test file tested the OLD, incorrect
 * behaviour (read-only filter that lets everything through). The actual filter
 * source (post-BUG3 fix) is a BLOCKING filter:
 *
 *   • No JWT                            → chain continues (unauthenticated OK)
 *   • JWT present + user in DB, status OK → chain continues (200)
 *   • JWT present + user NOT in DB     → 401 "User not provisioned in system"
 *   • JWT present + user.approvalStatus == null → 500 "User state invalid"
 *   • JWT present + Keycloak user doesn't exist → 401 (tested via @Autowired optional)
 *
 * The filter does NOT auto-create users — that would bypass registration+approval.
 * It BLOCKS users whose DB record is missing (desync = untrusted state).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserProvisioningFilter")
class UserProvisioningFilterTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserProvisioningFilter filter;

    private UUID userId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        userId = UUID.randomUUID();
    }

    private void setUpJwtAuthentication(UUID subjectId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subjectId.toString())   // FIX: .subject() sets getSubject(); .claim("sub",...) does NOT
                .claim("email", "user@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        // FIX: pass Collections.emptyList() as authorities so isAuthenticated() returns true.
        // JwtAuthenticationToken(jwt) with no authorities defaults isAuthenticated=false,
        // so the filter short-circuits at the !authentication.isAuthenticated() check,
        // never calling userRepository.findById() at all.
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User buildUser(ApprovalStatus status) {
        User u = new User();
        u.setId(userId);
        u.setName("Test User");
        u.setEmail("user@test.com");
        u.setApprovalStatus(status);
        return u;
    }

    // ── JWT present + user in DB (happy path) ─────────────────────────────────

    @Nested
    @DisplayName("JWT present — user in DB with valid approval status")
    class UserExistsInDb {

        @Test
        @DisplayName("APPROVED user — filter chain continues (200)")
        void approvedUser_chainContinues() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(ApprovalStatus.APPROVED)));

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    response,
                    new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("PENDING user — filter chain continues (approval gate handles blocking later)")
        void pendingUser_chainContinues() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(ApprovalStatus.PENDING)));

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    response,
                    new MockFilterChain());

            // UserProvisioningFilter lets PENDING through — ApprovalGateFilter (@Order 2) handles it
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("userRepository.findById called once with correct userId")
        void findByIdCalledWithCorrectId() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(buildUser(ApprovalStatus.APPROVED)));

            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    new MockHttpServletResponse(),
                    new MockFilterChain());

            verify(userRepository).findById(userId);
        }
    }

    // ── JWT present + user NOT in DB — BUG-T1 FIX ────────────────────────────

    @Nested
    @DisplayName("JWT present — user NOT in DB (desync) — BUG-T1 FIX: returns 401")
    class UserNotInDb {

        @Test
        @DisplayName("BUG-T1 FIX — user not found in DB returns 401 (NOT 200)")
        void userNotInDb_returns401() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    response,
                    new MockFilterChain());

            // CRITICAL: desync is BLOCKED — the filter returns 401, does NOT let the request through
            assertThat(response.getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("BUG-T1 FIX — filter chain NOT called when user not found")
        void userNotInDb_chainNotCalled() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            MockFilterChain chain = mock(MockFilterChain.class);
            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    new MockHttpServletResponse(),
                    chain);

            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("BUG-T1 FIX — no user is auto-created — read-only behaviour preserved")
        void userNotInDb_noSaveCalledOnRepository() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    new MockHttpServletResponse(),
                    new MockFilterChain());

            // The filter must NEVER call save() — presence check only
            verify(userRepository, never()).save(any());
            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    // ── User with null approvalStatus — returns 500 ───────────────────────────

    @Nested
    @DisplayName("JWT present — user in DB but approvalStatus is null (corrupt state)")
    class NullApprovalStatus {

        @Test
        @DisplayName("null approvalStatus → 500 Internal Server Error")
        void nullApprovalStatus_returns500() throws Exception {
            User corruptUser = buildUser(null); // deliberately null
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(corruptUser));

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    response,
                    new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(500);
        }

        @Test
        @DisplayName("null approvalStatus — chain NOT called")
        void nullApprovalStatus_chainNotCalled() throws Exception {
            User corruptUser = buildUser(null);
            setUpJwtAuthentication(userId);
            when(userRepository.findById(userId)).thenReturn(Optional.of(corruptUser));

            MockFilterChain chain = mock(MockFilterChain.class);
            filter.doFilterInternal(
                    new MockHttpServletRequest("GET", "/api/v1/events"),
                    new MockHttpServletResponse(),
                    chain);

            verify(chain, never()).doFilter(any(), any());
        }
    }

    // ── No JWT — unauthenticated request ──────────────────────────────────────

    @Nested
    @DisplayName("No JWT — unauthenticated request")
    class NoJwt {

        @Test
        @DisplayName("no JWT — DB not queried, filter chain continues (200)")
        void noJwt_dbNotQueried() throws Exception {
            SecurityContextHolder.clearContext();

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(
                    new MockHttpServletRequest("POST", "/api/v1/auth/register"),
                    response,
                    new MockFilterChain());

            // No JWT → no DB lookup → chain continues (unauthenticated requests pass through)
            verify(userRepository, never()).findById(any());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}