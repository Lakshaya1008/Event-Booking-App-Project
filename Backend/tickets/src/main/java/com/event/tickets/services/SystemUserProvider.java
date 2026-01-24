package com.event.tickets.services;

import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.SystemUserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * System User Provider
 *
 * Provides the persisted SYSTEM user for audit logging.
 * The SYSTEM user is created at startup and reused for all unauthenticated audit events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemUserProvider {

  private final UserRepository userRepository;

  private User systemUser;

  private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  /**
   * Loads the SYSTEM user on startup.
   * Throws an exception if the SYSTEM user is missing (should never happen).
   */
  @PostConstruct
  public void loadSystemUser() {
    systemUser = userRepository.findById(SYSTEM_USER_ID)
        .orElseThrow(() -> new SystemUserNotFoundException("SYSTEM user not found. Database initialization may have failed."));
    log.info("SYSTEM user loaded: {}", systemUser.getId());
  }

  /**
   * Returns the persisted SYSTEM user.
   * This user is used for unauthenticated audit events.
   */
  public User getSystemUser() {
    return systemUser;
  }
}
