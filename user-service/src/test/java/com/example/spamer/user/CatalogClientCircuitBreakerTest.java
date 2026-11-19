package com.example.spamer.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;


@TestPropertySource(properties = {
    "spamer.circuit-breaker.sliding-window-size=2",
    "spamer.circuit-breaker.minimum-calls=2",
    "spamer.circuit-breaker.open-state-seconds=60"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogClientCircuitBreakerTest extends AbstractIntegrationTest {

    static final AtomicInteger HITS = new AtomicInteger();
    static volatile int stubStatus = 404;
    static final HttpServer STUB;

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.createContext("/", exchange -> {
                HITS.incrementAndGet();
                exchange.sendResponseHeaders(stubStatus, -1);
                exchange.close();
            });
            STUB.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start stub", e);
        }
    }

    @DynamicPropertySource
    static void catalogInstance(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.catalog-service[0].uri",
                () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
    }

    private Map<String, Object> body(String userId) {
        return Map.of("userId", userId, "serviceId", UUID.randomUUID());
    }

    @Test
    @Order(1)
    void remote404StaysNotFoundAndDoesNotOpenBreaker() {
        stubStatus = 404;
        String userId = createUser("USER_FREE", "0");

        for (int i = 0; i < 3; i++) {
            post("/api/v1/subscriptions", body(userId)).expectStatus().isNotFound();
        }

        assertThat(HITS.get()).isEqualTo(3);
    }

    @Test
    @Order(2)
    void repeatedFailuresOpenBreakerAndStopCalls() {
        stubStatus = 500;
        HITS.set(0);
        String userId = createUser("USER_FREE", "0");

        for (int i = 0; i < 4; i++) {
            post("/api/v1/subscriptions", body(userId))
                    .expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.status").isEqualTo(503);
        }


        assertThat(HITS.get()).isEqualTo(2);
    }
}
