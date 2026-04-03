package com.roundtable.api.dto;

import com.roundtable.data.DataType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Public DTO for session creation requests.
 * Used by SessionController and passed through to MemoryService.
 */
@Data
public class SessionRequest {
    @NotBlank public String        title;
    @NotBlank public String        topic;
    @NotBlank public String        type;          // standalone | ongoing | combined
    public String                  globalContext;
    public String                  riskTolerance;
    public String                  assetClass;
    public List<String>            tickers;
    public List<DataType>          dataTypes;
    public List<UUID>              parentSessionIds;
    public int                     targetRounds;
    public List<String>            activeAgentIds;
}
