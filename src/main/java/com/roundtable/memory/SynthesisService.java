package com.roundtable.memory;

import com.roundtable.agent.AgentDefinition;
import com.roundtable.agent.AgentRegistry;
import com.roundtable.agent.provider.AIProviderService;
import com.roundtable.config.RoundtableConfig;
import com.roundtable.logging.EventLogger;
import com.roundtable.memory.MemoryRecords.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the Synthesiser agent after all debate agents complete a round.
 *
 * The Synthesiser (Gemini, locked) reads the full debate and produces:
 *   CONSENSUS      — what all agents agreed on
 *   DISPUTED       — genuine disagreements with reasons
 *   STRONGEST_CASE — best-argued position with agent name
 *   KEY_RISKS      — union of all risks raised
 *   SUGGESTED_NEXT — one concrete action or follow-up question
 *
 * It never participates in the debate itself. Neutral only.
 * Lives in the memory package because synthesis is a persistence concern —
 * it runs after debate completion and its output is saved to session_synthesis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SynthesisService {

    private final AgentRegistry    agentRegistry;
    private final RoundtableConfig config;
    private final EventLogger      eventLogger;

    public SynthesisRecord synthesise(UUID sessionId,
                                       String topic,
                                       String globalContext,
                                       List<RoundRecord> rounds,
                                       Integer roundNumber) {

        log.info("[SYNTHESIS] Running synthesiser for session={} round={}",
                sessionId, roundNumber != null ? roundNumber : "final");

        AgentDefinition   synthesiser = agentRegistry.getSynthesiser();
        AIProviderService provider    = agentRegistry.getProvider(synthesiser.getProvider());

        String systemPrompt = buildSystemPrompt(synthesiser, globalContext);
        String userPrompt   = buildUserPrompt(topic, rounds);

        long   start = System.currentTimeMillis();
        String rawResponse;

        try {
            rawResponse = provider.generate(
                    systemPrompt, userPrompt,
                    config.getProviders().getGemini().getSynthesiserModel(),
                    null
            );
        } catch (Exception e) {
            eventLogger.error("SYNTHESIS", "SynthesisService", "SYNTHESIS_FAILED",
                    sessionId, "Synthesiser call failed", e, Map.of());
            return buildFallbackSynthesis(sessionId, roundNumber);
        }

        long duration = System.currentTimeMillis() - start;
        log.debug("[SYNTHESIS] Completed in {}ms", duration);
        log.trace("[SYNTHESIS] Raw response: {}", rawResponse);

        eventLogger.info("SYNTHESIS", "SynthesisService", "SYNTHESIS_COMPLETE",
                sessionId, "Synthesis completed",
                Map.of("duration_ms",  (int) duration,
                       "roundNumber",  roundNumber != null ? roundNumber : "final"));

        return parseSynthesis(sessionId, roundNumber, rawResponse);
    }

    // ─── Prompt builders ─────────────────────────────────────────────────────

    private String buildSystemPrompt(AgentDefinition synthesiser, String globalContext) {
        return globalContext + "\n\n"
             + "Your role: " + synthesiser.getPersona() + "\n\n"
             + "You must respond using EXACTLY this format:\n"
             + "CONSENSUS: [bullet points of what agents agreed on]\n"
             + "DISPUTED: [bullet points of genuine disagreements and why]\n"
             + "STRONGEST_CASE: [the best-argued position — name the agent]\n"
             + "KEY_RISKS: [bullet points of all risks raised across all agents]\n"
             + "SUGGESTED_NEXT: [one concrete action or follow-up question]\n";
    }

    private String buildUserPrompt(String topic, List<RoundRecord> rounds) {
        StringBuilder sb = new StringBuilder();
        sb.append("DEBATE TOPIC: ").append(topic).append("\n\n");
        sb.append("DEBATE TRANSCRIPT:\n");
        sb.append("═══════════════════════════════════════\n\n");

        for (RoundRecord round : rounds) {
            sb.append("── Round ").append(round.getRoundNumber()).append(" ──\n\n");
            for (AgentResponseRecord resp : round.getResponses()) {
                if (!resp.isSuccess()) continue;
                sb.append(resp.getAgentName()).append(":\n");
                if (resp.getPosition() != null)
                    sb.append("  POSITION: ").append(resp.getPosition()).append("\n");
                if (resp.getReasoning() != null)
                    sb.append("  REASONING: ").append(resp.getReasoning()).append("\n");
                if (resp.getKeyRisk() != null)
                    sb.append("  KEY RISK: ").append(resp.getKeyRisk()).append("\n");
                if (resp.getConfidence() != null)
                    sb.append("  CONFIDENCE: ").append(resp.getConfidence()).append("\n");
                sb.append("\n");
            }
        }

        sb.append("═══════════════════════════════════════\n\n");
        sb.append("Synthesise the debate above. Follow the required format exactly.");
        return sb.toString();
    }

    // ─── Parsing ─────────────────────────────────────────────────────────────

    private SynthesisRecord parseSynthesis(UUID sessionId, Integer roundNumber, String raw) {
        return SynthesisRecord.builder()
                .sessionId(sessionId)
                .roundNumber(roundNumber)
                .consensus(extractBullets("CONSENSUS", raw))
                .disputed(extractBullets("DISPUTED", raw))
                .strongestCase(extractField("STRONGEST_CASE", raw))
                .keyRisks(extractBullets("KEY_RISKS", raw))
                .suggestedNext(extractField("SUGGESTED_NEXT", raw))
                .createdAt(Instant.now())
                .build();
    }

    private List<String> extractBullets(String field, String raw) {
        String block = extractField(field, raw);
        if (block == null || block.isBlank()) return List.of();
        return Arrays.stream(block.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.startsWith("-") || s.startsWith("•")
                        ? s.substring(1).trim() : s)
                .toList();
    }

    private String extractField(String field, String raw) {
        Pattern p = Pattern.compile(
                field + ":\\s*(.+?)(?=CONSENSUS:|DISPUTED:|STRONGEST_CASE:|KEY_RISKS:|SUGGESTED_NEXT:|$)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1).trim() : null;
    }

    private SynthesisRecord buildFallbackSynthesis(UUID sessionId, Integer roundNumber) {
        return SynthesisRecord.builder()
                .sessionId(sessionId)
                .roundNumber(roundNumber)
                .consensus(List.of("Synthesis unavailable — provider error"))
                .disputed(List.of())
                .strongestCase("Could not determine — synthesiser failed")
                .keyRisks(List.of())
                .suggestedNext("Review debate transcript manually")
                .createdAt(Instant.now())
                .build();
    }
}