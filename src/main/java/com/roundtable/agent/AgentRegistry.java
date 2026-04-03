package com.roundtable.agent;

import com.roundtable.agent.provider.AIProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads and manages agents from the agents database table.
 *
 * Agents are never hardcoded. Any change to agents (name, persona, model)
 * is done by updating the database — either via the UI or directly.
 * New agents are added by inserting a row — no code change needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRegistry {

    private final JdbcTemplate            jdbcTemplate;
    private final List<AIProviderService> providers;

    /**
     * Returns all active agents for a given module.
     * If moduleId is null, returns all active agents.
     */
    public List<AgentDefinition> getAgentsForModule(String moduleId) {
        String sql = moduleId != null
            ? "SELECT * FROM agents WHERE active = true AND ? = ANY(modules) ORDER BY name"
            : "SELECT * FROM agents WHERE active = true AND 'synthesiser' != ALL(modules) ORDER BY name";

        List<Map<String, Object>> rows = moduleId != null
            ? jdbcTemplate.queryForList(sql, moduleId)
            : jdbcTemplate.queryForList(sql);

        return rows.stream()
                .map(row -> mapToDefinition(row))
                .toList();
    }

    /**
     * Returns the Synthesiser agent (special — never participates in debate rounds).
     */
    public AgentDefinition getSynthesiser() {
        var rows = jdbcTemplate.queryForList(
            "SELECT * FROM agents WHERE active = true AND 'synthesiser' = ANY(modules) LIMIT 1");

        if (rows.isEmpty()) {
            throw new IllegalStateException("No active synthesiser agent found. "
                    + "Check the agents table — The Synthesiser should be seeded by V2 migration.");
        }
        return mapToDefinition(rows.get(0));
    }

    /**
     * Looks up the active prompt version for an agent.
     */
    public String getActivePromptVersion(UUID agentId) {
        var rows = jdbcTemplate.queryForList(
            "SELECT version FROM prompt_versions WHERE agent_id = ?::uuid AND active = true LIMIT 1",
            agentId.toString());
        return rows.isEmpty() ? "unknown" : (String) rows.get(0).get("version");
    }

    /**
     * Finds the provider service bean for a given provider name.
     */
    public AIProviderService getProvider(String providerName) {
        return providers.stream()
                .filter(p -> p.getProviderName().equalsIgnoreCase(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No provider registered for: " + providerName));
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private AgentDefinition mapToDefinition(Map<String, Object> row) {
        // PostgreSQL returns UUID columns as java.util.UUID objects, not Strings
        Object idObj = row.get("id");
        UUID id = idObj instanceof UUID
                ? (UUID) idObj
                : UUID.fromString(idObj.toString());

        return AgentDefinition.builder()
                .id(id)
                .name((String) row.get("name"))
                .provider((String) row.get("provider"))
                .model((String) row.get("model"))
                .domain((String) row.get("domain"))
                .persona((String) row.get("persona"))
                .outputFormat((String) row.get("output_format"))
                .build();
    }
}