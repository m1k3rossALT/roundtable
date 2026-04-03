package com.roundtable.data.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roundtable.config.RoundtableConfig;
import com.roundtable.data.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * FRED (Federal Reserve Economic Data) adapter.
 *
 * Provides: MACRO_INDICATOR
 * Free tier: Unlimited (official government API)
 * Key required: Free at https://fred.stlouisfed.org/docs/api/api_key.html
 *
 * Key FRED series used:
 *   DFF       — Federal Funds Rate
 *   CPIAUCSL  — CPI (inflation)
 *   GDP       — US GDP
 *   UNRATE    — Unemployment Rate
 *   T10Y2Y    — 10Y-2Y Treasury spread (recession indicator)
 *   M2SL      — M2 Money Supply
 *   DCOILWTICO — WTI Crude Oil Price
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FredApiAdapter implements DataSourceAdapter {

    private static final String SOURCE    = "fred";
    private static final Set<DataType> SUPPORTED = Set.of(DataType.MACRO_INDICATOR);

    // Default series to fetch when no specific series requested
    private static final Map<String, String> DEFAULT_SERIES = Map.of(
            "DFF",       "Federal Funds Rate (%)",
            "CPIAUCSL",  "Consumer Price Index (YoY%)",
            "GDP",       "US GDP (billions USD)",
            "UNRATE",    "Unemployment Rate (%)",
            "T10Y2Y",    "10Y-2Y Treasury Spread (basis pts)",
            "M2SL",      "M2 Money Supply (billions USD)"
    );

    private final RestTemplate     restTemplate;
    private final RoundtableConfig config;
    private final ObjectMapper     objectMapper = new ObjectMapper();

    @Override
    public String getSourceName()            { return SOURCE; }

    @Override
    public Set<DataType> getSupportedTypes() { return SUPPORTED; }

    @Override
    public boolean isAvailable() {
        return config.getData().getFred().isConfigured();
    }

    @Override
    public DataResult fetch(DataRequest request) {
        String apiKey = config.getData().getFred().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return DataResult.failure(SOURCE, DataType.MACRO_INDICATOR,
                    null, "FRED API key not configured");
        }

        log.debug("[FRED] Fetching macro indicators");

        // Check if a specific series was requested via params
        String seriesId = request.getParams() != null
                ? request.getParams().get("series_id")
                : null;

        try {
            Map<String, Object> data = seriesId != null
                    ? fetchSingleSeries(seriesId, apiKey)
                    : fetchDefaultSeries(apiKey);

            data.put("asOf", Instant.now().toString());
            log.trace("[FRED] Macro data: {}", data);

            return DataResult.builder()
                    .source(SOURCE)
                    .dataType(DataType.MACRO_INDICATOR)
                    .ticker(null)
                    .label("Macro indicators (FRED)")
                    .data(data)
                    .fetchedAt(Instant.now())
                    .fromCache(false)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.warn("[FRED] Fetch failed: {}", e.getMessage());
            return DataResult.failure(SOURCE, DataType.MACRO_INDICATOR,
                    null, "FRED fetch failed: " + e.getMessage());
        }
    }

    // ─── Internal fetch methods ───────────────────────────────────────────────

    private Map<String, Object> fetchDefaultSeries(String apiKey) {
        Map<String, Object> data = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : DEFAULT_SERIES.entrySet()) {
            try {
                String latestValue = fetchLatestValue(entry.getKey(), apiKey);
                data.put(entry.getValue(), latestValue);
            } catch (Exception e) {
                log.warn("[FRED] Failed to fetch series {}: {}", entry.getKey(), e.getMessage());
                data.put(entry.getValue(), "unavailable");
            }
        }

        return data;
    }

    private Map<String, Object> fetchSingleSeries(String seriesId, String apiKey) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series_id", seriesId);
        data.put("latest_value", fetchLatestValue(seriesId, apiKey));
        return data;
    }

    private String fetchLatestValue(String seriesId, String apiKey) {
        String url = String.format(
                "%s/series/observations?series_id=%s&api_key=%s&file_type=json&limit=1&sort_order=desc",
                config.getData().getFred().getBaseUrl(), seriesId, apiKey);

        String raw = restTemplate.getForObject(url, String.class);

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode obs  = root.path("observations").path(0);
            String date   = obs.path("date").asText();
            String value  = obs.path("value").asText();
            return value + " (as of " + date + ")";
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FRED response for " + seriesId);
        }
    }
}
