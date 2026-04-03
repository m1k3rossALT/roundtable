package com.roundtable.agent.provider;

import com.roundtable.config.RoundtableConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * OpenRouter provider.
 * Uses OpenAI-compatible format with two required attribution headers.
 * Gives access to many free-tier models via a single API key.
 *
 * Free models: meta-llama/llama-3.3-70b-instruct:free,
 *              mistralai/mistral-7b-instruct:free, google/gemma-2-9b-it:free
 */
@Service
public class OpenRouterProvider extends OpenAICompatibleProvider {

    public OpenRouterProvider(RestTemplate restTemplate, RoundtableConfig config) {
        super(restTemplate, config);
    }

    @Override
    public String getProviderName()  { return "openrouter"; }

    @Override
    public String getDisplayName()   { return "OpenRouter"; }

    @Override
    public boolean isConfigured() {
        return config.getProviders().getOpenrouter().isConfigured();
    }

    @Override
    protected RoundtableConfig.ProviderConfig getProviderConfig() {
        return config.getProviders().getOpenrouter();
    }

    /**
     * OpenRouter requires these headers for rate limiting and attribution.
     * Without them, requests may be rejected.
     */
    @Override
    protected void addProviderHeaders(HttpHeaders headers) {
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title",      "Roundtable");
    }
}
