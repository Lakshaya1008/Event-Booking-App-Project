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

/**
 * Keycloak Admin API Configuration
 *
 * FIXES APPLIED:
 *
 * FIX-KC1 — Switched from PASSWORD grant to CLIENT_CREDENTIALS.
 *   BEFORE: grantType(OAuth2Constants.PASSWORD) with username + password.
 *   The Resource Owner Password Credentials grant is:
 *     - Deprecated in OAuth 2.1 and being removed from Keycloak in newer versions
 *     - Incompatible with admin accounts that have MFA enabled
 *     - A security risk: raw admin credentials sent on every token renewal
 *   AFTER: grantType(OAuth2Constants.CLIENT_CREDENTIALS) using a dedicated
 *   service account client in Keycloak with only the permissions this app needs.
 *   The username and password fields are removed from the config class entirely.
 *
 *   Migration steps for existing deployments:
 *   1. In Keycloak Admin UI → Clients → Create a new client (e.g. "event-ticket-backend")
 *   2. Set Access Type = confidential, Service Accounts Enabled = ON
 *   3. Under Service Account Roles, assign only the realm-management roles needed
 *      (manage-users, view-users, manage-realm)
 *   4. Copy the client secret from the Credentials tab
 *   5. Set keycloak.admin.client-id=event-ticket-backend
 *      and keycloak.admin.client-secret=<copied secret> in your env
 *   6. Remove KEYCLOAK_ADMIN_USERNAME / KEYCLOAK_ADMIN_PASSWORD from your env
 */
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
                // FIX-KC1: CLIENT_CREDENTIALS — correct grant for backend service accounts
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(clientId)
                .clientSecret(clientSecret)
                // username / password intentionally removed
                .build();
    }
}