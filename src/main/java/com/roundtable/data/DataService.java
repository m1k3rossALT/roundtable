package com.roundtable.data;

import java.util.List;

/**
 * Data Module's public interface.
 * No other module calls DataSourceAdapter directly — only this interface.
 *
 * Handles:
 *   - Cache check before fetching
 *   - Source selection based on data type
 *   - Context assembly and formatting for prompt injection
 */
public interface DataService {

    /**
     * Fetch and assemble a data context for a given topic and ticker.
     * Results are cached per TTL rules in application.properties.
     *
     * @param ticker   Stock ticker — nullable for macro-only topics
     * @param topic    Free-text topic for context
     * @param required Which data types to fetch
     * @return Assembled DataContext ready for prompt injection
     */
    DataContext fetchContext(String ticker, String topic, List<DataType> required);
}
