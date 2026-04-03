package com.roundtable.memory;

import com.roundtable.api.dto.SessionRequest;
import com.roundtable.memory.MemoryRecords.AgentResponseRecord;
import com.roundtable.memory.MemoryRecords.CombinedContext;
import com.roundtable.memory.MemoryRecords.SessionRecord;
import com.roundtable.memory.MemoryRecords.SessionSummary;
import com.roundtable.memory.MemoryRecords.SynthesisRecord;

import java.util.List;
import java.util.UUID;

/**
 * Memory Module's public interface.
 * No other module touches the database directly — only through here.
 *
 * Owns: sessions, rounds, responses, synthesis, outcomes.
 */
public interface MemoryService {

    /** Create a new session (standalone or ongoing) */
    SessionRecord createSession(SessionRequest request);

    /** Load a full session including recent rounds */
    SessionRecord loadSession(UUID sessionId);

    /** All active ONGOING sessions — shown on app startup */
    List<SessionSummary> getActiveSessions();

    /** Save a completed round of agent responses */
    void saveRound(UUID sessionId, int roundNumber, List<AgentResponseRecord> responses);

    /** Save synthesis output */
    void saveSynthesis(UUID sessionId, SynthesisRecord synthesis);

    /** Begin session combining — returns a merged context for user confirmation */
    CombinedContext prepareCombinedContext(List<UUID> sessionIds);

    /** Confirm and create the combined session after user approves the context */
    SessionRecord confirmCombinedSession(CombinedContext combinedContext,
                                          SessionRequest request);

    /** Mark a session as concluded */
    void concludeSession(UUID sessionId);
}