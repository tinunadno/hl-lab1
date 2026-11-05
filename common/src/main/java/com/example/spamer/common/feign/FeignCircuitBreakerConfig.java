package com.example.spamer.common.feign;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Breaker settings for all Feign calls. Values come from the Config Server.
 * A 4xx is not a failure of the remote service (a missing user must not open
 * the breaker), so client errors are ignored.
 */
@Configuration
public class FeignCircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> feignCircuitBreakerDefaults(
            @Value("${spamer.circuit-breaker.sliding-window-size:10}") int slidingWindowSize,
            @Value("${spamer.circuit-breaker.minimum-calls:5}") int minimumCalls,
            @Value("${spamer.circuit-breaker.failure-rate-percent:50}") float failureRatePercent,
            @Value("${spamer.circuit-breaker.open-state-seconds:10}") long openStateSeconds,
            @Value("${spamer.circuit-breaker.timeout-seconds:5}") long timeoutSeconds) {
        CircuitBreakerConfig breaker = CircuitBreakerConfig.custom()
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumCalls)
                .failureRateThreshold(failureRatePercent)
                .waitDurationInOpenState(Duration.ofSeconds(openStateSeconds))
                .ignoreExceptions(FeignException.FeignClientException.class)
                .build();
        TimeLimiterConfig timeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(timeoutSeconds))
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(breaker)
                .timeLimiterConfig(timeLimiter)
                .build());
    }
}
