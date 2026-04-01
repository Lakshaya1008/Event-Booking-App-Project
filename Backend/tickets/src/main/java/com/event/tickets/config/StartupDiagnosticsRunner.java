package com.event.tickets.config;

import com.event.tickets.services.KeycloakAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class StartupDiagnosticsRunner implements ApplicationRunner {

    private final KeycloakAdminService keycloakAdminService;
    private final JdbcTemplate jdbcTemplate;

    /** All tables this application requires to be present in the DB. */
    private static final List<String> REQUIRED_TABLES = List.of(
            "users",
            "events",
            "ticket_types",
            "tickets",
            "discounts",
            "qr_codes",
            "ticket_validations",
            "invite_codes",
            "audit_logs",
            "user_attending_events",
            "user_staffing_events"
    );

    @Override
    public void run(ApplicationArguments args) {
        checkKeycloakConnection();
        checkDatabaseSchema();
    }

    // ── Check 1: Keycloak ─────────────────────────────────────────────────────

    /**
     * Verifies Keycloak Admin API is reachable by fetching available roles.
     * Prints exactly one line regardless of outcome.
     *
     * Uses getAvailableRoles() — a read-only call that requires a valid admin token,
     * confirming both network reachability AND valid credentials simultaneously.
     */
    private void checkKeycloakConnection() {
        try {
            // getAvailableRoles() is a cheap read — just lists realm roles
            List<String> roles = keycloakAdminService.getAvailableRoles();
            log.info("Keycloak connected [roles available: {}]", roles.size());
        } catch (Exception e) {
            log.warn("Keycloak UNREACHABLE — {} (retries will be attempted per sync job)", e.getMessage());
        }
    }

    // ── Check 2: Database schema ──────────────────────────────────────────────

    /**
     * Verifies all required tables exist in the database.
     * Queries information_schema — works on PostgreSQL.
     * Prints exactly one line regardless of outcome.
     */
    private void checkDatabaseSchema() {
        try {
            List<String> existingTables = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables " +
                            "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                    String.class
            );

            List<String> missingTables = REQUIRED_TABLES.stream()
                    .filter(t -> !existingTables.contains(t))
                    .toList();

            if (missingTables.isEmpty()) {
                log.info("Database schema OK [{} tables present]", REQUIRED_TABLES.size());
            } else {
                log.warn("Database schema INCOMPLETE — missing tables: {}", missingTables);
            }
        } catch (Exception e) {
            log.warn("Database schema check FAILED — {}", e.getMessage());
        }
    }
}