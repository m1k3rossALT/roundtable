package com.roundtable.logging;

import java.util.Map;
import java.util.UUID;

/**
 * Central logging interface used by all modules.
 *
 * Every significant event goes through here — never through direct
 * Logger calls in business logic. This ensures:
 *   - Consistent structured format across all modules
 *   - All ERROR/WARN events written to app_event_log (queryable)
 *   - Session context always attached when available
 *   - Single place to change logging behaviour
 *
 * TRACE logging is handled separately via SLF4J directly in provider
 * and data classes — it is too verbose for this structured interface.
 */
public interface EventLogger {

    void info(String module, String component, String eventType,
              UUID sessionId, String message, Map<String, Object> metadata);

    void warn(String module, String component, String eventType,
              UUID sessionId, String message, Map<String, Object> metadata);

    void error(String module, String component, String eventType,
               UUID sessionId, String message, Throwable ex,
               Map<String, Object> metadata);

    // ─── Convenience overloads (no session context) ───────────────────────

    default void info(String module, String component, String eventType,
                      String message, Map<String, Object> metadata) {
        info(module, component, eventType, null, message, metadata);
    }

    default void warn(String module, String component, String eventType,
                      String message, Map<String, Object> metadata) {
        warn(module, component, eventType, null, message, metadata);
    }

    default void error(String module, String component, String eventType,
                       String message, Throwable ex, Map<String, Object> metadata) {
        error(module, component, eventType, null, message, ex, metadata);
    }
}
