package com.event.tickets.config;

import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

import static org.mockito.Mockito.mock;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "test-user")
                // T-01 FIX: Keycloak puts roles in realm_access.roles, NOT a top-level "roles" claim.
                // The old .claim("roles", ...) mirrored the production C-08 bug — tests were passing
                // against a JWT structure that does not match real Keycloak tokens.
                // SecurityConfig.extractAuthorities() reads realm_access.roles — tests must match.
                .claim("realm_access", java.util.Map.of(
                        "roles", java.util.List.of("ATTENDEE")  // no ROLE_ prefix here; added in extractAuthorities
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Bean
    @Primary
    public Keycloak keycloakAdminClient() {
        // Return a mock Keycloak client for tests to avoid actual connections
        return mock(Keycloak.class);
    }
}