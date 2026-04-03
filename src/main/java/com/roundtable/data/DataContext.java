package com.roundtable.data;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The assembled data context injected into every agent's prompt
 * before a debate begins.
 *
 * Built by DataService from one or more DataResult objects.
 * Formatted as structured text so agents can reference it clearly.
 */
@Data
@Builder
public class DataContext {

    private List<DataResult> results;
    private String           formattedContext; // pre-formatted for prompt injection

    /**
     * Returns true if at least one data result was successfully fetched.
     * Agents should be warned when context is empty.
     */
    public boolean hasData() {
        return results != null && results.stream().anyMatch(DataResult::isSuccess);
    }

    /**
     * Summary of which sources contributed data — shown in session output.
     */
    public List<String> getSourcesSummary() {
        if (results == null) return List.of();
        return results.stream()
                .filter(DataResult::isSuccess)
                .map(r -> r.getSource() + ":" + r.getDataType().name().toLowerCase())
                .toList();
    }
}
