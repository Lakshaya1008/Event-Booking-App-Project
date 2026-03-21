package com.event.tickets.config;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs lightweight diagnostics when the application starts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class StartupDiagnosticsRunner implements ApplicationRunner {

    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String appName = environment.getProperty("spring.application.name", "tickets");
        String[] activeProfiles = environment.getActiveProfiles();

        log.info("Startup diagnostics - app: {}, active profiles: {}",
                appName,
                Arrays.toString(activeProfiles));
    }
}

