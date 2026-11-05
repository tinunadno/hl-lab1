package com.example.spamer.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.spamer.catalog.dto.ServiceResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureWebTestClient
@Testcontainers
class ServiceCatalogApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    WebTestClient web;

    private ServiceResponse create(String name) {
        return web.post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "description", "desc", "pricePerMessage", "2.5000"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceResponse.class)
                .returnResult().getResponseBody();
    }

    @Test
    void crudLifecycle() {
        ServiceResponse created = create("catalog-" + System.nanoTime());
        assertThat(created.active()).isTrue();

        web.get().uri("/api/v1/services/{id}", created.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.pricePerMessage").isEqualTo(2.5);

        web.put().uri("/api/v1/services/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "replaced-" + System.nanoTime(),
                        "pricePerMessage", "3.0000",
                        "active", false))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.active").isEqualTo(false);

        web.patch().uri("/api/v1/services/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("active", true, "description", "patched"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.active").isEqualTo(true)
                .jsonPath("$.description").isEqualTo("patched");

        web.get().uri("/api/v1/services?page=0&size=20").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Total-Count");

        web.delete().uri("/api/v1/services/{id}", created.id()).exchange()
                .expectStatus().isNoContent();
        web.get().uri("/api/v1/services/{id}", created.id()).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void duplicateNameConflicts() {
        String name = "dup-" + System.nanoTime();
        create(name);

        web.post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "pricePerMessage", "1.0000"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void validationErrorHasDetails() {
        web.post().uri("/api/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "", "pricePerMessage", "-1"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.details").isNotEmpty();
    }

    @Test
    void sizeAboveLimitRejected() {
        web.get().uri("/api/v1/services?size=51").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void malformedIdRejected() {
        web.get().uri("/api/v1/services/not-a-uuid").exchange()
                .expectStatus().isBadRequest();
    }
}
