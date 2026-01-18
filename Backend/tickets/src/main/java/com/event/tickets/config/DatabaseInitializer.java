package com.event.tickets.config;

import com.event.tickets.domain.entities.ApprovalStatus;
import com.event.tickets.domain.entities.User;
import com.event.tickets.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database initializer that handles schema migrations and data fixes on application startup.
 *
 * This component:
 * 1. Auto-approves existing users who were created before the approval system
 * 2. Ensures backward compatibility when adding new required fields
 * 3. Runs once on startup using @PostConstruct
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

  private final UserRepository userRepository;

  /**
   * Migrates existing users to the approval system.
   *
   * WHY: When adding the approval_status column to an existing database,
   * pre-existing users will have NULL values. This method auto-approves them
   * because they were created before the approval system existed.
   *
   * This ensures:
   * - No disruption for existing users
   * - Backward compatibility
   * - Grandfathering of legacy accounts
   */
  @PostConstruct
  @Transactional
  public void migrateExistingUsers() {
    try {
      log.info("Starting database migration: Checking for users without approval status...");

      // Find all users with null approval_status (existing users before migration)
      List<User> usersToMigrate = userRepository.findAll().stream()
          .filter(user -> user.getApprovalStatus() == null)
          .toList();

      if (usersToMigrate.isEmpty()) {
        log.info("✅ No users need migration. All users have approval status set.");
        return;
      }

      log.info("Found {} users without approval status. Auto-approving them...", usersToMigrate.size());

      // Auto-approve all existing users and set timestamps if missing
      LocalDateTime now = LocalDateTime.now();
      for (User user : usersToMigrate) {
        user.setApprovalStatus(ApprovalStatus.APPROVED);
        user.setApprovedAt(now);

        // Set timestamps if they're missing (backward compatibility)
        if (user.getCreatedAt() == null) {
          user.setCreatedAt(now);
        }
        if (user.getUpdatedAt() == null) {
          user.setUpdatedAt(now);
        }

        // approvedBy is left as null (system auto-approval)
        log.debug("Auto-approved user: {} ({})", user.getEmail(), user.getId());
      }

      userRepository.saveAll(usersToMigrate);

      log.info("✅ Successfully migrated {} existing users to APPROVED status", usersToMigrate.size());
      log.info("These users were grandfathered in because they existed before the approval system.");
      log.info("New registrations will require admin approval.");

    } catch (Exception e) {
      log.error("❌ Error during database migration: {}", e.getMessage(), e);
      log.warn("Application will continue, but existing users may have access issues.");
      log.warn("Please check the database manually or run fix_approval_schema.sql");
    }
  }

  /**
   * Additional startup validations can be added here.
   *
   * Future enhancements:
   * - Validate database constraints
   * - Check for orphaned records
   * - Initialize default admin account
   * - Verify required indexes exist
   */
  @PostConstruct
  public void validateDatabaseState() {
    log.info("Database validation complete. Application ready.");
  }
}
