package com.example.spamer.common.error;

// Another microservice is down or its circuit breaker is open. Maps to 503.
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super("Service temporarily unavailable: " + dependency, cause);
    }
}
