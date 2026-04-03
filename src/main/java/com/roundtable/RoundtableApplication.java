package com.roundtable;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Roundtable — Modular Multi-Agent AI Financial Intelligence Platform
 *
 * Phase 1: Hardened Debate Core
 *
 * Run: mvn spring-boot:run
 * Open: http://localhost:8080
 */
@SpringBootApplication
@EnableConfigurationProperties
public class RoundtableApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoundtableApplication.class, args);
    }
}
