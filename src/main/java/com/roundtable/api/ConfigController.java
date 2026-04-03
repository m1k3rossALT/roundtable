package com.roundtable.api;

import com.roundtable.agent.AgentRegistry;
import com.roundtable.agent.provider.AIProviderService;
import com.roundtable.config.RoundtableConfig;
import com.roundtable.data.DataSourceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes non-sensitive config to the frontend.
 *
 * GET /api/config/providers — which providers have keys configured
 * GET /api/config/agents    — active agents from the registry
 * GET /api/config/sources   — which data sources are available
 * GET /api/config/settings  — debate settings
 *
 * API keys are NEVER returned — only a boolean isConfigured flag.
 */
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final RoundtableConfig       config;
    private final List<AIProviderService> providers;
    private final List<DataSourceAdapter> dataAdapters;
    private final AgentRegistry          agentRegistry;

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        RoundtableConfig.ProvidersConfig p = config.getProviders();
        return ResponseEntity.ok(Map.of(
            "gemini",     providerInfo(p.getGemini(), "Gemini (Google AI Studio)"),
            "groq",       providerInfo(p.getGroq(),   "Groq"),
            "mistral",    providerInfo(p.getMistral(), "Mistral AI"),
            "openrouter", providerInfo(p.getOpenrouter(), "OpenRouter")
        ));
    }

    @GetMapping("/agents")
    public ResponseEntity<List<?>> getAgents() {
        return ResponseEntity.ok(agentRegistry.getAgentsForModule("debate"));
    }

    @GetMapping("/sources")
    public ResponseEntity<List<Map<String, Object>>> getSources() {
        List<Map<String, Object>> sources = dataAdapters.stream()
                .map(a -> Map.<String, Object>of(
                        "name",           a.getSourceName(),
                        "available",      a.isAvailable(),
                        "supportedTypes", a.getSupportedTypes()
                ))
                .toList();
        return ResponseEntity.ok(sources);
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        RoundtableConfig.DebateConfig d = config.getDebate();
        return ResponseEntity.ok(Map.of(
            "defaultRounds",        d.getDefaultRounds(),
            "maxTokens",            d.getMaxTokens(),
            "temperature",          d.getTemperature(),
            "maxRetries",           d.getMaxRetries(),
            "contextHistoryRounds", d.getContextHistoryRounds()
        ));
    }

    private Map<String, Object> providerInfo(RoundtableConfig.ProviderConfig cfg,
                                              String displayName) {
        return Map.of(
            "displayName",   displayName,
            "configured",    cfg.isConfigured(),
            "defaultModel",  cfg.getDefaultModel()
        );
    }
}
