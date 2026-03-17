package com.event.tickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * C-02 FIX: @EnableScheduling added.
 * Without this, every @Scheduled method (InviteCodeExpiryScheduler) is
 * silently ignored — Spring registers the bean but never executes the jobs.
 */
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class EventBookingAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventBookingAppApplication.class, args);
    }
}