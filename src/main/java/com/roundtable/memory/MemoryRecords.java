package com.roundtable.memory;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain records for the Memory Module.
 * These are internal representations — not exposed directly via API.
 * API DTOs in com.roundtable.api.dto map from these.
 */
public final class MemoryRecords {

    private MemoryRecords() {}

    // ─── Session ─────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class SessionRecord {
        private UUID          id;
        private String        moduleId;
        private String        title;
        private String        type;               // standalone | ongoing | combined
        private String        status;             // active | paused | concluded
        private String        globalContext;
        private String        riskTolerance;
        private String        assetClass;
        private String        topic;
        private List<UUID>    parentSessionIds;   // for combined sessions
        private Instant       createdAt;
        private Instant       lastActiveAt;
        private List<RoundRecord> recentRounds;   // loaded rounds for context
    }

    // ─── Session Summary (lightweight, for startup list) ─────────────────────

    @Data
    @Builder
    public static class SessionSummary {
        private UUID    id;
        private String  title;
        private String  topic;
        private String  type;
        private String  status;
        private Instant lastActiveAt;
        private int     roundCount;
        private String  lastSynthesisSuggestedNext; // quick preview
    }

    // ─── Round ───────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class RoundRecord {
        private UUID                   id;
        private UUID                   sessionId;
        private int                    roundNumber;
        private Instant                createdAt;
        private List<AgentResponseRecord> responses;
    }

    // ─── Agent Response ──────────────────────────────────────────────────────

    @Data
    @Builder
    public static class AgentResponseRecord {
        private UUID    id;
        private UUID    roundId;
        private String  agentId;
        private String  agentName;
        private String  provider;
        private String  model;
        private String  position;         // parsed from structured output
        private String  reasoning;
        private String  keyRisk;
        private String  confidence;
        private String  rawText;
        private String  promptVersion;
        private int     responseTimeMs;
        private int     tokenCount;
        private boolean success;
        private String  errorMessage;
        private Instant createdAt;
    }

    // ─── Synthesis ───────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class SynthesisRecord {
        private UUID         sessionId;
        private Integer      roundNumber;  // null = final synthesis
        private List<String> consensus;
        private List<String> disputed;
        private String       strongestCase;
        private List<String> keyRisks;
        private String       suggestedNext;
        private Instant      createdAt;
    }

    // ─── Combined Context ────────────────────────────────────────────────────

    @Data
    @Builder
    public static class CombinedContext {
        private List<UUID>   sourceSessionIds;
        private List<String> sourceSummaries;    // one per source session
        private String       mergedContext;       // what user sees for confirmation
    }
}
