package com.roundtable.module;

/**
 * Contract every analysis module must implement.
 *
 * Analysis modules are the "what does the system do" layer.
 * Each module is self-contained — it defines its own workflow,
 * uses agents and data via their interfaces, and produces a
 * structured output.
 *
 * Adding a new module (e.g. Hidden Gem Finder):
 *   1. Implement this interface
 *   2. Register as a Spring @Service
 *   3. Add a row to the modules table via Flyway migration
 *   4. Nothing else in the system changes
 *
 * MCP-ready: In Phase 2, modules will be able to request
 * MCP tools from the McpToolRegistry to enrich agent context.
 */
public interface AnalysisModule {

    /** Unique module identifier — matches modules.name in DB */
    String getModuleId();

    /** Human-readable name */
    String getDisplayName();

    /** Execute the module workflow for a given session */
    ModuleOutput execute(ModuleInput input);

    /** True if this module can handle the given module type string */
    default boolean supports(String moduleType) {
        return getModuleId().equalsIgnoreCase(moduleType);
    }
}
