package com.roundtable.module;

import com.roundtable.data.DataContext;
import com.roundtable.memory.MemoryRecords.SessionRecord;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Standard input contract for all analysis modules.
 */
@Data
@Builder
public class ModuleInput {
    private SessionRecord session;
    private DataContext   dataContext;
    private int           targetRounds;
    private List<String>  activeAgentIds;  // null = use all agents for this module
    private Map<String, Object> extraParams; // module-specific overrides
}
