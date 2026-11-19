package com.example.spamer.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.user.client.CatalogClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class UserApiTest extends AbstractIntegrationTest {

    @MockitoBean
    CatalogClient catalog;

    @Test
    void crudLifecycle() {
        String id = createUser("USER_FREE", "10.00");

        web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.balance").isEqualTo(10.0);

        String renamed = "renamed" + System.nanoTime();
        web.put().uri("/api/v1/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "username", renamed,
                        "email", renamed + "@example.com",
                        "role", "USER_PRO",
                        "balance", "5.00"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.role").isEqualTo("USER_PRO");

        web.patch().uri("/api/v1/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("balance", "99.50"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(99.5)
                .jsonPath("$.username").isEqualTo(renamed)
                .jsonPath("$.updatedAt").exists();

        web.get().uri("/api/v1/users?page=0&size=20").exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Total-Count");

        web.delete().uri("/api/v1/users/{id}", id).exchange().expectStatus().isNoContent();
        web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void duplicateUsernameConflicts() {
        String name = "dup" + System.nanoTime();
        post("/api/v1/users", Map.of(
                "username", name, "email", name + "@example.com", "role", "USER_FREE"))
                .expectStatus().isCreated();

        post("/api/v1/users", Map.of(
                "username", name, "email", "other" + name + "@example.com", "role", "USER_FREE"))
                .expectStatus().isEqualTo(409);
    }

    @Test
    void validationErrorHasDetails() {
        post("/api/v1/users", Map.of("username", "", "email", "not-an-email", "role", "USER_FREE"))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.details").isNotEmpty();
    }

    @Test
    void sizeAboveLimitRejected() {
        web.get().uri("/api/v1/users?size=51").exchange().expectStatus().isBadRequest();
    }

    @Test
    void debitIsAtomicAndIdempotent() {
        String id = createUser("USER_PRO", "10.00");
        String uri = "/internal/v1/users/" + id + "/balance/debit";
        String operation = UUID.randomUUID().toString();

        post(uri, Map.of("operationId", operation, "amount", "4.00"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.balance").isEqualTo(6.0);


        post(uri, Map.of("operationId", operation, "amount", "4.00"))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.balance").isEqualTo(6.0);
    }

    @Test
    void rejectedDebitLeavesNoTrace() {
        String id = createUser("USER_PRO", "1.00");
        String operation = UUID.randomUUID().toString();

        post("/internal/v1/users/" + id + "/balance/debit",
                Map.of("operationId", operation, "amount", "5.00"))
                .expectStatus().isEqualTo(422);


        post("/internal/v1/users/" + id + "/balance/refund", Map.of("operationId", operation))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.balance").isEqualTo(1.0);
    }

    @Test
    void refundReturnsMoneyOnce() {
        String id = createUser("USER_PRO", "10.00");
        String operation = UUID.randomUUID().toString();
        post("/internal/v1/users/" + id + "/balance/debit",
                Map.of("operationId", operation, "amount", "4.00"))
                .expectStatus().isOk();

        for (int i = 0; i < 2; i++) {
            post("/internal/v1/users/" + id + "/balance/refund", Map.of("operationId", operation))
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.balance").isEqualTo(10.0);
        }


        post("/internal/v1/users/" + id + "/balance/debit",
                Map.of("operationId", operation, "amount", "4.00"))
                .expectStatus().isEqualTo(409);
    }

    @Test
    void lateDebitAfterRefundIsIgnored() {
        String id = createUser("USER_PRO", "10.00");
        String operation = UUID.randomUUID().toString();

        post("/internal/v1/users/" + id + "/balance/refund", Map.of("operationId", operation))
                .expectStatus().isOk()
                .expectBody().jsonPath("$.balance").isEqualTo(10.0);

        post("/internal/v1/users/" + id + "/balance/debit",
                Map.of("operationId", operation, "amount", "4.00"))
                .expectStatus().isEqualTo(409);

        web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectBody().jsonPath("$.balance").isEqualTo(10.0);
    }

    @Test
    void subscribePromotesFreeUserAndRejectsDuplicate() {
        UUID serviceId = UUID.randomUUID();
        when(catalog.get(serviceId))
                .thenReturn(new CatalogClient.CatalogServiceDto(serviceId, "bulk", true));
        String id = createUser("USER_FREE", "0");

        post("/api/v1/subscriptions", Map.of(
                "userId", id, "serviceId", serviceId, "discountPercent", "10.00"))
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.discountPercent").isEqualTo(10.0);

        web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectBody().jsonPath("$.role").isEqualTo("USER_PRO");

        post("/api/v1/subscriptions", Map.of("userId", id, "serviceId", serviceId))
                .expectStatus().isEqualTo(409);
    }

    @Test
    void adminKeepsRoleWhenSubscribing() {
        UUID serviceId = UUID.randomUUID();
        when(catalog.get(serviceId))
                .thenReturn(new CatalogClient.CatalogServiceDto(serviceId, "bulk", true));
        String id = createUser("ADMIN", "0");

        post("/api/v1/subscriptions", Map.of("userId", id, "serviceId", serviceId))
                .expectStatus().isCreated();

        web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectBody().jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void subscribingToMissingServiceGives404() {
        when(catalog.get(any())).thenThrow(new NotFoundException("Service not found"));
        String id = createUser("USER_FREE", "0");

        post("/api/v1/subscriptions", Map.of("userId", id, "serviceId", UUID.randomUUID()))
                .expectStatus().isNotFound();

        Map<?, ?> user = web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectBody(Map.class).returnResult().getResponseBody();
        assertThat(user.get("role")).isEqualTo("USER_FREE");
    }
}
