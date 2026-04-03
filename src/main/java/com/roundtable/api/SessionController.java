package com.roundtable.api;

import com.roundtable.memory.MemoryRecords.CombinedContext;
import com.roundtable.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for session management.
 *
 * GET  /api/sessions              — list active ongoing sessions
 * GET  /api/sessions/{id}         — load a session
 * POST /api/sessions/combine      — prepare combined context for confirmation
 * POST /api/sessions/{id}/conclude — mark session as concluded
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final MemoryService memoryService;

    /** All active ONGOING sessions — shown on the startup screen */
    @GetMapping
    public ResponseEntity<List<?>> getActiveSessions() {
        return ResponseEntity.ok(memoryService.getActiveSessions());
    }

    /** Load a specific session (used when continuing) */
    @GetMapping("/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(memoryService.loadSession(sessionId));
    }

    /**
     * Prepare a combined context from multiple ongoing sessions.
     * Returns a merged summary for the user to confirm before the debate begins.
     *
     * Flow:
     *   1. Frontend calls this with session IDs
     *   2. Backend returns merged context text
     *   3. Frontend shows it to user: "Does this capture what you want? [Confirm / Edit]"
     *   4. User confirms → frontend calls POST /api/debate/start with the confirmed context
     */
    @PostMapping("/combine/prepare")
    public ResponseEntity<Map<String, Object>> prepareCombine(
            @RequestBody Map<String, List<UUID>> body) {

        List<UUID> sessionIds = body.get("sessionIds");
        if (sessionIds == null || sessionIds.size() < 2) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "At least 2 session IDs required for combining"));
        }

        log.info("[SESSION API] Preparing combined context for {} sessions", sessionIds.size());
        CombinedContext combined = memoryService.prepareCombinedContext(sessionIds);

        return ResponseEntity.ok(Map.of(
            "sourceSessionIds", combined.getSourceSessionIds(),
            "sourceSummaries",  combined.getSourceSummaries(),
            "mergedContext",    combined.getMergedContext(),
            "confirmationNote", "Please review the merged context above. "
                                + "Confirm to proceed or edit before starting the debate."
        ));
    }

    /** Mark a session as concluded */
    @PostMapping("/{sessionId}/conclude")
    public ResponseEntity<Map<String, String>> conclude(@PathVariable UUID sessionId) {
        memoryService.concludeSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "concluded",
                                        "sessionId", sessionId.toString()));
    }
}
