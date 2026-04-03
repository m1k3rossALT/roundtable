package com.roundtable.data;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Output contract from a DataSourceAdapter.
 * Normalised — callers never deal with source-specific formats.
 */
@Data
@Builder
public class DataResult {
    private String              source;       // which adapter produced this
    private DataType            dataType;
    private String              ticker;
    private String              label;        // human-readable description
    private Map<String, Object> data;         // normalised key-value pairs
    private Instant             fetchedAt;
    private boolean             fromCache;
    private boolean             success;
    private String              errorMessage; // null if success

    public static DataResult failure(String source, DataType dataType,
                                     String ticker, String errorMessage) {
        return DataResult.builder()
                .source(source)
                .dataType(dataType)
                .ticker(ticker)
                .success(false)
                .errorMessage(errorMessage)
                .fetchedAt(Instant.now())
                .build();
    }
}
