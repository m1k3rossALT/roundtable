package com.roundtable.module.debate;

import com.roundtable.config.RoundtableConfig;
import com.roundtable.data.DataType;
import com.roundtable.data.DataService;
import com.roundtable.data.DataContext;
import com.roundtable.logging.EventLogger;
import com.roundtable.memory.MemoryRecords.RoundRecord;
import com.roundtable.memory.MemoryRecords.SessionRecord;
import com.roundtable.memory.MemoryRecords.SynthesisRecord;
import com.roundtable.memory.MemoryService;
import com.roundtable.memory.SynthesisService;
import com.roundtable.module.AnalysisModule;
import com.roundtable.module.ModuleInput;
import com.roundtable.module.ModuleOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Debate Analysis Module — top-level workflow controller.
 *
 * Flow:
 *   1. Fetch data context (Yahoo Finance + FRED)
 *   2. Run each debate round sequentially (via DebateOrchestrator)
 *   3. Save each completed round to Memory Module
 *   4. Run Synthesiser after all rounds complete
 *   5. Save synthesis and return ModuleOutput
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DebateModule implements AnalysisModule {

    private final DataService        dataService;
    private final DebateOrchestrator orchestrator;
    private final SynthesisService   synthesisService;
    private final MemoryService      memoryService;
    private final RoundtableConfig   config;
    private final EventLogger        eventLogger;

    @Override public String getModuleId()    { return "debate"; }
    @Override public String getDisplayName() { return "Debate"; }

    @Override
    public ModuleOutput execute(ModuleInput input) {
        SessionRecord session = input.getSession();
        long          start   = System.currentTimeMillis();

        log.info("[DEBATE_MODULE] Starting session={} topic={}", session.getId(), session.getTopic());
        eventLogger.info("DEBATE", "DebateModule", "SESSION_START", session.getId(),
                "Debate started", Map.of("topic", session.getTopic(), "rounds", input.getTargetRounds()));

        try {
            // Fetch data context
            DataContext dataContext = dataService.fetchContext(
                    extractTicker(session.getTopic()), session.getTopic(),
                    List.of(DataType.PRICE, DataType.FUNDAMENTALS, DataType.MACRO_INDICATOR));

            if (!dataContext.hasData()) {
                eventLogger.warn("DEBATE", "DebateModule", "DATA_CONTEXT_EMPTY",
                        session.getId(), "No data fetched — agents will note limitation", Map.of());
            }

            // Run rounds
            int               target    = input.getTargetRounds() > 0 ? input.getTargetRounds()
                                          : config.getDebate().getDefaultRounds();
            List<RoundRecord> allRounds = new ArrayList<>(session.getRecentRounds());
            int               startAt   = allRounds.size() + 1;

            for (int rn = startAt; rn < startAt + target; rn++) {
                RoundRecord round = orchestrator.runRound(
                        session, dataContext.getFormattedContext(), allRounds, rn);
                memoryService.saveRound(session.getId(), rn, round.getResponses());
                allRounds.add(round);
            }

            // Synthesise
            SynthesisRecord synthesis = synthesisService.synthesise(
                    session.getId(), session.getTopic(),
                    session.getGlobalContext() != null ? session.getGlobalContext() : "",
                    allRounds, null);
            memoryService.saveSynthesis(session.getId(), synthesis);

            int duration = (int)(System.currentTimeMillis() - start);
            eventLogger.info("DEBATE", "DebateModule", "SESSION_COMPLETE", session.getId(),
                    "Debate completed", Map.of("rounds", allRounds.size(), "duration_ms", duration));

            return ModuleOutput.builder().sessionId(session.getId().toString())
                    .rounds(allRounds).synthesis(synthesis)
                    .dataSourcesUsed(dataContext.getSourcesSummary()).success(true).build();

        } catch (Exception e) {
            eventLogger.error("DEBATE", "DebateModule", "SESSION_FAILED", session.getId(),
                    "Debate failed", e, Map.of("duration_ms", (int)(System.currentTimeMillis() - start)));
            return ModuleOutput.failure("Debate failed: " + e.getMessage());
        }
    }

    private String extractTicker(String topic) {
        if (topic == null) return null;
        for (String word : topic.split("\\s+")) {
            String clean = word.replaceAll("[^A-Za-z]", "");
            if (clean.length() >= 1 && clean.length() <= 5 && clean.equals(clean.toUpperCase())) {
                return clean;
            }
        }
        return null;
    }
}