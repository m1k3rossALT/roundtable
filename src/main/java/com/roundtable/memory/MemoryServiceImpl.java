package com.roundtable.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roundtable.api.dto.SessionRequest;
import com.roundtable.logging.EventLogger;
import com.roundtable.memory.MemoryRecords.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * MemoryService implementation.
 *
 * UUID FIX: PostgreSQL UUID columns require explicit ::uuid cast when passing
 * UUID values as VARCHAR via JdbcTemplate. All UUID params use ::uuid cast.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryServiceImpl implements MemoryService {

    private final JdbcTemplate  jdbcTemplate;
    private final ObjectMapper  objectMapper;
    private final EventLogger   eventLogger;
    private final ContextLoader contextLoader;

    @Override
    @Transactional
    public SessionRecord createSession(SessionRequest request) {
        log.debug("[MEMORY] Creating session type={} topic={}", request.getType(), request.getTopic());

        UUID sessionId = UUID.randomUUID();

        jdbcTemplate.update(
            """
            INSERT INTO sessions
              (id, title, type, status, global_context,
               risk_tolerance, asset_class, topic)
            VALUES (?::uuid, ?, ?, 'active', ?, ?, ?, ?)
            """,
            sessionId.toString(),
            request.getTitle(),
            request.getType(),
            request.getGlobalContext(),
            request.getRiskTolerance(),
            request.getAssetClass(),
            request.getTopic()
        );

        eventLogger.info("MEMORY", "MemoryServiceImpl", "SESSION_START",
                sessionId, "Session created",
                Map.of("type", request.getType(), "topic", request.getTopic()));

        return SessionRecord.builder()
                .id(sessionId)
                .title(request.getTitle())
                .type(request.getType())
                .status("active")
                .globalContext(request.getGlobalContext())
                .riskTolerance(request.getRiskTolerance())
                .assetClass(request.getAssetClass())
                .topic(request.getTopic())
                .parentSessionIds(List.of())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .recentRounds(List.of())
                .build();
    }

    @Override
    public SessionRecord loadSession(UUID sessionId) {
        log.debug("[MEMORY] Loading session {}", sessionId);

        var rows = jdbcTemplate.queryForList(
            "SELECT * FROM sessions WHERE id = ?::uuid", sessionId.toString());

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        Map<String, Object> row    = rows.get(0);
        List<RoundRecord>   rounds = contextLoader.loadRecentRounds(sessionId);

        return SessionRecord.builder()
                .id(sessionId)
                .title((String) row.get("title"))
                .type((String) row.get("type"))
                .status((String) row.get("status"))
                .globalContext((String) row.get("global_context"))
                .riskTolerance((String) row.get("risk_tolerance"))
                .assetClass((String) row.get("asset_class"))
                .topic((String) row.get("topic"))
                .parentSessionIds(List.of())
                .recentRounds(rounds)
                .build();
    }

    @Override
    public List<SessionSummary> getActiveSessions() {
        return jdbcTemplate.query(
            """
            SELECT s.id, s.title, s.topic, s.type, s.status, s.last_active_at,
                   COUNT(dr.id) as round_count
            FROM sessions s
            LEFT JOIN debate_rounds dr ON dr.session_id = s.id
            WHERE s.status = 'active' AND s.type = 'ongoing'
            GROUP BY s.id, s.title, s.topic, s.type, s.status, s.last_active_at
            ORDER BY s.last_active_at DESC
            """,
            (rs, rowNum) -> SessionSummary.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .title(rs.getString("title"))
                    .topic(rs.getString("topic"))
                    .type(rs.getString("type"))
                    .status(rs.getString("status"))
                    .lastActiveAt(rs.getTimestamp("last_active_at").toInstant())
                    .roundCount(rs.getInt("round_count"))
                    .build()
        );
    }

    @Override
    @Transactional
    public void saveRound(UUID sessionId, int roundNumber,
                          List<AgentResponseRecord> responses) {

        log.debug("[MEMORY] Saving round {} for session {}", roundNumber, sessionId);

        UUID roundId = UUID.randomUUID();

        jdbcTemplate.update(
            "INSERT INTO debate_rounds (id, session_id, round_number) VALUES (?::uuid, ?::uuid, ?)",
            roundId.toString(), sessionId.toString(), roundNumber
        );

        for (AgentResponseRecord resp : responses) {
            try {
                Map<String, String> structured = Map.of(
                        "position",   nvl(resp.getPosition()),
                        "reasoning",  nvl(resp.getReasoning()),
                        "keyRisk",    nvl(resp.getKeyRisk()),
                        "confidence", nvl(resp.getConfidence())
                );
                String structuredJson = objectMapper.writeValueAsString(structured);

                // agent_id can be null if agent lookup failed — use NULL instead of bad cast
                String agentId = resp.getAgentId();

                jdbcTemplate.update(
                    """
                    INSERT INTO agent_responses
                      (id, round_id, agent_id, provider, model,
                       structured_output, raw_text, prompt_version,
                       response_time_ms, token_count, success, error_message)
                    VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID().toString(),
                    roundId.toString(),
                    agentId,
                    resp.getProvider(),
                    resp.getModel(),
                    structuredJson,
                    resp.getRawText(),
                    resp.getPromptVersion(),
                    resp.getResponseTimeMs(),
                    resp.getTokenCount(),
                    resp.isSuccess(),
                    resp.getErrorMessage()
                );
            } catch (Exception e) {
                log.error("[MEMORY] Failed to save response for agent {}: {}",
                        resp.getAgentName(), e.getMessage());
            }
        }

        jdbcTemplate.update(
            "UPDATE sessions SET last_active_at = NOW() WHERE id = ?::uuid",
            sessionId.toString()
        );

        eventLogger.info("MEMORY", "MemoryServiceImpl", "ROUND_SAVED",
                sessionId, "Round saved",
                Map.of("roundNumber", roundNumber, "responseCount", responses.size()));
    }

    @Override
    @Transactional
    public void saveSynthesis(UUID sessionId, SynthesisRecord synthesis) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO session_synthesis
                  (session_id, round_number, consensus, disputed,
                   strongest_case, key_risks, suggested_next)
                VALUES (?::uuid, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?)
                """,
                sessionId.toString(),
                synthesis.getRoundNumber(),
                objectMapper.writeValueAsString(synthesis.getConsensus()),
                objectMapper.writeValueAsString(synthesis.getDisputed()),
                synthesis.getStrongestCase(),
                objectMapper.writeValueAsString(synthesis.getKeyRisks()),
                synthesis.getSuggestedNext()
            );
            eventLogger.info("MEMORY", "MemoryServiceImpl", "SYNTHESIS_SAVED",
                    sessionId, "Synthesis saved", Map.of());
        } catch (Exception e) {
            eventLogger.error("MEMORY", "MemoryServiceImpl", "SYNTHESIS_SAVE_FAILED",
                    sessionId, "Failed to save synthesis", e, Map.of());
        }
    }

    @Override
    public CombinedContext prepareCombinedContext(List<UUID> sessionIds) {
        List<String> summaries = new ArrayList<>();
        for (UUID id : sessionIds) {
            summaries.add(buildSessionSummary(loadSession(id)));
        }
        return CombinedContext.builder()
                .sourceSessionIds(sessionIds)
                .sourceSummaries(summaries)
                .mergedContext(buildMergedContext(summaries))
                .build();
    }

    @Override
    @Transactional
    public SessionRecord confirmCombinedSession(CombinedContext combinedContext,
                                                 SessionRequest request) {
        SessionRecord session = createSession(request);
        String parentIds = combinedContext.getSourceSessionIds().stream()
                .map(UUID::toString).reduce((a, b) -> a + "," + b).orElse("");
        jdbcTemplate.update(
            "UPDATE sessions SET parent_session_ids = ?::uuid[], type = 'combined' WHERE id = ?::uuid",
            "{" + parentIds + "}", session.getId().toString()
        );
        return session;
    }

    @Override
    public void concludeSession(UUID sessionId) {
        jdbcTemplate.update(
            "UPDATE sessions SET status = 'concluded', concluded_at = NOW() WHERE id = ?::uuid",
            sessionId.toString()
        );
        eventLogger.info("MEMORY", "MemoryServiceImpl", "SESSION_CONCLUDED",
                sessionId, "Session concluded", Map.of());
    }

    private String buildSessionSummary(SessionRecord session) {
        StringBuilder sb = new StringBuilder();
        sb.append("Session: ").append(session.getTitle()).append("\n");
        sb.append("Topic: ").append(session.getTopic()).append("\n");
        if (!session.getRecentRounds().isEmpty()) {
            session.getRecentRounds().forEach(round ->
                round.getResponses().forEach(resp -> {
                    if (resp.getPosition() != null)
                        sb.append("  - ").append(resp.getAgentName())
                          .append(": ").append(resp.getPosition()).append("\n");
                })
            );
        }
        return sb.toString();
    }

    private String buildMergedContext(List<String> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("COMBINED CONTEXT FROM PRIOR SESSIONS\n═══════════════════\n\n");
        for (int i = 0; i < summaries.size(); i++)
            sb.append("── Session ").append(i + 1).append(" ──\n").append(summaries.get(i)).append("\n");
        return sb.toString();
    }

    private String nvl(String val) { return val != null ? val : ""; }
}