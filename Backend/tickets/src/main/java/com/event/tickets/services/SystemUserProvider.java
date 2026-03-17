package com.event.tickets.services;

import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.SystemUserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

/**
 * C-03 FIX: @DependsOn("databaseInitializer") added.
 *
 * Previously, SystemUserProvider and DatabaseInitializer both had @PostConstruct
 * methods. Spring does not guarantee bean initialization order within the same
 * context refresh — SystemUserProvider could run BEFORE DatabaseInitializer
 * created the SYSTEM user row, causing SystemUserNotFoundException on app startup.
 *
 * @DependsOn forces Spring to fully initialize the "databaseInitializer" bean
 * (including its @PostConstruct) before this bean's @PostConstruct runs.
 * This guarantees the SYSTEM user row exists when loadSystemUser() executes.
 */
@Service
@DependsOn("databaseInitializer")
@RequiredArgsConstructor
@Slf4j
public class SystemUserProvider {

    private final UserRepository userRepository;

    private User systemUser;

    private static final UUID SYSTEM_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    @PostConstruct
    public void loadSystemUser() {
        systemUser = userRepository.findById(SYSTEM_USER_ID)
                .orElseThrow(() -> new SystemUserNotFoundException(
                        "SYSTEM user not found. DatabaseInitializer may have failed."));
        log.info("SYSTEM user loaded: {}", systemUser.getId());
    }

    public User getSystemUser() {
        return systemUser;
    }
}