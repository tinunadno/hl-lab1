package com.example.spamer.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.spamer.user.client.CatalogClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

class SecurityApiTest extends AbstractIntegrationTest {

    @MockitoBean
    CatalogClient catalog;

    @Autowired
    DatabaseClient db;

    private WebTestClient.ResponseSpec login(String username, String password) {
        return post(anonymous, "/api/v1/auth/login",
                Map.of("username", username, "password", password));
    }

    private String loginToken(String username) {
        return login(username, PASSWORD)
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody()
                .get("accessToken").toString();
    }

    @Test
    void loginIssuesTokenThatOpensOwnProfile() {
        String name = "login" + System.nanoTime();
        String id = createUser(name, "USER_PRO", "3.00");

        login(name, PASSWORD).expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.userId").isEqualTo(id)
                .jsonPath("$.role").isEqualTo("USER_PRO")
                .jsonPath("$.password").doesNotExist();

        as(loginToken(name)).get().uri("/api/v1/users/me").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.id").isEqualTo(id);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSame401() {
        String name = "wrong" + System.nanoTime();
        createUser(name, "USER_FREE", "0");

        login(name, "not-the-password").expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid username or password");
        login("nobody" + System.nanoTime(), PASSWORD).expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid username or password");
    }

    @Test
    void passwordIsStoredAsBcryptHashAndNeverReturned() {
        String name = "hash" + System.nanoTime();
        String id = createUser(name, "USER_FREE", "0");

        String stored = db.sql("SELECT password_hash FROM users WHERE id = :id")
                .bind("id", UUID.fromString(id))
                .map((row, meta) -> row.get("password_hash", String.class))
                .one().block();
        assertThat(stored).startsWith("$2").isNotEqualTo(PASSWORD);

        web.get().uri("/api/v1/users/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.passwordHash").doesNotExist();
        web.get().uri("/api/v1/users").exchange()
                .expectBody(String.class)
                .value(body -> assertThat(body).doesNotContain("password").doesNotContain("$2"));
    }

    @Test
    void requestWithoutTokenIsRejected() {
        anonymous.get().uri("/api/v1/users").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals("WWW-Authenticate", "Bearer")
                .expectBody().jsonPath("$.status").isEqualTo(401);
        anonymous.get().uri("/api/v1/users").header("Authorization", "Bearer not-a-jwt").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void onlySupervisorCreatesUsers() {
        for (String role : new String[] {"USER_PRO", "USER_FREE", "BUY_OPERATOR", "VICTIM"}) {
            String id = createUser(role, "0");
            post(as(tokenFor(id, role)), "/api/v1/users", Map.of(
                    "username", "x" + System.nanoTime(), "email", "x" + System.nanoTime() + "@e.com",
                    "role", "SUPERVISOR", "password", PASSWORD))
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.status").isEqualTo(403);
        }
    }

    @Test
    void userSeesOnlyOwnProfile() {
        String me = createUser("USER_FREE", "0");
        String other = createUser("USER_FREE", "0");
        WebTestClient client = as(tokenFor(me, "USER_FREE"));

        client.get().uri("/api/v1/users/{id}", me).exchange().expectStatus().isOk();
        client.get().uri("/api/v1/users/{id}", other).exchange().expectStatus().isForbidden();
        client.get().uri("/api/v1/users").exchange().expectStatus().isForbidden();
        client.delete().uri("/api/v1/users/{id}", other).exchange().expectStatus().isForbidden();
    }

    @Test
    void ownerChangesOnlyEmailAndPassword() {
        String name = "owner" + System.nanoTime();
        String me = createUser(name, "USER_FREE", "1.00");
        WebTestClient client = as(tokenFor(me, "USER_FREE"));

        client.patch().uri("/api/v1/users/{id}", me)
                .bodyValue(Map.of("email", name + "@new.example.com", "password", "brand-new-pass"))
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.email").isEqualTo(name + "@new.example.com");
        login(name, "brand-new-pass").expectStatus().isOk();
        login(name, PASSWORD).expectStatus().isUnauthorized();

        client.patch().uri("/api/v1/users/{id}", me)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("role", "SUPERVISOR"))
                .exchange().expectStatus().isForbidden();
        client.patch().uri("/api/v1/users/{id}", me)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("balance", "1000.00"))
                .exchange().expectStatus().isForbidden();

