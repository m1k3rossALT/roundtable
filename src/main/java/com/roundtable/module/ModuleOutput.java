package com.roundtable.module;

import com.roundtable.memory.MemoryRecords.RoundRecord;
import com.roundtable.memory.MemoryRecords.SynthesisRecord;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Standard output contract for all analysis modules.
 */
@Data
@Builder
public class ModuleOutput {
    private String            sessionId;
    private List<RoundRecord> rounds;
    private SynthesisRecord   synthesis;
    private List<String>      dataSourcesUsed;
    private boolean           success;
    private String            errorMessage;

    public static ModuleOutput failure(String errorMessage) {
        return ModuleOutput.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
