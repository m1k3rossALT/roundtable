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
 * Yahoo Finance data source adapter.
 *
 * Provides: PRICE, FUNDAMENTALS, EARNINGS
 * Free tier: Unlimited (unofficial public API — no key required)
 *
 * Note: This uses Yahoo Finance's unofficial query API which is
 * publicly available. It is subject to rate limiting under heavy use.
 * Caching is essential to avoid hitting limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YahooFinanceAdapter implements DataSourceAdapter {

    private static final String SOURCE = "yahoo_finance";
    private static final Set<DataType> SUPPORTED = Set.of(
            DataType.PRICE, DataType.FUNDAMENTALS, DataType.EARNINGS
    );

    private final RestTemplate       restTemplate;
    private final RoundtableConfig   config;
    private final ObjectMapper       objectMapper = new ObjectMapper();

    @Override
    public String getSourceName()          { return SOURCE; }

    @Override
    public Set<DataType> getSupportedTypes() { return SUPPORTED; }

    @Override
    public boolean isAvailable() {
        return config.getData().getYahooFinance().isConfigured();
    }

    @Override
    public DataResult fetch(DataRequest request) {
        if (request.getTicker() == null || request.getTicker().isBlank()) {
            return DataResult.failure(SOURCE, request.getDataType(),
                    null, "Yahoo Finance requires a ticker symbol");
        }

        String ticker = request.getTicker().toUpperCase().trim();
        log.debug("[YAHOO] Fetching {} for {}", request.getDataType(), ticker);

        try {
            return switch (request.getDataType()) {
                case PRICE        -> fetchPrice(ticker);
                case FUNDAMENTALS -> fetchFundamentals(ticker);
                case EARNINGS     -> fetchEarnings(ticker);
                default -> DataResult.failure(SOURCE, request.getDataType(),
                        ticker, "Unsupported data type: " + request.getDataType());
            };
        } catch (Exception e) {
            log.warn("[YAHOO] Fetch failed for {} {}: {}", ticker,
                    request.getDataType(), e.getMessage());
            return DataResult.failure(SOURCE, request.getDataType(),
                    ticker, e.getMessage());
        }
    }

    // ─── Price ───────────────────────────────────────────────────────────────

    private DataResult fetchPrice(String ticker) {
        String url = config.getData().getYahooFinance().getBaseUrl()
                + "/quote?symbols=" + ticker;
        String raw = restTemplate.getForObject(url, String.class);

        JsonNode root  = parseJson(raw);
        JsonNode quote = root.path("quoteResponse").path("result").path(0);

        if (quote.isMissingNode()) {
            return DataResult.failure(SOURCE, DataType.PRICE, ticker,
                    "No price data returned for " + ticker);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("symbol",             safeText(quote, "symbol"));
        data.put("shortName",          safeText(quote, "shortName"));
        data.put("currentPrice",       safeDouble(quote, "regularMarketPrice"));
        data.put("previousClose",      safeDouble(quote, "regularMarketPreviousClose"));
        data.put("dayChange",          safeDouble(quote, "regularMarketChange"));
        data.put("dayChangePct",       safeDouble(quote, "regularMarketChangePercent"));
        data.put("volume",             safeLong(quote,   "regularMarketVolume"));
        data.put("avgVolume",          safeLong(quote,   "averageDailyVolume3Month"));
        data.put("marketCap",          safeLong(quote,   "marketCap"));
        data.put("52WeekHigh",         safeDouble(quote, "fiftyTwoWeekHigh"));
        data.put("52WeekLow",          safeDouble(quote, "fiftyTwoWeekLow"));
        data.put("exchange",           safeText(quote,   "fullExchangeName"));
        data.put("currency",           safeText(quote,   "currency"));
        data.put("asOf",               Instant.now().toString());

        log.trace("[YAHOO] Price data for {}: {}", ticker, data);

        return DataResult.builder()
                .source(SOURCE).dataType(DataType.PRICE).ticker(ticker)
                .label("Price data for " + ticker)
                .data(data).fetchedAt(Instant.now())
                .fromCache(false).success(true)
                .build();
    }

    // ─── Fundamentals ────────────────────────────────────────────────────────

    private DataResult fetchFundamentals(String ticker) {
        String url = config.getData().getYahooFinance().getBaseUrl()
                + "/quoteSummary/" + ticker
                + "?modules=summaryDetail,defaultKeyStatistics,financialData";
        String raw = restTemplate.getForObject(url, String.class);

        JsonNode root     = parseJson(raw);
        JsonNode result   = root.path("quoteSummary").path("result").path(0);
        JsonNode summary  = result.path("summaryDetail");
        JsonNode keyStats = result.path("defaultKeyStatistics");
        JsonNode finData  = result.path("financialData");

        Map<String, Object> data = new LinkedHashMap<>();
        // Valuation
        data.put("trailingPE",         safeDouble(summary,  "trailingPE"));
        data.put("forwardPE",          safeDouble(summary,  "forwardPE"));
        data.put("priceToBook",        safeDouble(keyStats, "priceToBook"));
        data.put("pegRatio",           safeDouble(keyStats, "pegRatio"));
        data.put("enterpriseToEbitda", safeDouble(keyStats, "enterpriseToEbitda"));
        // Financial health
        data.put("totalDebt",          safeLong(finData,    "totalDebt"));
        data.put("totalCash",          safeLong(finData,    "totalCash"));
        data.put("debtToEquity",       safeDouble(finData,  "debtToEquity"));
        data.put("currentRatio",       safeDouble(finData,  "currentRatio"));
        // Profitability
        data.put("grossMargins",       safeDouble(finData,  "grossMargins"));
        data.put("operatingMargins",   safeDouble(finData,  "operatingMargins"));
        data.put("profitMargins",      safeDouble(finData,  "profitMargins"));
        data.put("returnOnEquity",     safeDouble(finData,  "returnOnEquity"));
        data.put("returnOnAssets",     safeDouble(finData,  "returnOnAssets"));
        // Growth
        data.put("revenueGrowth",      safeDouble(finData,  "revenueGrowth"));
        data.put("earningsGrowth",     safeDouble(finData,  "earningsGrowth"));
        // Dividends
        data.put("dividendYield",      safeDouble(summary,  "dividendYield"));
        data.put("payoutRatio",        safeDouble(summary,  "payoutRatio"));
        // Ownership
        data.put("institutionPct",     safeDouble(keyStats, "institutionsPercentHeld"));
        data.put("insiderPct",         safeDouble(keyStats, "heldPercentInsiders"));
        data.put("shortPct",           safeDouble(keyStats, "shortPercentOfFloat"));
        data.put("asOf",               Instant.now().toString());

        log.trace("[YAHOO] Fundamentals for {}: {}", ticker, data);

        return DataResult.builder()
                .source(SOURCE).dataType(DataType.FUNDAMENTALS).ticker(ticker)
                .label("Fundamental data for " + ticker)
                .data(data).fetchedAt(Instant.now())
                .fromCache(false).success(true)
                .build();
    }

    // ─── Earnings ────────────────────────────────────────────────────────────

    private DataResult fetchEarnings(String ticker) {
        String url = config.getData().getYahooFinance().getBaseUrl()
                + "/quoteSummary/" + ticker + "?modules=earnings,earningsTrend";
        String raw = restTemplate.getForObject(url, String.class);

        JsonNode root     = parseJson(raw);
        JsonNode result   = root.path("quoteSummary").path("result").path(0);
        JsonNode earnings = result.path("earnings");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ticker", ticker);

        // Last 4 quarters
        JsonNode quarterly = earnings.path("earningsChart").path("quarterly");
        if (!quarterly.isMissingNode() && quarterly.isArray()) {
            var quarters = new java.util.ArrayList<Map<String, Object>>();
            for (JsonNode q : quarterly) {
                Map<String, Object> quarter = new LinkedHashMap<>();
                quarter.put("date",   safeText(q, "date"));
                quarter.put("actual", safeDouble(q, "actual"));
                quarter.put("estimate", safeDouble(q, "estimate"));
                quarters.add(quarter);
            }
            data.put("recentQuarters", quarters);
        }

        data.put("asOf", Instant.now().toString());

        return DataResult.builder()
                .source(SOURCE).dataType(DataType.EARNINGS).ticker(ticker)
                .label("Earnings data for " + ticker)
                .data(data).fetchedAt(Instant.now())
                .fromCache(false).success(true)
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private JsonNode parseJson(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Yahoo Finance response: " + e.getMessage());
        }
    }

    private String safeText(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }

    private Double safeDouble(JsonNode node, String field) {
        JsonNode f = node.path(field);
        if (f.isMissingNode() || f.isNull()) return null;
        // Yahoo wraps values: { "raw": 123.4, "fmt": "123.4" }
        if (f.has("raw")) return f.path("raw").asDouble();
        return f.isDouble() || f.isInt() ? f.asDouble() : null;
    }

    private Long safeLong(JsonNode node, String field) {
        JsonNode f = node.path(field);
        if (f.isMissingNode() || f.isNull()) return null;
        if (f.has("raw")) return f.path("raw").asLong();
        return f.isNumber() ? f.asLong() : null;
    }
}
