package com.example.spamer.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.spamer.common.error.BusinessException;
import com.example.spamer.common.error.DependencyUnavailableException;
import com.example.spamer.common.error.NotFoundException;
import com.example.spamer.common.security.JwtTokenIssuer;
import com.example.spamer.dispatch.client.CatalogClient;
import com.example.spamer.dispatch.client.UserClient;
import com.example.spamer.dispatch.service.RefundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@SpringBootTest
@Testcontainers
class DispatchApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static final AtomicInteger HITS = new AtomicInteger();
    static volatile int receiverStatus = 200;
    static final HttpServer STUB;

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.createContext("/inbox", exchange -> {
                HITS.incrementAndGet();
                byte[] reply = "{\"received\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(receiverStatus, reply.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(reply);
                }
            });
            STUB.start();
        } catch (IOException e) {
            throw new IllegalStateException("could not start stub receiver", e);
        }
    }

    @DynamicPropertySource
    static void senderTarget(DynamicPropertyRegistry registry) {
        registry.add("spamer.sender.target-url",
                () -> "http://127.0.0.1:" + STUB.getAddress().getPort() + "/inbox");
    }

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired RefundService refunds;
    @Autowired JwtTokenIssuer tokens;

    /** Все запросы по умолчанию идут с токеном супервайзера; тесты ролей задают свой. */
    MockMvc mvc;

    @MockitoBean UserClient users;
    @MockitoBean CatalogClient catalog;

    UUID userId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").header(HttpHeaders.AUTHORIZATION, bearer(UUID.randomUUID(), "SUPERVISOR")))
                .build();
    }

    private String bearer(UUID id, String role) {
        return "Bearer " + tokens.issueForUser(id, "test-" + role, role).value();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID id, String role) {
        return request.header(HttpHeaders.AUTHORIZATION, bearer(id, role));
    }

    private JsonNode send(Map<String, Object> body, int expectedStatus) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/spam/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }

    private Map<String, Object> sendBody(int count) {
        return Map.of(
                "userId", userId,
                "serviceId", serviceId,
                "victimContact", "demo@example.com",
                "messageBody", "hello",
                "messageCount", count);
    }

    private void priced(String price) {
        when(catalog.get(serviceId)).thenReturn(
                new CatalogClient.ServiceDto(serviceId, "svc", new BigDecimal(price), true));
    }

    private String createLog(String contact) throws Exception {
        return createLog(userId, contact);
    }

    private String createLog(UUID owner, String contact) throws Exception {
        when(users.get(owner)).thenReturn(new UserClient.UserDto(owner, BigDecimal.TEN));
        MvcResult res = mvc.perform(post("/api/v1/spam-log")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "userId", owner,
                                "victimContact", contact,
                                "status", "QUEUED"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void sendReachesReceiverAndJournals() throws Exception {
        receiverStatus = 200;
        priced("2.0000");
        when(users.debit(eq(userId), any()))
                .thenReturn(new UserClient.BalanceDto(new BigDecimal("94.00")));
        mvc.perform(post("/api/v1/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "host", "10.0.0.1", "port", 8080,
                                "protocol", "HTTP", "status", "ACTIVE"))))
                .andExpect(status().isCreated());
        int before = HITS.get();

        JsonNode sent = send(sendBody(3), 200);

        assertThat(sent.get("status").asText()).isEqualTo("SENT");
        assertThat(sent.get("delivered").asInt()).isEqualTo(3);
        assertThat(sent.get("remainingBalance").asDouble()).isEqualTo(94.0);
        assertThat(HITS.get() - before).isEqualTo(3);

        mvc.perform(get("/api/v1/spam-log/{id}", sent.get("spamLogId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SENT")))
                .andExpect(jsonPath("$.proxyIds", hasSize(greaterThan(0))));
    }

    @Test
    void failedDeliveryIsJournaledAndRefunded() throws Exception {
        receiverStatus = 503;
        priced("1.0000");
        when(users.debit(eq(userId), any()))
                .thenReturn(new UserClient.BalanceDto(new BigDecimal("9.00")));

        send(sendBody(1), 422);

        verify(users).refund(eq(userId), any());
    }

    @Test
    void refusedDebitLeavesNoJournalRow() throws Exception {
        priced("5.0000");
        when(users.debit(eq(userId), any())).thenThrow(new BusinessException("Insufficient"));
        int before = HITS.get();

        send(sendBody(1), 422);

        assertThat(HITS.get()).isEqualTo(before);
        verify(users, never()).refund(any(), any());
    }

    @Test
    void refundIsRetriedAfterUserServiceRecovers() throws Exception {
        priced("1.0000");
        when(users.debit(eq(userId), any())).thenThrow(
                new DependencyUnavailableException("user-service", new RuntimeException()));
        when(users.refund(eq(userId), any())).thenThrow(
                new DependencyUnavailableException("user-service", new RuntimeException()));

        send(sendBody(1), 503);


        org.mockito.Mockito.reset(users);
        refunds.retryPending();
        verify(users).refund(eq(userId), any());

        org.mockito.Mockito.clearInvocations(users);
        refunds.retryPending();
        verify(users, never()).refund(eq(userId), any());
    }

    @Test
    void unavailableCatalogGives503() throws Exception {
        when(catalog.get(serviceId)).thenThrow(
                new DependencyUnavailableException("catalog-service", new RuntimeException()));

        send(sendBody(1), 503);
    }

    @Test
    void proxyCrudLifecycle() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "host", "10.0.0.8", "port", 8080, "protocol", "HTTP",
                                "country", "FI", "status", "ACTIVE",
                                "providerId", serviceId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providerId", is(serviceId.toString())))
                .andReturn();
        String id = json.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(get("/api/v1/proxy/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.port", is(8080)));

        mvc.perform(put("/api/v1/proxy/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "host", "10.0.0.9", "port", 1080, "protocol", "SOCKS5",
                                "country", "DE", "status", "CHECKING"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocol", is("SOCKS5")));

        mvc.perform(patch("/api/v1/proxy/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "status", "BANNED", "port", 1081, "country", "NL",
                                "protocol", "HTTPS", "host", "10.0.0.10"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BANNED")))
                .andExpect(jsonPath("$.host", is("10.0.0.10")));

        mvc.perform(get("/api/v1/proxy").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"));

        mvc.perform(delete("/api/v1/proxy/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/proxy/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void proxyWithUnknownProviderRejected() throws Exception {
        when(catalog.get(serviceId)).thenThrow(NotFoundException.of("Service", serviceId));

        mvc.perform(post("/api/v1/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "host", "10.0.0.8", "port", 8080, "protocol", "HTTP",
                                "status", "ACTIVE", "providerId", serviceId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void spamLogCrudAndCursorFeed() throws Exception {
        String id = createLog("first@example.com");
        createLog("second@example.com");
        createLog("third@example.com");

        mvc.perform(put("/api/v1/spam-log/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "userId", userId,
                                "victimContact", "replaced@example.com",
                                "status", "BLOCKED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BLOCKED")));

        mvc.perform(patch("/api/v1/spam-log/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "status", "FAILED",
                                "messageBody", "patched",
                                "victimContact", "patched@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageBody", is("patched")));

        mvc.perform(get("/api/v1/spam-log").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"));

        MvcResult first = mvc.perform(get("/api/v1/spam-log/feed").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(header().doesNotExist("X-Total-Count"))
                .andReturn();
        String cursor = json.readTree(first.getResponse().getContentAsString())
                .get("nextCursor").asText();

        mvc.perform(get("/api/v1/spam-log/feed").param("cursor", cursor).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists());

        mvc.perform(delete("/api/v1/spam-log/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/spam-log/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void validationAndPageLimitsRejected() throws Exception {
        mvc.perform(post("/api/v1/spam/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details", hasSize(greaterThan(0))));

        mvc.perform(get("/api/v1/proxy").param("size", "51"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/proxy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void builtInReceiverEchoes() throws Exception {
        mvc.perform(post("/api/v1/test-receiver/inbox")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("contact", "x", "message", "hi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received", is(true)))
                .andExpect(jsonPath("$.echo.contact", is("x")));
    }

    // ---- лабораторная №3: роли ----

    private Map<String, Object> ownSendBody(int count) {
        return Map.of(
                "serviceId", serviceId,
                "victimContact", "demo@example.com",
                "messageBody", "hello",
                "messageCount", count);
    }

    @Test
    void clientSendsFromOwnAccountWithoutPassingUserId() throws Exception {
        receiverStatus = 200;
        priced("1.0000");
        when(users.debit(eq(userId), any()))
                .thenReturn(new UserClient.BalanceDto(new BigDecimal("8.00")));

        mvc.perform(as(post("/api/v1/spam/send"), userId, "USER_PRO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ownSendBody(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SENT")));

        verify(users).debit(eq(userId), any());
    }

    @Test
    void clientCannotSpendSomeoneElsesBalance() throws Exception {
        mvc.perform(as(post("/api/v1/spam/send"), UUID.randomUUID(), "USER_PRO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(sendBody(1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        verify(users, never()).debit(any(), any());
    }

    @Test
    void freeUserHasLowerMessageLimit() throws Exception {
        mvc.perform(as(post("/api/v1/spam/send"), userId, "USER_FREE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ownSendBody(11))))
                .andExpect(status().isForbidden());
        verify(catalog, never()).get(any());

        receiverStatus = 200;
        priced("1.0000");
        when(users.debit(eq(userId), any()))
                .thenReturn(new UserClient.BalanceDto(new BigDecimal("0.00")));
        mvc.perform(as(post("/api/v1/spam/send"), userId, "USER_FREE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ownSendBody(10))))
                .andExpect(status().isOk());
    }

    @Test
    void buyOperatorManagesProxiesButCannotSend() throws Exception {
        UUID operator = UUID.randomUUID();

        mvc.perform(as(post("/api/v1/spam/send"), operator, "BUY_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ownSendBody(1))))
                .andExpect(status().isForbidden());

        mvc.perform(as(post("/api/v1/proxy"), operator, "BUY_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "host", "10.0.0.20", "port", 3128,
                                "protocol", "HTTP", "status", "ACTIVE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void clientsAndVictimsCannotTouchProxies() throws Exception {
        for (String role : new String[] {"USER_PRO", "USER_FREE", "VICTIM"}) {
            mvc.perform(as(get("/api/v1/proxy"), userId, role))
                    .andExpect(status().isForbidden());
            mvc.perform(as(post("/api/v1/proxy"), userId, role)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "host", "10.0.0.21", "port", 3128,
                                    "protocol", "HTTP", "status", "ACTIVE"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void clientSeesOnlyOwnSpamLog() throws Exception {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String mine = createLog(me, "mine@example.com");
        String theirs = createLog(other, "theirs@example.com");

        mvc.perform(as(get("/api/v1/spam-log/{id}", mine), me, "USER_PRO"))
                .andExpect(status().isOk());
        mvc.perform(as(get("/api/v1/spam-log/{id}", theirs), me, "USER_PRO"))
                .andExpect(status().isForbidden());

        mvc.perform(as(get("/api/v1/spam-log").param("size", "50"), me, "USER_PRO"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(me.toString())));

        mvc.perform(as(get("/api/v1/spam-log/feed").param("size", "50"), me, "USER_FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id", is(mine)));
        mvc.perform(as(get("/api/v1/spam-log/feed").param("cursor", mine), me, "USER_FREE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void onlySupervisorEditsSpamLog() throws Exception {
        UUID me = UUID.randomUUID();
        String mine = createLog(me, "own@example.com");

        mvc.perform(as(delete("/api/v1/spam-log/{id}", mine), me, "USER_PRO"))
                .andExpect(status().isForbidden());
        mvc.perform(as(patch("/api/v1/spam-log/{id}", mine), me, "USER_PRO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SENT\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/spam-log"), me, "BUY_OPERATOR"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTokenIsRejectedButReceiverIsPublic() throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        anonymous.perform(get("/api/v1/proxy"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status", is(401)));
        anonymous.perform(get("/api/v1/spam-log").header(HttpHeaders.AUTHORIZATION, "Bearer broken"))
                .andExpect(status().isUnauthorized());
        anonymous.perform(post("/api/v1/test-receiver/inbox")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
