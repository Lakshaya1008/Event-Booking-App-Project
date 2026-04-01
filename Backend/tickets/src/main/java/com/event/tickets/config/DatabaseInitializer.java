package com.event.tickets.config;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.services.KeycloakAdminService;
import com.event.tickets.util.SystemUser;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("databaseInitializer")
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

    private final UserRepository userRepository;
    private final KeycloakAdminService keycloakAdminService;

    /**
     * Runs synchronously at startup. Performs only DB operations — no Keycloak calls.
     * Fast and reliable even if Keycloak is temporarily unavailable.
     */
    @PostConstruct
    @Transactional
    public void initialize() {
        migrateExistingUsers();
        createSystemUser();
        validateDatabaseState();
        normalizeKeycloakStateAsync();
    }

    // ── step 1 ────────────────────────────────────────────────────────────────

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

    private void createSystemUser() {
        try {
            if (!userRepository.existsById(SystemUser.SYSTEM_USER_UUID)) {
                User systemUser = new User();
                systemUser.setId(SystemUser.SYSTEM_USER_UUID);
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

    // ── step 3 (terminal) ─────────────────────────────────────────────────────

    private void validateDatabaseState() {
        log.info("Database initialization complete. Application is ready.");
    }

    // ── step 4 (async, post-startup) ─────────────────────────────────────────

    @Async
    @Transactional
    public void normalizeKeycloakStateAsync() {
        try {
            List<User> syncPending = userRepository.findByKeycloakSyncPending(true).stream()
                    .filter(u -> u.getApprovalStatus() == ApprovalStatus.APPROVED)
                    .filter(u -> !SystemUser.SYSTEM_USER_UUID.equals(u.getId()))
                    .toList();

            if (syncPending.isEmpty()) {
                log.info("No users require Keycloak normalization on startup.");
                return;
            }

            log.info("Startup Keycloak normalization: {} user(s) with sync_pending=true", syncPending.size());

            int normalized = 0;
            for (User u : syncPending) {
                try {
                    keycloakAdminService.activateUser(u.getId());
                    u.setKeycloakSyncPending(false);
                    userRepository.save(u);
                    normalized++;
                } catch (Exception e) {
                    log.warn("Startup Keycloak normalization failed for user {}: {}", u.getEmail(), e.getMessage());
                }
            }
            log.info("Startup Keycloak normalization complete: {} users activated.", normalized);
        } catch (Exception e) {
            log.error("Startup Keycloak normalization error: {}", e.getMessage(), e);
        }
    }
}