package com.roundtable.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * EventLogger implementation.
 *
 * Writes to two destinations:
 *   1. SLF4J / Logback → console + rolling file (all levels)
 *   2. app_event_log table → ERROR and WARN only (queryable, session-linked)
 *
 * INFO events are file-only to keep the database table lean.
 * TRACE is handled directly via SLF4J in individual classes — not routed here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventLoggerImpl implements EventLogger {

    private final JdbcTemplate   jdbcTemplate;
    private final ObjectMapper   objectMapper;

    // ─── INFO ─────────────────────────────────────────────────────────────────

    @Override
    public void info(String module, String component, String eventType,
                     UUID sessionId, String message, Map<String, Object> metadata) {

        log.info("[{}] [{}] [{}] {} | meta={}",
                module, component, eventType, message, formatMeta(metadata));
        // INFO not written to DB — file log only
    }

    // ─── WARN ─────────────────────────────────────────────────────────────────

    @Override
    public void warn(String module, String component, String eventType,
                     UUID sessionId, String message, Map<String, Object> metadata) {

        log.warn("[{}] [{}] [{}] {} | meta={}",
                module, component, eventType, message, formatMeta(metadata));
        writeToDb("WARN", module, component, eventType, sessionId, message, metadata, null);
    }

    // ─── ERROR ────────────────────────────────────────────────────────────────

    @Override
    public void error(String module, String component, String eventType,
                      UUID sessionId, String message, Throwable ex,
                      Map<String, Object> metadata) {

        if (ex != null) {
            log.error("[{}] [{}] [{}] {} | meta={}", module, component, eventType,
                    message, formatMeta(metadata), ex);
        } else {
            log.error("[{}] [{}] [{}] {} | meta={}",
                    module, component, eventType, message, formatMeta(metadata));
        }

        writeToDb("ERROR", module, component, eventType, sessionId, message, metadata,
                ex != null ? ex.getMessage() : null);
    }

    // ─── DB Write ─────────────────────────────────────────────────────────────

    private void writeToDb(String level, String module, String component,
                           String eventType, UUID sessionId, String message,
                           Map<String, Object> metadata, String errorDetail) {
        try {
            String metaJson = metadata != null
                    ? objectMapper.writeValueAsString(metadata)
                    : null;

            // Include error detail in metadata if present
            if (errorDetail != null && metadata != null) {
                Map<String, Object> enriched = new java.util.HashMap<>(metadata);
                enriched.put("error", errorDetail);
                metaJson = objectMapper.writeValueAsString(enriched);
            }

            Integer durationMs = metadata != null
                    ? (Integer) metadata.get("duration_ms")
                    : null;

            jdbcTemplate.update(
                """
                INSERT INTO app_event_log
                  (level, module, component, event_type, session_id,
                   agent_id, message, metadata, duration_ms)
                VALUES (?, ?, ?, ?, ?::uuid, ?, ?, ?::jsonb, ?)
                """,
                level, module, component, eventType,
                sessionId != null ? sessionId.toString() : null,
                metadata != null ? (String) metadata.get("agent_id") : null,
                message, metaJson, durationMs
            );
        } catch (Exception e) {
            // Never let logging failure break the application
            log.error("Failed to write event to app_event_log: {}", e.getMessage());
        }
    }

    private String formatMeta(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return metadata.toString();
        }
    }
}