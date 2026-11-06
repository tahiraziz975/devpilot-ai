package ai.devpilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqChatClientTest {
    @Test
    void sendsAuthenticatedChatRequestAndReturnsAssistantContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/openai/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] result = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Try checking the null value.\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1",
                    "test-secret", "test-model");
            assertThat(client.respond("Explain this code")).isEqualTo("Try checking the null value.");
            assertThat(authorization.get()).isEqualTo("Bearer test-secret");
            assertThat(body.get()).contains("\"model\":\"test-model\"")
                    .contains("\"stream\":false")
                    .contains("\"role\":\"system\"")
                    .contains("\"role\":\"user\"")
                    .contains("\"content\":\"Explain this code\"")
                    .doesNotContain("test-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClearlyWhenApiKeyIsMissing() {
        var client = new GroqChatClient(new ObjectMapper(), "https://api.groq.com/openai/v1", "", "test-model");
        assertThatThrownBy(() -> client.respond("hello"))
                .isInstanceOf(GroqConfigurationException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }

    @Test
    void reportsGroqHttpErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openai/v1/chat/completions", exchange -> {
            byte[] result = "{\"error\":{\"message\":\"rate limit exceeded\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, result.length);
            exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1",
                    "test-secret", "test-model");
            assertThatThrownBy(() -> client.respond("hello"))
                    .isInstanceOf(GroqUpstreamException.class)
                    .hasMessageContaining("HTTP 429");
        } finally {
            server.stop(0);
        }
    }
}
