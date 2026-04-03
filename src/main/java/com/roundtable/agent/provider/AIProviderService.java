package com.roundtable.agent.provider;

/**
 * Contract every AI provider must implement.
 *
 * Adding a new provider = implement this interface + one config block.
 * No other code changes required anywhere in the system.
 *
 * MCP-ready note: In Phase 2, this interface will be extended to support
 * tool/function calling so providers can invoke MCP tools mid-reasoning.
 */
public interface AIProviderService {

    /** Unique provider identifier — matches config key (gemini, groq, etc.) */
    String getProviderName();

    /**
     * Generate a response from the model.
     *
     * @param systemPrompt  Combined global context + agent persona
     * @param userPrompt    Debate history + round instruction
     * @param model         Model override — null uses provider default
     * @param apiKeyOverride Key override — null/blank uses configured default
     * @return The model's text response
     */
    String generate(String systemPrompt, String userPrompt,
                    String model, String apiKeyOverride);

    /** True if an API key is configured for this provider */
    boolean isConfigured();

    /** Display name for UI and logs */
    default String getDisplayName() {
        return getProviderName();
    }
}
