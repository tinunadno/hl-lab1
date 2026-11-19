package com.example.spamer.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.spamer.dispatch.service.MessageSender.SendResult;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

class MessageSenderTest {

    @Test
    void successKeepsStatusAndFlattensTheBody() throws Exception {
        HttpServer server = server(200, "  line\n two  ");
        try {
            SendResult result = sender(url(server)).send("a@example.com", "hello", 1);

            assertThat(result.ok()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.responseSnippet()).isEqualTo("line two");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nullMessageIsSentAsEmptyText() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inbox", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(reply);
            }
        });
        server.start();
        try {
            sender(url(server)).send("a@example.com", null, 4);

            assertThat(body.get()).contains("\"message\":\"\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpErrorIsNotASuccessfulSend() throws Exception {
        HttpServer server = server(503, "down");
        try {
            SendResult result = sender(url(server)).send("a@example.com", "hello", 2);

            assertThat(result.ok()).isFalse();
            assertThat(result.httpStatus()).isEqualTo(503);
            assertThat(result.responseSnippet()).isEqualTo("down");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void longBodyIsCutToTwoHundredCharacters() throws Exception {
        HttpServer server = server(200, "x".repeat(250));
        try {
            SendResult result = sender(url(server)).send("a@example.com", "hello", 3);

            assertThat(result.responseSnippet()).hasSize(200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingBodyStaysNull() throws Exception {
        HttpServer server = server(204, null);
        try {
            SendResult result = sender(url(server)).send("a@example.com", "hello", 5);

            assertThat(result.ok()).isTrue();
            assertThat(result.responseSnippet()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableReceiverIsAFailedSend() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        SendResult result = sender("http://127.0.0.1:" + port + "/inbox")
                .send("a@example.com", "hello", 6);

        assertThat(result.ok()).isFalse();
        assertThat(result.httpStatus()).isZero();
        assertThat(result.responseSnippet()).isNotBlank();
    }

    private static MessageSender sender(String target) {
        Environment env = mock(Environment.class);
        when(env.getProperty("spamer.sender.target-url")).thenReturn(target);
        return new MessageSender(RestClient.builder().build(), env);
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/inbox";
    }

    private static HttpServer server(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/inbox", exchange -> {
            if (body == null) {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
                return;
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        return server;
    }
}
