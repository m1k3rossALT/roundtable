package com.roundtable.data.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roundtable.data.DataResult;
import com.roundtable.data.DataType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Manages the data_cache table.
 *
 * TTLs (in minutes) by data type:
 *   PRICE           → 15 min
 *   FUNDAMENTALS    → 24 hr  (1440 min)
 *   EARNINGS        → 24 hr
 *   MACRO_INDICATOR → 6 hr   (360 min)
 *   TECHNICAL       → 60 min
 *   NEWS            → 30 min
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCacheService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final Map<DataType, Integer> TTL_MINUTES = Map.of(
            DataType.PRICE,           15,
            DataType.FUNDAMENTALS,    1440,
            DataType.EARNINGS,        1440,
            DataType.MACRO_INDICATOR, 360,
            DataType.TECHNICAL,       60,
            DataType.NEWS,            30
    );

    /**
     * Returns cached DataResult if present and not expired.
     */
    public Optional<DataResult> get(String source, DataType dataType, String ticker) {
        String cacheKey = buildKey(source, dataType, ticker);
        log.trace("[CACHE] Checking key={}", cacheKey);

        try {
            var rows = jdbcTemplate.queryForList(
                """
                SELECT data FROM data_cache
                WHERE cache_key = ? AND expires_at > NOW()
                """, cacheKey);

            if (rows.isEmpty()) {
                log.debug("[CACHE] Miss — key={}", cacheKey);
                return Optional.empty();
            }

            Object dataObj = rows.get(0).get("data");
            String json = dataObj instanceof String
                    ? (String) dataObj
                    : dataObj.toString();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            log.debug("[CACHE] Hit — key={}", cacheKey);
            return Optional.of(DataResult.builder()
                    .source(source)
                    .dataType(dataType)
                    .ticker(ticker)
                    .data(data)
                    .fetchedAt(Instant.now())
                    .fromCache(true)
                    .success(true)
                    .build());

        } catch (Exception e) {
            log.warn("[CACHE] Read error for key={}: {}", cacheKey, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores a DataResult in the cache with appropriate TTL.
     */
    public void put(DataResult result) {
        if (!result.isSuccess() || result.getData() == null) return;

        String cacheKey  = buildKey(result.getSource(), result.getDataType(), result.getTicker());
        int    ttlMins   = TTL_MINUTES.getOrDefault(result.getDataType(), 60);

        log.debug("[CACHE] Writing key={} ttl={}min", cacheKey, ttlMins);

        try {
            String dataJson = objectMapper.writeValueAsString(result.getData());

            jdbcTemplate.update(
                """
                INSERT INTO data_cache (source, data_type, cache_key, data, expires_at)
                VALUES (?, ?, ?, ?::jsonb, NOW() + (? || ' minutes')::interval)
                ON CONFLICT (cache_key) DO UPDATE
                  SET data = EXCLUDED.data,
                      fetched_at = NOW(),
                      expires_at = EXCLUDED.expires_at
                """,
                result.getSource(),
                result.getDataType().name(),
                cacheKey,
                dataJson,
                String.valueOf(ttlMins)
            );
        } catch (Exception e) {
            log.warn("[CACHE] Write error for key={}: {}", cacheKey, e.getMessage());
        }
    }

    /**
     * Evicts a specific cache entry. Used when data is known to be stale.
     */
    public void evict(String source, DataType dataType, String ticker) {
        String cacheKey = buildKey(source, dataType, ticker);
        jdbcTemplate.update("DELETE FROM data_cache WHERE cache_key = ?", cacheKey);
        log.debug("[CACHE] Evicted key={}", cacheKey);
    }

    private String buildKey(String source, DataType dataType, String ticker) {
        return String.format("%s:%s:%s",
                source,
                dataType.name().toLowerCase(),
                ticker != null ? ticker.toUpperCase() : "global");
    }
}
