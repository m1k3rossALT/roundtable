package com.roundtable.exception;

/**
 * Thrown when an AI provider API call fails.
 * Carries the provider name for targeted error handling and logging.
 */
public class ProviderException extends RuntimeException {

    private final String provider;

    public ProviderException(String provider, String message) {
        super(message);
        this.provider = provider;
    }

    public ProviderException(String provider, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
    }

    public String getProvider() { return provider; }
}
