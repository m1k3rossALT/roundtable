package com.roundtable.memory;

import com.roundtable.config.RoundtableConfig;
import com.roundtable.memory.MemoryRecords.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Loads recent debate rounds for session context injection.
 *
 * When an agent needs to know what was said before, this service
 * loads the last N rounds (configured via roundtable.debate.context-history-rounds)
 * and formats them for prompt injection.
 *
 * Keeps context window usage controlled — we never dump the full
 * session history into every prompt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextLoader {

    private final JdbcTemplate     jdbcTemplate;
    private final RoundtableConfig config;

    /**
     * Loads the most recent N rounds for a session.
     * N is controlled by roundtable.debate.context-history-rounds (default 3).
     */
    public List<RoundRecord> loadRecentRounds(UUID sessionId) {
        int limit = config.getDebate().getContextHistoryRounds();
        log.debug("[CONTEXT] Loading last {} rounds for session {}", limit, sessionId);

        List<Map<String, Object>> roundRows = jdbcTemplate.queryForList(
            """
            SELECT id, round_number, created_at
            FROM debate_rounds
            WHERE session_id = ?::uuid
            ORDER BY round_number DESC
            LIMIT ?
            """,
            sessionId.toString(), limit
        );

        if (roundRows.isEmpty()) return List.of();

        // Reverse so rounds are in chronological order
        Collections.reverse(roundRows);

        List<RoundRecord> rounds = new ArrayList<>();
        for (Map<String, Object> row : roundRows) {
            UUID roundId     = (UUID) row.get("id");
            int  roundNumber = (int) row.get("round_number");

            List<AgentResponseRecord> responses = loadResponsesForRound(roundId);

            rounds.add(RoundRecord.builder()
                    .id(roundId)
                    .sessionId(sessionId)
                    .roundNumber(roundNumber)
                    .responses(responses)
                    .build());
        }

        return rounds;
    }

    /**
     * Formats loaded rounds into structured text for agent prompt injection.
     * Agents receive this as part of their user prompt context.
     */
    public String formatRoundsForPrompt(List<RoundRecord> rounds) {
        if (rounds == null || rounds.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("─── PRIOR CONVERSATION ───\n\n");

        for (RoundRecord round : rounds) {
            sb.append("Round ").append(round.getRoundNumber()).append(":\n");
            for (AgentResponseRecord resp : round.getResponses()) {
                if (!resp.isSuccess()) continue;
                sb.append(resp.getAgentName()).append(":\n");
                if (resp.getPosition() != null && !resp.getPosition().isBlank()) {
                    sb.append("  POSITION: ").append(resp.getPosition()).append("\n");
                }
                if (resp.getReasoning() != null && !resp.getReasoning().isBlank()) {
                    sb.append("  REASONING: ").append(resp.getReasoning()).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("─── END OF PRIOR CONVERSATION ───\n\n");
        return sb.toString();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private List<AgentResponseRecord> loadResponsesForRound(UUID roundId) {
        return jdbcTemplate.query(
            """
            SELECT ar.id, ar.agent_id, ar.provider, ar.model,
                   ar.structured_output, ar.raw_text, ar.prompt_version,
                   ar.response_time_ms, ar.token_count, ar.success, ar.error_message,
                   ar.created_at,
                   a.name as agent_name
            FROM agent_responses ar
            LEFT JOIN agents a ON a.id::text = ar.agent_id
            WHERE ar.round_id = ?::uuid
            ORDER BY ar.created_at
            """,
            (rs, rowNum) -> {
                // Parse structured_output JSON if available
                String pos = null, reasoning = null, keyRisk = null, confidence = null;
                String structuredJson = rs.getString("structured_output");
                if (structuredJson != null) {
                    try {
                        var node = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(structuredJson);
                        pos        = node.path("position").asText(null);
                        reasoning  = node.path("reasoning").asText(null);
                        keyRisk    = node.path("keyRisk").asText(null);
                        confidence = node.path("confidence").asText(null);
                    } catch (Exception ignored) {}
                }

                return AgentResponseRecord.builder()
                        .id(UUID.fromString(rs.getString("id")))
                        .roundId(roundId)
                        .agentId(rs.getString("agent_id"))
                        .agentName(rs.getString("agent_name"))
                        .provider(rs.getString("provider"))
                        .model(rs.getString("model"))
                        .position(pos)
                        .reasoning(reasoning)
                        .keyRisk(keyRisk)
                        .confidence(confidence)
                        .rawText(rs.getString("raw_text"))
                        .promptVersion(rs.getString("prompt_version"))
                        .responseTimeMs(rs.getInt("response_time_ms"))
                        .tokenCount(rs.getInt("token_count"))
                        .success(rs.getBoolean("success"))
                        .errorMessage(rs.getString("error_message"))
                        .build();
            },
            roundId.toString()
        );
    }
}
