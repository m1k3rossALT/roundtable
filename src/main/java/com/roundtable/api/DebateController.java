package com.roundtable.api;

import com.roundtable.api.dto.SessionRequest;
import com.roundtable.data.DataContext;
import com.roundtable.data.DataService;
import com.roundtable.data.DataType;
import com.roundtable.logging.EventLogger;
import com.roundtable.memory.MemoryRecords.SessionRecord;
import com.roundtable.memory.MemoryService;
import com.roundtable.module.ModuleInput;
import com.roundtable.module.ModuleOutput;
import com.roundtable.module.debate.DebateModule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for debate operations.
 *
 * POST /api/debate/start     — create session and run all rounds
 * POST /api/debate/continue  — continue an existing session
 */
@Slf4j
@RestController
@RequestMapping("/api/debate")
@RequiredArgsConstructor
public class DebateController {

    private final DebateModule  debateModule;
    private final MemoryService memoryService;
    private final DataService   dataService;
    private final EventLogger   eventLogger;

    /**
     * Start a new debate session and run it to completion.
     *
     * 1. Creates the session in the database
     * 2. Fetches financial data for the topic/tickers
     * 3. Runs all rounds
     * 4. Returns the full debate output including synthesis
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
            @Valid @RequestBody SessionRequest request) {

        log.info("[DEBATE API] Start debate — topic={}", request.getTopic());

        // Create session
        SessionRecord session = memoryService.createSession(request);

        // Fetch data context
        List<DataType> dataTypes = request.getDataTypes() != null
                ? request.getDataTypes()
                : List.of(DataType.PRICE, DataType.FUNDAMENTALS, DataType.MACRO_INDICATOR);

        String primaryTicker = request.getTickers() != null && !request.getTickers().isEmpty()
                ? request.getTickers().get(0)
                : null;

        DataContext dataContext = dataService.fetchContext(
                primaryTicker, request.getTopic(), dataTypes);

        // Execute debate
        ModuleInput input = ModuleInput.builder()
                .session(session)
                .dataContext(dataContext)
                .targetRounds(request.getTargetRounds() > 0 ? request.getTargetRounds() : 2)
                .activeAgentIds(request.getActiveAgentIds())
                .build();

        ModuleOutput output = debateModule.execute(input);

        eventLogger.info("API", "DebateController", "DEBATE_STARTED",
                session.getId(), "Debate completed via API",
                Map.of("topic",   request.getTopic(),
                       "success", output.isSuccess()));

        return ResponseEntity.ok(buildResponse(session, output));
    }

    /**
     * Continue an existing ongoing session with additional rounds.
     */
    @PostMapping("/continue/{sessionId}")
    public ResponseEntity<Map<String, Object>> continueSession(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) Map<String, Object> body) {

        log.info("[DEBATE API] Continue session={}", sessionId);

        SessionRecord session = memoryService.loadSession(sessionId);

        int targetRounds = body != null && body.containsKey("targetRounds")
                ? (int) body.get("targetRounds") : 1;

        DataContext dataContext = dataService.fetchContext(
                null, session.getTopic(),
                List.of(DataType.PRICE, DataType.FUNDAMENTALS, DataType.MACRO_INDICATOR));

        ModuleInput input = ModuleInput.builder()
                .session(session)
                .dataContext(dataContext)
                .targetRounds(targetRounds)
                .build();

        ModuleOutput output = debateModule.execute(input);

        return ResponseEntity.ok(buildResponse(session, output));
    }

    // ─── Response builder ─────────────────────────────────────────────────────

    private Map<String, Object> buildResponse(SessionRecord session, ModuleOutput output) {
        return Map.of(
            "sessionId",       session.getId().toString(),
            "topic",           session.getTopic(),
            "success",         output.isSuccess(),
            "rounds",          output.getRounds() != null ? output.getRounds() : List.of(),
            "synthesis",       output.getSynthesis() != null ? output.getSynthesis() : Map.of(),
            "dataSourcesUsed", output.getDataSourcesUsed() != null
                               ? output.getDataSourcesUsed() : List.of(),
            "errorMessage",    output.getErrorMessage() != null ? output.getErrorMessage() : ""
        );
    }
}
