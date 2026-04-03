package com.roundtable.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MCP Tool Registry — Phase 1 stub, wired in Phase 2.
 *
 * This class exists in Phase 1 to ensure all interfaces are MCP-ready.
 * The registry is empty but the infrastructure is in place:
 *   - Tools can be registered without changing any other class
 *   - The DebateOrchestrator is designed to accept MCP context
 *   - Data adapters are the canonical tool implementations
 *
 * Phase 2 will:
 *   1. Implement the Spring MCP server library
 *   2. Register get_market_data, get_macro_indicator,
 *      search_rag, get_session_summary as MCP tools
 *   3. Each tool delegates to the existing DataService or MemoryService
 *   4. Agents call tools mid-reasoning rather than receiving pre-loaded data
 *
 * Adding a tool in Phase 2:
 *   registry.register("get_market_data", params ->
 *       dataService.fetchContext(params.get("ticker"), null, List.of(DataType.PRICE)));
 */
@Slf4j
@Service
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new HashMap<>();

    /**
     * Register a new MCP tool.
     * Phase 2: called during application startup for each tool.
     */
    public void register(String toolName, McpTool tool) {
        tools.put(toolName, tool);
        log.info("[MCP] Tool registered: {}", toolName);
    }

    /**
     * Invoke a registered tool by name.
     */
    public Object invoke(String toolName, Map<String, String> params) {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("MCP tool not found: " + toolName);
        }
        return tool.execute(params);
    }

    /** Returns names of all registered tools */
    public Set<String> getRegisteredTools() {
        return tools.keySet();
    }

    /** True if at least one tool is registered (Phase 2 check) */
    public boolean isActive() {
        return !tools.isEmpty();
    }

    /** Functional interface for tool implementations */
    @FunctionalInterface
    public interface McpTool {
        Object execute(Map<String, String> params);
    }
}
