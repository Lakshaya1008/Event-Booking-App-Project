package com.event.tickets.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("KeycloakAdminConfig wiring")
class KeycloakAdminConfigWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(KeycloakAdminConfig.class)
            .withPropertyValues(
                    "keycloak.admin.server-url=http://localhost:9090",
                    "keycloak.admin.realm=event-ticket-platform",
                    "keycloak.admin.client-id=event-ticket-platform-app",
                    "keycloak.admin.client-secret=test-secret",
                    "keycloak.admin.username=admin",
                    "keycloak.admin.password=admin");

    @Test
    @DisplayName("creates keycloakAdminClient bean from configured properties")
    void createsKeycloakAdminClientBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Keycloak.class);
            assertThat(context.getBean(Keycloak.class)).isNotNull();
        });
    }
}



