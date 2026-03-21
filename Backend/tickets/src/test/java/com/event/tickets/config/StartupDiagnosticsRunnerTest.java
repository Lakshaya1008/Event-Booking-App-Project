package com.event.tickets.config;

import com.event.tickets.services.KeycloakAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupDiagnosticsRunner")
class StartupDiagnosticsRunnerTest {

    @Mock private KeycloakAdminService keycloakAdminService;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private StartupDiagnosticsRunner runner;

    @Test
    @DisplayName("does not throw when Keycloak is reachable and schema is complete")
    void happyPath_nothingThrows() {
        when(keycloakAdminService.getAvailableRoles()).thenReturn(List.of("ADMIN","ORGANIZER","STAFF","ATTENDEE"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "users","events","ticket_types","tickets","discounts",
                        "qr_codes","ticket_validations","invite_codes","audit_logs",
                        "user_attending_events","user_staffing_events"
                ));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
        verify(keycloakAdminService).getAvailableRoles();
        verify(jdbcTemplate).queryForList(anyString(), eq(String.class));
    }

    @Test
    @DisplayName("does not throw when Keycloak is unreachable — logs warning only")
    void keycloakDown_doesNotThrow() {
        when(keycloakAdminService.getAvailableRoles())
                .thenThrow(new RuntimeException("connection refused"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "users","events","ticket_types","tickets","discounts",
                        "qr_codes","ticket_validations","invite_codes","audit_logs",
                        "user_attending_events","user_staffing_events"
                ));

        // Must not throw — Keycloak down is logged as a warning, not a startup failure
        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not throw when schema has missing tables — logs warning only")
    void missingTables_doesNotThrow() {
        when(keycloakAdminService.getAvailableRoles()).thenReturn(List.of("ADMIN"));
        // Simulate missing invite_codes and qr_codes tables
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("users","events","tickets","audit_logs"));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("does not throw when DB schema check itself fails")
    void schemaCheckException_doesNotThrow() {
        when(keycloakAdminService.getAvailableRoles()).thenReturn(List.of("ADMIN"));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    }
}