package com.event.tickets.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * TEST-FIX-4: Updated for CLIENT_CREDENTIALS grant (FIX-KC1).
 *
 * BEFORE: Properties included username and password, matching the old PASSWORD grant config.
 * The config class had username/password fields that were used in KeycloakBuilder.
 *
 * AFTER: Config only needs serverUrl, realm, clientId, clientSecret, targetRealm.
 * username and password fields are removed — they were for the PASSWORD grant which
 * is now replaced by CLIENT_CREDENTIALS. Providing them would cause Spring to reject
 * unknown properties (or silently ignore them, masking the migration).
 *
 * Also added: targetRealm property which was missing from the old test but is required
 * by KeycloakAdminServiceImpl via @Value("${keycloak.admin.target-realm}").
 */
@DisplayName("KeycloakAdminConfig wiring")
class KeycloakAdminConfigWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(KeycloakAdminConfig.class)
            .withPropertyValues(
                    "keycloak.admin.server-url=http://localhost:9090",
                    // realm = master (the auth realm for the service account token)
                    "keycloak.admin.realm=master",
                    // client-id of the dedicated service account client
                    "keycloak.admin.client-id=event-ticket-backend",
                    "keycloak.admin.client-secret=test-client-secret",
                    // target-realm = where application users live
                    "keycloak.admin.target-realm=event-ticket-platform"
                    // NOTE: username and password intentionally omitted — CLIENT_CREDENTIALS
                    // grant does not use human credentials
            );

    @Test
    @DisplayName("creates keycloakAdminClient bean from CLIENT_CREDENTIALS properties")
    void createsKeycloakAdminClientBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Keycloak.class);
            assertThat(context.getBean(Keycloak.class)).isNotNull();
        });
    }

    @Test
    @DisplayName("does not fail when clientSecret is blank (public client fallback)")
    void createsBean_withBlankClientSecret() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(KeycloakAdminConfig.class)
                .withPropertyValues(
                        "keycloak.admin.server-url=http://localhost:9090",
                        "keycloak.admin.realm=master",
                        "keycloak.admin.client-id=event-ticket-backend",
                        "keycloak.admin.client-secret=",
                        "keycloak.admin.target-realm=event-ticket-platform"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(Keycloak.class);
                });
    }
}