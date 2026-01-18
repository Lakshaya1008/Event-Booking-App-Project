package com.event.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak Admin API Configuration
 *
 * Provides integration with Keycloak Admin REST API for role management.
 * This is the ONLY component that communicates with Keycloak Admin API.
 *
 * Security:
 * - Uses admin credentials from application.properties
 * - Client authenticated against master realm
 * - Used exclusively by backend services (no frontend access)
 */
@Configuration
@ConfigurationProperties(prefix = "keycloak.admin")
@Getter
@Setter
public class KeycloakAdminConfig {

  private String serverUrl;
  private String realm;
  private String clientId;
  private String clientSecret;
  private String username;
  private String password;

  /**
   * Creates Keycloak Admin Client bean.
   *
   * Authenticates using admin credentials to access Keycloak Admin API.
   * Backend is the sole authority for invoking Keycloak operations.
   *
   * @return Configured Keycloak admin client
   */
  @Bean
  @ConditionalOnMissingBean
  public Keycloak keycloakAdminClient() {
    return KeycloakBuilder.builder()
        .serverUrl(serverUrl)
        .realm("master") // Authenticate against master realm
        .username(username)
        .password(password)
        .clientId("admin-cli") // Default admin CLI client
        .build();
  }
}
