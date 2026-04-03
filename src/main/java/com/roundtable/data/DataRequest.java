package com.roundtable.data;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Input contract for a data fetch request.
 * Passed to DataSourceAdapter.fetch().
 */
@Data
@Builder
public class DataRequest {
    private String         ticker;     // e.g. "NVDA" — nullable for macro requests
    private String         topic;      // free-text topic for context
    private DataType       dataType;
    private Map<String, String> params; // source-specific params (e.g. FRED series ID)
}
