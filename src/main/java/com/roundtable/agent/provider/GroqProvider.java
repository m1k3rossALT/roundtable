package com.roundtable.agent.provider;

import com.roundtable.config.RoundtableConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Groq provider.
 * Uses OpenAI-compatible chat completions format.
 * Models: llama-3.3-70b-versatile, mixtral-8x7b-32768, gemma2-9b-it
 */
@Service
public class GroqProvider extends OpenAICompatibleProvider {

    public GroqProvider(RestTemplate restTemplate, RoundtableConfig config) {
        super(restTemplate, config);
    }

    @Override
    public String getProviderName()  { return "groq"; }

    @Override
    public String getDisplayName()   { return "Groq"; }

    @Override
    public boolean isConfigured() {
        return config.getProviders().getGroq().isConfigured();
    }

    @Override
    protected RoundtableConfig.ProviderConfig getProviderConfig() {
        return config.getProviders().getGroq();
    }
}
