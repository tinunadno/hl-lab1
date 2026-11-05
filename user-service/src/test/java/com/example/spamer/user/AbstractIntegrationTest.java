package com.example.spamer.user;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureWebTestClient
public abstract class AbstractIntegrationTest {

    // One container for all test classes: cached Spring contexts keep using it.
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

    @Autowired
    protected WebTestClient web;

    protected WebTestClient.ResponseSpec post(String uri, Object body) {
        return web.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }

    protected String createUser(String role, String balance) {
        String unique = "u" + System.nanoTime();
        return post("/api/v1/users", Map.of(
                        "username", unique,
                        "email", unique + "@example.com",
                        "role", role,
                        "balance", balance))
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult().getResponseBody()
                .get("id").toString();
    }
}
