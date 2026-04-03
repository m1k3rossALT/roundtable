package com.roundtable.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.roundtable.config.RoundtableConfig;
import com.roundtable.exception.ProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for OpenAI-compatible providers.
 *
 * Groq, Mistral, and OpenRouter all implement the OpenAI Chat Completions format.
 * This base class handles all shared logic. Subclasses provide their config slice
 * and any provider-specific headers.
 *
 * Adding a new OpenAI-compatible provider = extend this class, provide config,
 * implement getProviderName(). Nothing else needed.
 */
@Slf4j
public abstract class OpenAICompatibleProvider implements AIProviderService {

    protected final RestTemplate     restTemplate;
    protected final RoundtableConfig config;
    protected final ObjectMapper     objectMapper = new ObjectMapper();

    protected OpenAICompatibleProvider(RestTemplate restTemplate,
                                       RoundtableConfig config) {
        this.restTemplate = restTemplate;
        this.config        = config;
    }

    @Override
    public String generate(String systemPrompt, String userPrompt,
                           String model, String apiKeyOverride) {

        RoundtableConfig.ProviderConfig cfg = getProviderConfig();
        String apiKey    = resolve(apiKeyOverride, cfg.getApiKey());
        String modelName = resolve(model,          cfg.getDefaultModel());
        String url       = cfg.getBaseUrl();

        if (apiKey == null || apiKey.isBlank()) {
            throw new ProviderException(getProviderName(),
                    "API key for '" + getProviderName() + "' not configured. "
                    + "Add it to application-local.properties.");
        }

        ObjectNode body = buildRequestBody(systemPrompt, userPrompt, modelName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        addProviderHeaders(headers);

        log.debug("[{}] Calling model={}", getProviderName().toUpperCase(), modelName);
        log.trace("[{}] System prompt: {}", getProviderName(), systemPrompt);
        log.trace("[{}] User prompt: {}", getProviderName(), userPrompt);

        try {
            long start = System.currentTimeMillis();
            String responseBody = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), String.class);
            long duration = System.currentTimeMillis() - start;

            log.debug("[{}] Response received in {}ms",
                    getProviderName().toUpperCase(), duration);
            log.trace("[{}] Raw response: {}", getProviderName(), responseBody);

            return parseResponse(responseBody);

        } catch (HttpClientErrorException e) {
            throw new ProviderException(getProviderName(),
                    buildErrorMessage(e.getStatusCode().value(),
                            e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new ProviderException(getProviderName(),
                    "Request failed: " + e.getMessage(), e);
        }
    }

    /** Returns this provider's config slice from RoundtableConfig */
    protected abstract RoundtableConfig.ProviderConfig getProviderConfig();

    /**
     * Override to add provider-specific headers.
     * Default: no additional headers.
     */
    protected void addProviderHeaders(HttpHeaders headers) {}

    // ─── Shared logic ────────────────────────────────────────────────────────

    private ObjectNode buildRequestBody(String systemPrompt, String userPrompt,
                                        String model) {
        ObjectNode body     = objectMapper.createObjectNode();
        body.put("model",      model);
        body.put("max_tokens", config.getDebate().getMaxTokens());
        body.put("temperature", config.getDebate().getTemperature());

        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role",    "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role",    "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        body.set("messages", messages);
        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices")
                       .path(0)
                       .path("message")
                       .path("content")
                       .asText();
        } catch (Exception e) {
            throw new ProviderException(getProviderName(),
                    "Failed to parse response: " + responseBody);
        }
    }

    private String buildErrorMessage(int status, String body) {
        return switch (status) {
            case 401 -> "Unauthorised — check your " + getProviderName() + " API key";
            case 404 -> "Model not found — check model name in config";
            case 429 -> "Rate limit exceeded — free tier quota hit, try again later";
            case 503 -> getProviderName() + " service unavailable — retry shortly";
            default  -> "HTTP " + status + ": " + body;
        };
    }

    protected String resolve(String override, String defaultVal) {
        return (override != null && !override.isBlank()) ? override : defaultVal;
    }
}
