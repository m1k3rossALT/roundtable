package com.roundtable.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for all application configuration.
 *
 * Binds all "roundtable.*" properties from application.properties
 * and the gitignored application-local.properties into typed Java objects.
 *
 * No @Value annotations scattered around the codebase.
 * Every config value comes through here.
 */
@Data
@Component
@ConfigurationProperties(prefix = "roundtable")
public class RoundtableConfig {

    private ProvidersConfig providers = new ProvidersConfig();
    private DataConfig      data      = new DataConfig();
    private DebateConfig    debate    = new DebateConfig();

    // ─── Providers ───────────────────────────────────────────────────────────

    @Data
    public static class ProvidersConfig {
        private ProviderConfig gemini     = new ProviderConfig();
        private ProviderConfig groq       = new ProviderConfig();
        private ProviderConfig mistral    = new ProviderConfig();
        private ProviderConfig openrouter = new ProviderConfig();
    }

    @Data
    public static class ProviderConfig {
        /** API key — loaded from application-local.properties. Never logged. */
        private String apiKey        = "";
        private String baseUrl       = "";
        private String defaultModel  = "";
        /** Gemini only — embedding API URL */
        private String embeddingUrl  = "";
        /** Gemini only — locked synthesiser model */
        private String synthesiserModel = "";

        /** True only if a key has been set. Used for UI status badges. */
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    // ─── Data Sources ────────────────────────────────────────────────────────

    @Data
    public static class DataConfig {
        private DataSourceConfig yahooFinance = new DataSourceConfig();
        private DataSourceConfig fred         = new DataSourceConfig();
    }

    @Data
    public static class DataSourceConfig {
        private String baseUrl = "";
        private String apiKey  = "";   // FRED requires key; Yahoo does not

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }

    // ─── Debate ──────────────────────────────────────────────────────────────

    @Data
    public static class DebateConfig {
        /** Max tokens per agent response */
        private int    maxTokens            = 800;
        /** Model temperature */
        private double temperature          = 0.7;
        /** Default number of debate rounds */
        private int    defaultRounds        = 2;
        /** Max retries when output format validation fails */
        private int    maxRetries           = 2;
        /** How many rounds of history to include in agent context */
        private int    contextHistoryRounds = 3;
    }
}
