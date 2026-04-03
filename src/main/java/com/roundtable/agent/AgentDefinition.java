package com.roundtable.agent;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

/**
 * Immutable agent definition loaded from the database.
 * This is what the debate engine works with at runtime.
 */
@Data
@Builder
public class AgentDefinition {
    private UUID   id;
    private String name;
    private String provider;
    private String model;
    private String domain;
    private String persona;
    private String outputFormat;
}
