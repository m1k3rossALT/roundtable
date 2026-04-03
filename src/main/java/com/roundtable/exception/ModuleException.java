package com.roundtable.exception;

/**
 * Thrown when an analysis module encounters an unrecoverable error.
 */
public class ModuleException extends RuntimeException {

    private final String moduleId;

    public ModuleException(String moduleId, String message) {
        super(message);
        this.moduleId = moduleId;
    }

    public ModuleException(String moduleId, String message, Throwable cause) {
        super(message, cause);
        this.moduleId = moduleId;
    }

    public String getModuleId() { return moduleId; }
}
