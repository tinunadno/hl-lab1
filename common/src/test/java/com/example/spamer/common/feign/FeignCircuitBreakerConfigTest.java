package com.example.spamer.common.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder.Resilience4JCircuitBreakerConfiguration;

class FeignCircuitBreakerConfigTest {

    @Test
    void clientErrorsDoNotCountAndCallHasADeadline() {
        Resilience4JCircuitBreakerFactory factory = mock(Resilience4JCircuitBreakerFactory.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<String, Resilience4JCircuitBreakerConfiguration>> captor =
                ArgumentCaptor.forClass(Function.class);

        new FeignCircuitBreakerConfig()
                .feignCircuitBreakerDefaults(8, 4, 40f, 12L, 3L)
                .customize(factory);

        verify(factory).configureDefault(captor.capture());
        Resilience4JCircuitBreakerConfiguration built = captor.getValue().apply("catalog-service");

        assertThat(built.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(8);
        assertThat(built.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(4);
        assertThat(built.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(40f);
        assertThat(built.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                .isEqualTo(Duration.ofSeconds(12).toMillis());
        assertThat(built.getTimeLimiterConfig().getTimeoutDuration()).isEqualTo(Duration.ofSeconds(3));
        assertThat(built.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(status(404)))
                .isTrue();
        assertThat(built.getCircuitBreakerConfig().getIgnoreExceptionPredicate().test(status(500)))
                .isFalse();
    }

    private static FeignException status(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET, "/x", Map.of(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("x", feign.Response.builder()
                .status(status)
                .reason("r")
                .request(request)
                .headers(Map.of())
                .body("{}", StandardCharsets.UTF_8)
                .build());
    }
}
