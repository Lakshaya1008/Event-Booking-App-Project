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
     * Primary security filter chain, which disables CSRF and enables stateless sessions.
     * It also configures OAuth2 resource server with JWT authentication and custom error handling.
     *
     * Public endpoints:
     * - /actuator/health/** - Health check endpoints
     * - /api/v1/auth/register - Public registration endpoint (invite-code based)
     */
    @Bean
    @Primary
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/auth/register").permitAll() // Public registration endpoint
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

    /**
     * Custom converter: extracts roles from both realm_access and resource_access.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return authenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse allowed origins from comma-separated string
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

        // Parse allowed methods from comma-separated string
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L); // Cache preflight requests for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Extracts authorities from JWT claims and adds ROLE_ prefix for Spring Security.
     *
     * Spring Security's hasRole() method automatically prepends "ROLE_" to the role name,
     * so we need to add the prefix here to match. For example:
     * - JWT contains: "ORGANIZER"
     * - We create: "ROLE_ORGANIZER"
     * - @PreAuthorize("hasRole('ORGANIZER')") checks for: "ROLE_ORGANIZER" ✓
     *
     * Roles are extracted from:
     * 1. realm_access.roles (realm-level roles)
     * 2. resource_access.event-ticket-platform-app.roles (client-specific roles)
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Realm roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?>) {
            for (Object role : (Collection<?>) realmAccess.get("roles")) {
                // Add ROLE_ prefix for Spring Security compatibility
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
            }
        }

        // Client roles (for your event-ticket-platform-app)
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && resourceAccess.get("event-ticket-platform-app") instanceof Map<?, ?> clientAccess) {
            Object clientRoles = clientAccess.get("roles");
            if (clientRoles instanceof Collection<?>) {
                for (Object role : (Collection<?>) clientRoles) {
                    // Add ROLE_ prefix for Spring Security compatibility
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                }
            }
        }

        return authorities;
    }
}
