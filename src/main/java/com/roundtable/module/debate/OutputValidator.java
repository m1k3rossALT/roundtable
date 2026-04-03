package com.roundtable.module.debate;

import com.roundtable.config.RoundtableConfig;
import com.roundtable.logging.EventLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and parses structured agent output.
 *
 * Expected format:
 *   POSITION:   [one clear sentence]
 *   REASONING:  [2-3 paragraphs]
 *   KEY RISK:   [one sentence]
 *   CONFIDENCE: [Low | Medium | High — reason]
 *
 * If the format is not followed, the caller is responsible for
 * triggering a retry (up to maxRetries configured in application.properties).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputValidator {

    private final RoundtableConfig config;
    private final EventLogger      eventLogger;

    private static final Pattern POSITION_PATTERN   = Pattern.compile(
            "POSITION:\\s*(.+?)(?=REASONING:|KEY RISK:|CONFIDENCE:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern REASONING_PATTERN  = Pattern.compile(
            "REASONING:\\s*(.+?)(?=KEY RISK:|CONFIDENCE:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_RISK_PATTERN   = Pattern.compile(
            "KEY RISK:\\s*(.+?)(?=CONFIDENCE:|$)",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile(
            "CONFIDENCE:\\s*(.+?)$",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    /**
     * Attempts to parse structured output.
     * Returns a ParsedOutput with isValid=true if all four fields are present.
     */
    public ParsedOutput parse(String rawText, String agentName, UUID sessionId) {
        log.trace("[VALIDATOR] Parsing output for agent={}", agentName);

        if (rawText == null || rawText.isBlank()) {
            return ParsedOutput.invalid(rawText, "Empty response received");
        }

        String position   = extract(POSITION_PATTERN, rawText);
        String reasoning  = extract(REASONING_PATTERN, rawText);
        String keyRisk    = extract(KEY_RISK_PATTERN, rawText);
        String confidence = extract(CONFIDENCE_PATTERN, rawText);

        boolean valid = position != null && reasoning != null
                     && keyRisk != null && confidence != null;

        if (!valid) {
            String missing = buildMissingFieldsMessage(position, reasoning, keyRisk, confidence);
            log.warn("[VALIDATOR] Invalid output from agent={}: missing fields={}",
                    agentName, missing);

            eventLogger.warn("DEBATE", "OutputValidator", "OUTPUT_FORMAT_INVALID",
                    sessionId, "Agent output missing required fields",
                    Map.of("agentName",    agentName,
                           "missingFields", missing));

            return ParsedOutput.invalid(rawText,
                    "Output missing required fields: " + missing);
        }

        log.debug("[VALIDATOR] Valid output from agent={}", agentName);
        return ParsedOutput.valid(rawText, position.trim(), reasoning.trim(),
                keyRisk.trim(), confidence.trim());
    }

    /**
     * Builds the retry instruction appended to the system prompt when
     * a response fails validation.
     */
    public String buildRetryInstruction(String originalPrompt, String failureReason) {
        return originalPrompt + "\n\n"
             + "IMPORTANT — YOUR PREVIOUS RESPONSE DID NOT FOLLOW THE REQUIRED FORMAT.\n"
             + "Reason: " + failureReason + "\n"
             + "You MUST structure your response exactly as follows:\n"
             + "POSITION: [one clear sentence stating your stance]\n"
             + "REASONING: [2-3 paragraphs grounded in the provided data]\n"
             + "KEY RISK: [the one thing that could invalidate your position]\n"
             + "CONFIDENCE: [Low | Medium | High — one-line reason]\n";
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String extract(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            String val = m.group(1).trim();
            return val.isBlank() ? null : val;
        }
        return null;
    }

    private String buildMissingFieldsMessage(String pos, String reas,
                                              String risk, String conf) {
        StringBuilder sb = new StringBuilder();
        if (pos   == null) sb.append("POSITION ");
        if (reas  == null) sb.append("REASONING ");
        if (risk  == null) sb.append("KEY RISK ");
        if (conf  == null) sb.append("CONFIDENCE");
        return sb.toString().trim();
    }

    // ─── ParsedOutput ────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    public static class ParsedOutput {
        private String  rawText;
        private String  position;
        private String  reasoning;
        private String  keyRisk;
        private String  confidence;
        private boolean valid;
        private String  invalidReason;

        static ParsedOutput valid(String raw, String pos, String reas,
                                   String risk, String conf) {
            return ParsedOutput.builder()
                    .rawText(raw).position(pos).reasoning(reas)
                    .keyRisk(risk).confidence(conf).valid(true).build();
        }

        static ParsedOutput invalid(String raw, String reason) {
            return ParsedOutput.builder()
                    .rawText(raw).valid(false).invalidReason(reason).build();
        }
    }
}
