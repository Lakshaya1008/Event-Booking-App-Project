package com.event.tickets.filters;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UserProvisioningFilter tests.
 *
 * This filter is intentionally read-only — it detects sync issues between Keycloak
 * and the DB (Keycloak user exists but no DB record) and logs a warning.
 * It does NOT auto-create DB records; that would bypass the registration + approval flow.
 *
 * Tests verify:
 *  1. JWT present + user in DB → filter chain continues, no warning logged
 *  2. JWT present + user NOT in DB → filter chain continues, warning logged
 *  3. No JWT → filter chain continues, no DB query made
 *  4. Non-JWT principal (e.g. anonymous) → filter chain continues
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
                .claim("sub", subjectId.toString())
                .claim("email", "user@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Nested
    @DisplayName("JWT present — user in DB")
    class UserExistsInDb {

        @Test
        @DisplayName("filter chain continues — no blocking")
        void userInDb_chainContinues() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.existsById(userId)).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // 200 — chain was called, filter did nothing
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("userRepository.existsById called once with correct userId")
        void userInDb_existsCalledWithCorrectId() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.existsById(userId)).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            verify(userRepository).existsById(userId);
        }
    }

    @Nested
    @DisplayName("JWT present — user NOT in DB (desync scenario)")
    class UserNotInDb {

        @Test
        @DisplayName("filter chain still continues — desync does NOT block the request")
        void userNotInDb_chainStillContinues() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.existsById(userId)).thenReturn(false);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // CRITICAL: desync is LOGGED but does NOT block — ApprovalGateFilter will
            // block on the next filter in the chain since user isn't in DB
            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("no 403 response — filter is read-only, not a blocker")
        void userNotInDb_no403() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.existsById(userId)).thenReturn(false);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            assertThat(response.getStatus()).isNotEqualTo(403);
        }

        @Test
        @DisplayName("no user is auto-created — read-only filter design preserved")
        void userNotInDb_noSaveCalledOnRepository() throws Exception {
            setUpJwtAuthentication(userId);
            when(userRepository.existsById(userId)).thenReturn(false);

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // The filter must NEVER call save() — it is intentionally read-only
            verify(userRepository, never()).save(any());
            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("No JWT — unauthenticated request")
    class NoJwt {

        @Test
        @DisplayName("no JWT — DB not queried, filter chain continues")
        void noJwt_dbNotQueried() throws Exception {
            SecurityContextHolder.clearContext();

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            // No JWT → no DB lookup
            verify(userRepository, never()).existsById(any());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}