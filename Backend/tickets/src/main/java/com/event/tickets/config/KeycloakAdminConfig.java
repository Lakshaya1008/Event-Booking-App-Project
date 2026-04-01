package com.event.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
@Getter
@Setter
public class KeycloakAdminConfig {

    private String serverUrl;
    /** The realm this backend authenticates INTO to obtain a service-account token. */
    private String realm;
    /** Client ID of the dedicated service-account client in Keycloak. */
    private String clientId;
    /** Client secret for the service-account client. */
    private String clientSecret;
    /** The realm where application users live (may differ from the auth realm). */
    private String targetRealm;

    /**
     * Creates a Keycloak Admin Client bean using CLIENT_CREDENTIALS grant.
     *
     * The client authenticates as a service account (no human credentials involved).
     * Token refresh is handled automatically by the Keycloak client library.
     */
    @Bean
    @ConditionalOnMissingBean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)
                .clientSecret(clientSecret)
                // username / password intentionally removed
                .build();
    }
}