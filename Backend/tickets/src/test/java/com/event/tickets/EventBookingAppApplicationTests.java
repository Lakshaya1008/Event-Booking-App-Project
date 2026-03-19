package com.event.tickets;

import com.event.tickets.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class EventBookingAppApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the application context loads successfully
    }
}
