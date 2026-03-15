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

    @Bean
    @Primary
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**",
                                "/actuator/info", "/actuator/metrics", "/actuator/metrics/**").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/v1/auth/register").permitAll()

                        // Swagger / OpenAPI — allow unauthenticated access to the API docs
                        // The docs only describe the API surface — no business data is exposed here.
                        // Remove these lines if you want Swagger locked behind authentication.
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs",
                                "/api-docs/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
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
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return authenticationConverter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Extracts roles from realm_access and resource_access JWT claims.
     * Adds ROLE_ prefix for Spring Security @PreAuthorize compatibility.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Realm-level roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?>) {
            for (Object role : (Collection<?>) realmAccess.get("roles")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
            }
        }

        // Client-specific roles
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null &&
                resourceAccess.get("event-ticket-platform-app") instanceof Map<?, ?> clientAccess) {
            Object clientRoles = clientAccess.get("roles");
            if (clientRoles instanceof Collection<?>) {
                for (Object role : (Collection<?>) clientRoles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toString()));
                }
            }
        }

        return authorities;
    }
}