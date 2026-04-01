package com.event.tickets.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import com.event.tickets.services.KeycloakAdminService;

@Component("keycloakHealthIndicator")
@RequiredArgsConstructor
@Slf4j
public class KeycloakHealthIndicator extends AbstractHealthIndicator {

    private final KeycloakAdminService keycloakAdminService;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            // Try a simple Keycloak API call to verify connectivity
            // This calls the admin API which is always available
            keycloakAdminService.getUserCount();

            builder
                .up()
                .withDetail("service", "Keycloak Admin API")
                .withDetail("status", "reachable");

            log.debug("Keycloak health check passed");
        } catch (Exception e) {
            log.warn("Keycloak health check failed: {}", e.getMessage());
            builder
                .down()
                .withDetail("service", "Keycloak Admin API")
                .withDetail("status", "unreachable")
                .withDetail("error", e.getMessage())
                .withException(e);
        }
    }
}

