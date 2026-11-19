package com.example.spamer.common.error;


public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super("Service temporarily unavailable: " + dependency, cause);
    }
}