        String other = createUser("USER_FREE", "0");
        client.patch().uri("/api/v1/users/{id}", other)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("email", "steal@example.com"))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void supervisorCannotDeleteThemselves() {
        String me = createUser("SUPERVISOR", "0");

        as(tokenFor(me, "SUPERVISOR")).delete().uri("/api/v1/users/{id}", me).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void internalBalanceNeedsServiceToken() {
        String id = createUser("USER_PRO", "10.00");
        Map<String, Object> debit = Map.of("operationId", UUID.randomUUID(), "amount", "1.00");

        post(as(tokenFor(id, "USER_PRO")), "/internal/v1/users/" + id + "/balance/debit", debit)
                .expectStatus().isForbidden();
        post("/internal/v1/users/" + id + "/balance/debit", debit)
                .expectStatus().isForbidden();
        internal("/internal/v1/users/" + id + "/balance/debit", debit)
                .expectStatus().isOk();
    }

    @Test
    void serviceTokenCanReadAnyUser() {
        String id = createUser("USER_PRO", "2.00");

        as(serviceToken()).get().uri("/api/v1/users/{id}", id).exchange()
                .expectStatus().isOk();
        as(serviceToken()).get().uri("/api/v1/users/me").exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void clientSubscribesOnlyThemselvesAndWithoutDiscount() {
        UUID serviceId = UUID.randomUUID();
        when(catalog.get(serviceId))
                .thenReturn(new CatalogClient.CatalogServiceDto(serviceId, "bulk", true));
        String me = createUser("USER_FREE", "0");
        String other = createUser("USER_FREE", "0");
        WebTestClient client = as(tokenFor(me, "USER_FREE"));

        post(client, "/api/v1/subscriptions", Map.of("userId", other, "serviceId", serviceId))
                .expectStatus().isForbidden();
        post(client, "/api/v1/subscriptions", Map.of("serviceId", serviceId, "discountPercent", "50"))
                .expectStatus().isForbidden();

        post(client, "/api/v1/subscriptions", Map.of("serviceId", serviceId))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(me)
                .jsonPath("$.discountPercent").isEqualTo(0);
    }

    @Test
    void buyOperatorAndVictimCannotSubscribe() {
        for (String role : new String[] {"BUY_OPERATOR", "VICTIM"}) {
            String id = createUser(role, "0");
            post(as(tokenFor(id, role)), "/api/v1/subscriptions",
                    Map.of("serviceId", UUID.randomUUID()))
                    .expectStatus().isForbidden();
        }
    }

    @Test
    void supervisorMustNameTheSubscriber() {
        post("/api/v1/subscriptions", Map.of("serviceId", UUID.randomUUID()))
                .expectStatus().isEqualTo(422);
    }

    @Test
    void bootstrapSupervisorCanLogInAndCreateUsers() {
        String token = login("root", "root-password-123")
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody()
                .get("accessToken").toString();

        post(as(token), "/api/v1/users", Map.of(
                "username", "made-by-root" + System.nanoTime(),
                "email", "root" + System.nanoTime() + "@example.com",
                "role", "BUY_OPERATOR",
                "password", PASSWORD))
                .expectStatus().isCreated();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = "eyJhbGciOiJIUzI1NiJ9."
                + "eyJpc3MiOiJzcGFtZXIiLCJzdWIiOiJ4Iiwicm9sZSI6IlNVUEVSVklTT1IiLCJleHAiOjQxMDI0NDQ4MDB9."
                + "c2lnbmF0dXJlLXdpdGgtd3Jvbmcta2V5LXNpZ25hdHVyZS13aXRo";

        as(forged).get().uri("/api/v1/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void openApiAndHealthArePublic() {
        anonymous.get().uri("/actuator/health").exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
        anonymous.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                .isEqualTo("bearer");
    }
}
