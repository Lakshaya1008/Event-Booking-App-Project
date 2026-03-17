package com.event.tickets.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * L-13 FIX: Provides a Spring-managed RestTemplate bean.
 *
 * EmailServiceImpl previously created new RestTemplate() as a field initializer —
 * bypassing Spring auto-configuration and making it impossible to @MockBean in tests.
 *
 * This @Configuration class exposes a properly configured RestTemplate that:
 * - Has 10-second connect + read timeouts (prevents hanging email calls)
 * - Can be replaced with @MockBean in tests
 * - Is consistent with any RestTemplateCustomizer beans in the context
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}