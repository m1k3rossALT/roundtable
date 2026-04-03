package com.roundtable.data;

import com.roundtable.data.cache.DataCacheService;
import com.roundtable.logging.EventLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DataService implementation.
 *
 * Orchestrates:
 *   1. Cache check per (source, dataType, ticker)
 *   2. Live fetch if cache miss
 *   3. Cache write on successful fetch
 *   4. Context assembly — formats all results into prompt-ready text
 *
 * All registered DataSourceAdapter beans are injected automatically.
 * Adding a new adapter = register it as a @Service. Nothing else needed here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataServiceImpl implements DataService {

    private final List<DataSourceAdapter> adapters;   // all registered adapters
    private final DataCacheService        cache;
    private final EventLogger             eventLogger;

    @Override
    public DataContext fetchContext(String ticker, String topic, List<DataType> required) {
        long start = System.currentTimeMillis();
        log.info("[DATA] Fetching context — ticker={}, topic={}, types={}",
                ticker, topic, required);

        List<DataResult> results = new ArrayList<>();

        for (DataType dataType : required) {
            DataSourceAdapter adapter = selectAdapter(dataType);

            if (adapter == null) {
                log.warn("[DATA] No adapter available for type={}", dataType);
                continue;
            }

            // Check cache first
            Optional<DataResult> cached = cache.get(adapter.getSourceName(), dataType, ticker);
            if (cached.isPresent()) {
                results.add(cached.get());
                continue;
            }

            // Live fetch
            DataRequest request = DataRequest.builder()
                    .ticker(ticker)
                    .topic(topic)
                    .dataType(dataType)
                    .params(new HashMap<>())
                    .build();

            DataResult result = adapter.fetch(request);
            results.add(result);

            if (result.isSuccess()) {
                cache.put(result);
            } else {
                eventLogger.warn("DATA", "DataServiceImpl", "DATA_FETCH_FAILED",
                        null, "Data fetch failed for " + dataType + "/" + ticker,
                        Map.of("source", adapter.getSourceName(),
                               "dataType", dataType.name(),
                               "ticker",   ticker != null ? ticker : "global",
                               "error",    result.getErrorMessage() != null
                                           ? result.getErrorMessage() : "unknown"));
            }
        }

        long duration = System.currentTimeMillis() - start;
        eventLogger.info("DATA", "DataServiceImpl", "DATA_FETCH_COMPLETE",
                null, "Data context assembled",
                Map.of("ticker",      ticker != null ? ticker : "global",
                       "typesRequested", required.size(),
                       "typesSuccess",   results.stream().filter(DataResult::isSuccess).count(),
                       "duration_ms",    (int) duration));

        DataContext context = DataContext.builder()
                .results(results)
                .formattedContext(formatForPrompt(ticker, topic, results))
                .build();

        log.debug("[DATA] Context assembled — {} results in {}ms",
                results.size(), duration);
        return context;
    }

    // ─── Adapter selection ────────────────────────────────────────────────────

    /**
     * Selects the first available adapter that supports the requested data type.
     * Priority is determined by adapter declaration order in the Spring context.
     * To change priority, adjust the order of @Service bean registration.
     */
    private DataSourceAdapter selectAdapter(DataType dataType) {
        return adapters.stream()
                .filter(a -> a.supports(dataType) && a.isAvailable())
                .findFirst()
                .orElse(null);
    }

    // ─── Context formatting ───────────────────────────────────────────────────

    /**
     * Formats all data results into structured text for prompt injection.
     *
     * Agents are explicitly told what data is available so they can
     * reference specific figures without hallucinating.
     */
    private String formatForPrompt(String ticker, String topic, List<DataResult> results) {
        if (results.isEmpty() || results.stream().noneMatch(DataResult::isSuccess)) {
            return "DATA CONTEXT: No external data was available for this session. "
                 + "Base your analysis on general knowledge only and state this limitation clearly.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════\n");
        sb.append("DATA CONTEXT — USE ONLY THESE FIGURES\n");
        sb.append("You must not cite financial figures that do not appear below.\n");
        sb.append("If a figure you need is absent, state that it is unavailable.\n");
        sb.append("═══════════════════════════════════════\n\n");

        if (ticker != null) {
            sb.append("Subject: ").append(ticker.toUpperCase()).append("\n");
        }
        if (topic != null && !topic.isBlank()) {
            sb.append("Topic: ").append(topic).append("\n");
        }
        sb.append("\n");

        for (DataResult result : results) {
            if (!result.isSuccess() || result.getData() == null) continue;

            sb.append("── ").append(result.getLabel())
              .append(" [Source: ").append(result.getSource()).append("] ──\n");

            for (Map.Entry<String, Object> entry : result.getData().entrySet()) {
                if (entry.getValue() != null && !"asOf".equals(entry.getKey())) {
                    sb.append("  ").append(entry.getKey())
                      .append(": ").append(entry.getValue()).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("═══════════════════════════════════════\n");
        return sb.toString();
    }
}
