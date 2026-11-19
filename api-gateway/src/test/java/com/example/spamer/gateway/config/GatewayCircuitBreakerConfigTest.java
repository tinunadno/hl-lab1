package com.example.spamer.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration;

class GatewayCircuitBreakerConfigTest {

    @Test
    void routeCallUsesTheConfiguredDeadline() {
        ReactiveResilience4JCircuitBreakerFactory factory =
                mock(ReactiveResilience4JCircuitBreakerFactory.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<String, Resilience4JCircuitBreakerConfiguration>> captor =
                ArgumentCaptor.forClass(Function.class);

        new GatewayCircuitBreakerConfig().gatewayCircuitBreakerDefaults(60L).customize(factory);

        verify(factory).configureDefault(captor.capture());
        Resilience4JCircuitBreakerConfiguration built = captor.getValue().apply("user-service");

        assertThat(built.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(60));
        assertThat(built.getCircuitBreakerConfig().getSlidingWindowSize())
                .isEqualTo(CircuitBreakerConfig.ofDefaults().getSlidingWindowSize());
    }
}
