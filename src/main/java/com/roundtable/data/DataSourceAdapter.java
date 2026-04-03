package com.roundtable.data;

import java.util.Set;

/**
 * Contract every data source must implement.
 *
 * Adding a new data source = implement this interface.
 * Register it as a Spring bean and DataService picks it up automatically.
 * No other code changes needed.
 *
 * MCP-ready: In Phase 2, MCP tools will delegate to these adapters.
 * The adapter is the canonical source of truth for each data type —
 * whether accessed via direct call or MCP tool.
 */
public interface DataSourceAdapter {

    /** Unique source identifier — used in cache keys and logs */
    String getSourceName();

    /** Which data types this source can provide */
    Set<DataType> getSupportedTypes();

    /** True if this source is reachable and configured */
    boolean isAvailable();

    /**
     * Fetch data for the given request.
     * Must never throw — return DataResult.failure() on any error.
     */
    DataResult fetch(DataRequest request);

    default boolean supports(DataType dataType) {
        return getSupportedTypes().contains(dataType);
    }
}
