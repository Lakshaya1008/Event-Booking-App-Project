package com.event.tickets.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;

/**
 * FIXES APPLIED:
 *
 * FIX-SC1 — Keycloak client name externalized to application.properties.
 *   BEFORE: resourceAccess.get("event-ticket-platform-app") — hardcoded string.
 *   If the Keycloak client is ever renamed, all @PreAuthorize checks silently
 *   return 403 with no obvious error message.
 *   AFTER: keycloak.client-id property injected via @Value. Change the client
 *   name in config without touching any Java code.
 *
 *   Add to application.properties:
 *     keycloak.client-id=${KEYCLOAK_CLIENT_ID:event-ticket-platform-app}
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomSecurityErrorHandler customSecurityErrorHandler;

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    /**
     * FIX-SC1: Read client ID from config, not hardcoded.
     * Add keycloak.client-id to application.properties / environment variables.
     */
    @Value("${keycloak.client-id:event-ticket-platform-app}")
    private String keycloakClientId;

    @Bean
    @Primary
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/auth/register").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html", "/swagger-ui/**",
                                "/api-docs", "/api-docs/**",
                                "/v3/api-docs", "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(customSecurityErrorHandler)
                        .accessDeniedHandler(customSecurityErrorHandler)
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(customSecurityErrorHandler)
                        .accessDeniedHandler(customSecurityErrorHandler)
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Reads roles from realm_access.roles and resource_access.<clientId>.roles.
     * Adds ROLE_ prefix for Spring Security @PreAuthorize compatibility.
     *
     * FIX-SC1: Uses injected keycloakClientId instead of a hardcoded string.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Realm-level roles (e.g. ADMIN, ORGANIZER, STAFF, ATTENDEE)
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> realmRoles) {
            for (Object role : realmRoles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
            }
        }

        // Client-level roles — FIX-SC1: keycloakClientId from config, not hardcoded
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null &&
                resourceAccess.get(keycloakClientId) instanceof Map<?, ?> clientAccess) {
            Object clientRoles = clientAccess.get("roles");
            if (clientRoles instanceof Collection<?> roles) {
                for (Object role : roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                }
            }
        }

        return authorities;
    }
}