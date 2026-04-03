package com.roundtable.agent.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.roundtable.config.RoundtableConfig;
import com.roundtable.exception.ProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Google AI Studio (Gemini) provider.
 *
 * Gemini uses a different API format from OpenAI-compatible providers:
 *   - system_instruction field (not a system message in messages array)
 *   - contents array with role and parts structure
 *   - generationConfig block for tokens and temperature
 *   - API key as a URL query parameter
 *
 * Docs: https://ai.google.dev/api/generate-content
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiProvider implements AIProviderService {

    private static final String PROVIDER = "gemini";

    private final RestTemplate       restTemplate;
    private final RoundtableConfig   config;
    private final ObjectMapper       objectMapper = new ObjectMapper();

    @Override
    public String getProviderName()  { return PROVIDER; }

    @Override
    public String getDisplayName()   { return "Gemini (Google AI Studio)"; }

    @Override
    public boolean isConfigured() {
        return config.getProviders().getGemini().isConfigured();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt,
                           String model, String apiKeyOverride) {

        RoundtableConfig.ProviderConfig cfg = config.getProviders().getGemini();
        String apiKey    = resolve(apiKeyOverride, cfg.getApiKey());
        String modelName = resolve(model,         cfg.getDefaultModel());

        if (apiKey == null || apiKey.isBlank()) {
            throw new ProviderException(PROVIDER,
                    "Gemini API key not configured. Add it to application-local.properties.");
        }

        String url = String.format("%s/%s:generateContent?key=%s",
                cfg.getBaseUrl(), modelName, apiKey);

        ObjectNode body = buildRequestBody(systemPrompt, userPrompt);

        log.debug("[GEMINI] Calling model={}", modelName);
        log.trace("[GEMINI] System prompt: {}", systemPrompt);
        log.trace("[GEMINI] User prompt: {}", userPrompt);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            long start = System.currentTimeMillis();
            String responseBody = restTemplate.postForObject(
                    url, new HttpEntity<>(body, headers), String.class);
            long duration = System.currentTimeMillis() - start;

            log.debug("[GEMINI] Response received in {}ms", duration);
            log.trace("[GEMINI] Raw response: {}", responseBody);

            return parseResponse(responseBody);

        } catch (HttpClientErrorException e) {
            throw new ProviderException(PROVIDER,
                    buildErrorMessage(e.getStatusCode().value(), e.getResponseBodyAsString()), e);
        } catch (Exception e) {
            throw new ProviderException(PROVIDER, "Request failed: " + e.getMessage(), e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ObjectNode buildRequestBody(String systemPrompt, String userPrompt) {
        ObjectNode body = objectMapper.createObjectNode();

        // System instruction
        ObjectNode sysInstruction = objectMapper.createObjectNode();
        ArrayNode  sysParts       = objectMapper.createArrayNode();
        sysParts.add(objectMapper.createObjectNode().put("text", systemPrompt));
        sysInstruction.set("parts", sysParts);
        body.set("system_instruction", sysInstruction);

        // User content
        ArrayNode contents     = objectMapper.createArrayNode();
        ObjectNode contentNode = objectMapper.createObjectNode();
        ArrayNode  parts       = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", userPrompt));
        contentNode.put("role", "user");
        contentNode.set("parts", parts);
        contents.add(contentNode);
        body.set("contents", contents);

        // Generation config
        ObjectNode genConfig = objectMapper.createObjectNode();
        genConfig.put("maxOutputTokens", config.getDebate().getMaxTokens());
        genConfig.put("temperature",     config.getDebate().getTemperature());
        body.set("generationConfig", genConfig);

        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("candidates")
                       .path(0)
                       .path("content")
                       .path("parts")
                       .path(0)
                       .path("text")
                       .asText();
        } catch (Exception e) {
            throw new ProviderException(PROVIDER,
                    "Failed to parse response: " + responseBody);
        }
    }

    private String buildErrorMessage(int status, String body) {
        return switch (status) {
            case 400 -> "Bad request (check model name or request format): " + body;
            case 403 -> "Forbidden — check your Gemini API key";
            case 429 -> "Rate limit exceeded — free tier quota hit, try again later";
            case 503 -> "Gemini service unavailable — retry shortly";
            default  -> "HTTP " + status + ": " + body;
        };
    }

    private String resolve(String override, String defaultVal) {
        return (override != null && !override.isBlank()) ? override : defaultVal;
    }
}
