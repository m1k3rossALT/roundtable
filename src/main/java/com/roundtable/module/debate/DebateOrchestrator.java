package com.roundtable.module.debate;

import com.roundtable.agent.AgentDefinition;
import com.roundtable.agent.AgentRegistry;
import com.roundtable.config.RoundtableConfig;
import com.roundtable.exception.ProviderException;
import com.roundtable.logging.EventLogger;
import com.roundtable.memory.ContextLoader;
import com.roundtable.memory.MemoryRecords.AgentResponseRecord;
import com.roundtable.memory.MemoryRecords.RoundRecord;
import com.roundtable.memory.MemoryRecords.SessionRecord;
import com.roundtable.module.debate.OutputValidator.ParsedOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebateOrchestrator {

    private final AgentRegistry    agentRegistry;
    private final OutputValidator  outputValidator;
    private final ContextLoader    contextLoader;
    private final RoundtableConfig config;
    private final EventLogger      eventLogger;

    public RoundRecord runRound(SessionRecord session, String dataContext,
                                List<RoundRecord> priorRounds, int roundNumber) {

        log.info("[DEBATE] Starting round {} for session={}", roundNumber, session.getId());
        List<AgentDefinition> agents = agentRegistry.getAgentsForModule("debate");

        if (agents.isEmpty()) throw new IllegalStateException(
                "No active agents found for debate module.");

        List<AgentResponseRecord> responses      = new ArrayList<>();
        List<AgentResponseRecord> soFarThisRound = new ArrayList<>();

        for (AgentDefinition agent : agents) {
            AgentResponseRecord response = callAgent(agent, session, dataContext,
                    priorRounds, soFarThisRound, roundNumber);
            responses.add(response);
            if (response.isSuccess()) soFarThisRound.add(response);
        }

        log.info("[DEBATE] Round {} complete — {} responses", roundNumber, responses.size());
        return RoundRecord.builder()
                .id(UUID.randomUUID()).sessionId(session.getId())
                .roundNumber(roundNumber).responses(responses).build();
    }

    private AgentResponseRecord callAgent(AgentDefinition agent, SessionRecord session,
                                           String dataContext, List<RoundRecord> priorRounds,
                                           List<AgentResponseRecord> soFarThisRound,
                                           int roundNumber) {
        log.debug("[DEBATE] Agent={} round={}", agent.getName(), roundNumber);

        String systemPrompt  = buildSystemPrompt(session.getGlobalContext(), agent);
        String userPrompt    = buildUserPrompt(session.getTopic(), dataContext,
                priorRounds, soFarThisRound, roundNumber);
        String promptVersion = agentRegistry.getActivePromptVersion(agent.getId());
        int    maxRetries    = config.getDebate().getMaxRetries();
        String lastError     = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                eventLogger.warn("DEBATE", "DebateOrchestrator", "AGENT_RETRY",
                        session.getId(), "Retrying agent call",
                        Map.of("agentName", agent.getName(), "attempt", attempt));
                userPrompt = outputValidator.buildRetryInstruction(userPrompt, lastError);
            }

            long start = System.currentTimeMillis();
            try {
                var    provider = agentRegistry.getProvider(agent.getProvider());
                String raw      = provider.generate(systemPrompt, userPrompt, agent.getModel(), null);
                int    duration = (int)(System.currentTimeMillis() - start);
                ParsedOutput parsed = outputValidator.parse(raw, agent.getName(), session.getId());

                if (parsed.isValid()) {
                    eventLogger.info("DEBATE", "DebateOrchestrator", "PROVIDER_CALL",
                            session.getId(), "Agent responded",
                            Map.of("agentName", agent.getName(), "provider", agent.getProvider(),
                                   "round", roundNumber, "duration_ms", duration));
                    return AgentResponseRecord.builder()
                            .agentId(agent.getId().toString()).agentName(agent.getName())
                            .provider(agent.getProvider()).model(agent.getModel())
                            .position(parsed.getPosition()).reasoning(parsed.getReasoning())
                            .keyRisk(parsed.getKeyRisk()).confidence(parsed.getConfidence())
                            .rawText(raw).promptVersion(promptVersion)
                            .responseTimeMs(duration).success(true).createdAt(Instant.now()).build();
                }
                lastError = parsed.getInvalidReason();

            } catch (ProviderException e) {
                lastError = e.getMessage();
                eventLogger.error("DEBATE", "DebateOrchestrator", "PROVIDER_CALL",
                        session.getId(), "Provider failed for " + agent.getName(), e,
                        Map.of("agentName", agent.getName(), "attempt", attempt));
            }
        }

        eventLogger.error("DEBATE", "DebateOrchestrator", "AGENT_FAILED",
                session.getId(), "Agent exhausted retries: " + agent.getName(), null,
                Map.of("agentName", agent.getName(), "lastError", lastError != null ? lastError : "unknown"));

        return AgentResponseRecord.builder()
                .agentId(agent.getId().toString()).agentName(agent.getName())
                .provider(agent.getProvider()).model(agent.getModel())
                .rawText("Agent failed after retries: " + lastError)
                .promptVersion(promptVersion).success(false).errorMessage(lastError)
                .createdAt(Instant.now()).build();
    }

    private String buildSystemPrompt(String globalContext, AgentDefinition agent) {
        return (globalContext != null ? globalContext.trim() : "")
             + "\n\nName: " + agent.getName()
             + "\nRole: " + agent.getPersona()
             + "\n\nYou MUST structure every response as:\n"
             + "POSITION: [one clear sentence]\n"
             + "REASONING: [2-3 data-grounded paragraphs]\n"
             + "KEY RISK: [the one thing that invalidates your position]\n"
             + "CONFIDENCE: [Low | Medium | High — one-line reason]\n\n"
             + "Cite only figures from the DATA CONTEXT. "
             + "Reference other agents by name when engaging their points.";
    }

    private String buildUserPrompt(String topic, String dataContext,
                                    List<RoundRecord> priorRounds,
                                    List<AgentResponseRecord> soFar, int roundNumber) {
        StringBuilder sb = new StringBuilder();
        if (dataContext != null && !dataContext.isBlank()) sb.append(dataContext).append("\n\n");
        sb.append("DEBATE TOPIC: ").append(topic).append("\n\n");
        if (!priorRounds.isEmpty()) sb.append(contextLoader.formatRoundsForPrompt(priorRounds));

        if (!soFar.isEmpty()) {
            sb.append("THIS ROUND SO FAR:\n\n");
            soFar.forEach(r -> sb.append(r.getAgentName()).append(":\n")
                    .append("  POSITION: ").append(r.getPosition()).append("\n\n"));
        }

        if (roundNumber == 1 && priorRounds.isEmpty() && soFar.isEmpty()) {
            sb.append("INSTRUCTION: Give your opening argument. State a clear position "
                    + "grounded in the data context. 2-3 paragraphs.");
        } else {
            sb.append("INSTRUCTION: React to what has been said. Reference agents by name. "
                    + "Do not repeat your opening. Push the debate forward. 2-3 paragraphs.");
        }
        return sb.toString();
    }
}
