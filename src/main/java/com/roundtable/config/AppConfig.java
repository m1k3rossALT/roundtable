package com.roundtable.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * General Spring application configuration.
 * Declares shared infrastructure beans.
 */
@Configuration
public class AppConfig {

    /**
     * Shared RestTemplate for all outbound HTTP calls to AI providers
     * and data sources.
     *
     * Connect: 10s | Read: 60s
     * Read timeout is generous — LLM APIs can be slow on free tier.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}