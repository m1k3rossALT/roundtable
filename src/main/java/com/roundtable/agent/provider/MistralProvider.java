package com.roundtable.agent.provider;

import com.roundtable.config.RoundtableConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Mistral AI provider.
 * Uses OpenAI-compatible chat completions format.
 * Models: mistral-small-latest, mistral-medium, open-mistral-7b
 */
@Service
public class MistralProvider extends OpenAICompatibleProvider {

    public MistralProvider(RestTemplate restTemplate, RoundtableConfig config) {
        super(restTemplate, config);
    }

    @Override
    public String getProviderName()  { return "mistral"; }

    @Override
    public String getDisplayName()   { return "Mistral AI"; }

    @Override
    public boolean isConfigured() {
        return config.getProviders().getMistral().isConfigured();
    }

    @Override
    protected RoundtableConfig.ProviderConfig getProviderConfig() {
        return config.getProviders().getMistral();
    }
}
