package com.event.tickets.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupDiagnosticsRunner")
class StartupDiagnosticsRunnerTest {

    @Mock
    private Environment environment;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private StartupDiagnosticsRunner startupDiagnosticsRunner;

    @Test
    @DisplayName("runs without throwing and reads environment values")
    void runsWithoutThrowingAndReadsEnvironmentValues() {
        when(environment.getProperty("spring.application.name", "tickets")).thenReturn("tickets");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});

        assertDoesNotThrow(() -> startupDiagnosticsRunner.run(applicationArguments));

        verify(environment).getProperty("spring.application.name", "tickets");
        verify(environment).getActiveProfiles();
    }
}

