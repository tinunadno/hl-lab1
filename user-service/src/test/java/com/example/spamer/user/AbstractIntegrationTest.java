package com.example.spamer.user;

import com.example.spamer.common.security.JwtTokenIssuer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureWebTestClient
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "secret-password-1";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getFirstMappedPort() + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.url", POSTGRES::getJdbcUrl);
        registry.add("spring.liquibase.user", POSTGRES::getUsername);
        registry.add("spring.liquibase.password", POSTGRES::getPassword);
    }

    /** Клиент без токена. */
    @Autowired
    protected WebTestClient anonymous;

    @Autowired
    protected JwtTokenIssuer tokens;

    /** Клиент с токеном супервайзера: им выполняется подготовка данных. */
    protected WebTestClient web;

    @BeforeEach
    void authenticateAsSupervisor() {
        web = as(supervisorToken());
    }

    protected String supervisorToken() {
        return tokens.issueForUser(UUID.randomUUID(), "test-supervisor", "SUPERVISOR").value();
    }

    protected String serviceToken() {
        return tokens.issueForService("dispatch-service").value();
    }

    protected String tokenFor(String userId, String role) {
        return tokens.issueForUser(UUID.fromString(userId), "user-" + userId, role).value();
    }

    protected WebTestClient as(String token) {
        return anonymous.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    protected WebTestClient.ResponseSpec post(String uri, Object body) {
        return post(web, uri, body);
    }

    protected WebTestClient.ResponseSpec post(WebTestClient client, String uri, Object body) {
        return client.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    /** Внутренние эндпоинты списания и возврата доступны только служебному токену. */
    protected WebTestClient.ResponseSpec internal(String uri, Object body) {
        return post(as(serviceToken()), uri, body);
    }

    protected String createUser(String role, String balance) {
        return createUser("u" + System.nanoTime(), role, balance);
    }

    protected String createUser(String username, String role, String balance) {
        return post("/api/v1/users", Map.of(
                        "username", username,
                        "email", username + "@example.com",
                        "role", role,
                        "balance", balance,
                        "password", PASSWORD))
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody()
                .get("id").toString();
    }
}
