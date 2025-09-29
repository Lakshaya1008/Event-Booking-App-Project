package com.event.tickets;

import com.event.tickets.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSecurityConfig.class) // 👈 add this
class EventBookingAppApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the application context loads successfully
    }
}
