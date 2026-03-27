package com.example.artemisha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Artemis HA Demo — Live/Backup Replication
 *
 * Requires two external Artemis brokers already running:
 *   Live   → tcp://localhost:61616
 *   Backup → tcp://localhost:61617
 */
@SpringBootApplication
@EnableScheduling   // needed for @Scheduled producer
public class ArtemisHaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArtemisHaApplication.class, args);
    }
}
