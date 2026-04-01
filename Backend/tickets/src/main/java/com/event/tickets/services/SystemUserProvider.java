package com.event.tickets.services;

import com.event.tickets.domain.entities.User;
import com.event.tickets.exceptions.SystemUserNotFoundException;
import com.event.tickets.repositories.UserRepository;
import com.event.tickets.util.SystemUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

@Service
@DependsOn("databaseInitializer")
@RequiredArgsConstructor
@Slf4j
public class SystemUserProvider {

    private final UserRepository userRepository;
    private User systemUser;

    @PostConstruct
    public void loadSystemUser() {
        systemUser = userRepository.findById(SystemUser.SYSTEM_USER_UUID)
                .orElseThrow(() -> new SystemUserNotFoundException(
                        "SYSTEM user not found. DatabaseInitializer may have failed."));
        log.info("SYSTEM user loaded: {}", systemUser.getId());
    }

    public User getSystemUser() {
        return systemUser;
    }
}