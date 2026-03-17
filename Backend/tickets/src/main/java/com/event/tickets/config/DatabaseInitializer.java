package com.event.tickets.config;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.KeycloakAdminService;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-03 FIX: Race condition eliminated.
 * Previously had 4 separate @PostConstruct methods. Jakarta EE spec permits
 * only ONE @PostConstruct per class — multiple methods produce undefined
 * execution order. On a fresh database, SystemUserProvider.loadSystemUser()
 * could run BEFORE createSystemUser(), causing immediate app crash.
 *
 * L-07 FIX: All 4 methods consolidated into one @PostConstruct initialize()
 * that calls them in strict dependency order:
 *   1. migrateExistingUsers    (no deps)
 *   2. createSystemUser        (no deps — must complete before SystemUserProvider)
 *   3. normalizeKeycloak       (needs system user row to exist for skip check)
 *   4. validateDatabaseState   (terminal log)
 *
 * H-11 FIX: normalizeKeycloak now calls activateUser() (1 atomic API call)
 * instead of setUserEnabled() + setEmailVerified() + clearRequiredActions()
 * (3 separate Keycloak round-trips per user).
 *
 * SystemUserProvider is annotated @DependsOn("databaseInitializer") so Spring
 * guarantees this bean fully initializes (including createSystemUser) before
 * SystemUserProvider attempts loadSystemUser().
 */
@Component("databaseInitializer")
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    /**
     * Single entry point — runs once after Spring context is ready.
     * Order matters: createSystemUser must complete before normalizeKeycloak
     * so the SYSTEM user skip-check works correctly.
     */
    @PostConstruct
    @Transactional
    public void initialize() {
        migrateExistingUsers();
        createSystemUser();
        normalizeKeycloakStateForApprovedUsers();
        validateDatabaseState();
    }

    // ── step 1 ────────────────────────────────────────────────────────────────

    /**
     * Auto-approves users created before the approval system existed (null status).
     */
    private void migrateExistingUsers() {
        try {
            List<User> usersToMigrate = userRepository.findAll().stream()
                    .filter(u -> u.getApprovalStatus() == null)
                    .toList();

            if (usersToMigrate.isEmpty()) {
                log.info("No users require approval-status migration.");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            for (User u : usersToMigrate) {
                u.setApprovalStatus(ApprovalStatus.APPROVED);
                u.setApprovedAt(now);
                if (u.getCreatedAt() == null) u.setCreatedAt(now);
                if (u.getUpdatedAt() == null) u.setUpdatedAt(now);
            }
            userRepository.saveAll(usersToMigrate);
            log.info("Migrated {} legacy users to APPROVED status.", usersToMigrate.size());
        } catch (Exception e) {
            log.error("User migration failed: {}", e.getMessage(), e);
        }
    }

    // ── step 2 ────────────────────────────────────────────────────────────────

    /**
     * Creates the SYSTEM user (00000000-…) if it does not exist.
     * Must run before SystemUserProvider.loadSystemUser() — guaranteed by
     * the @DependsOn("databaseInitializer") on SystemUserProvider.
     */
    private void createSystemUser() {
        try {
            if (!userRepository.existsById(SYSTEM_USER_ID)) {
                User systemUser = new User();
                systemUser.setId(SYSTEM_USER_ID);
                systemUser.setEmail("system@system.local");
                systemUser.setName("SYSTEM");
                systemUser.setApprovalStatus(ApprovalStatus.APPROVED);
                LocalDateTime now = LocalDateTime.now();
                systemUser.setCreatedAt(now);
                systemUser.setUpdatedAt(now);
                userRepository.save(systemUser);
                log.info("SYSTEM user created for audit logging.");
            } else {
                log.debug("SYSTEM user already exists.");
            }
        } catch (Exception e) {
            log.error("SYSTEM user creation failed: {}", e.getMessage(), e);
            throw new RuntimeException("SYSTEM user creation failed — application cannot start", e);
        }
    }

    // ── step 3 ────────────────────────────────────────────────────────────────

    /**
     * H-11 FIX: Calls activateUser() — one atomic Keycloak operation — instead
     * of three separate HTTP calls (setUserEnabled + setEmailVerified +
     * clearRequiredActions) that each open a round-trip to Keycloak.
     */
    private void normalizeKeycloakStateForApprovedUsers() {
        try {
            List<User> approvedUsers = userRepository.findAll().stream()
                    .filter(u -> u.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .filter(u -> !SYSTEM_USER_ID.equals(u.getId()))
                    .toList();

            if (approvedUsers.isEmpty()) {
                log.info("No approved users require Keycloak normalization.");
                return;
            }

            int normalized = 0;
            for (User u : approvedUsers) {
                try {
                    // H-11 FIX: single activateUser() call instead of 3 separate calls
                    keycloakAdminService.activateUser(u.getId());
                    normalized++;
                } catch (Exception e) {
                    log.warn("Keycloak normalization failed for user {}: {}", u.getEmail(), e.getMessage());
                }
            }
            log.info("Keycloak normalization complete: {} users activated.", normalized);
        } catch (Exception e) {
            log.error("Keycloak normalization error: {}", e.getMessage(), e);
            log.warn("Application will continue — some approved users may not be able to log in.");
        }
    }

    // ── step 4 ────────────────────────────────────────────────────────────────

    private void validateDatabaseState() {
        log.info("Database initialization complete. Application is ready.");
    }
}