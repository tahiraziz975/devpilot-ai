package ai.devpilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    void executesOnlyTheRepositoryToolRequestedThenReturnsTheFinalAnswer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        AtomicReference<String> firstBody = new AtomicReference<>();
        AtomicReference<String> thirdBody = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/openai/v1/chat/completions", exchange -> {
            int call = calls.updateAndGet(value -> value + 1);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (call == 1) firstBody.set(body);
            if (call == 3) thirdBody.set(body);
            String result;
            if (call == 1 || call == 2) {
                var arguments = call == 1
                        ? mapper.createObjectNode().put("repository", "octo/app").put("path", "README.md")
                        : mapper.createObjectNode().put("repository", "octo/app");
                String toolName = call == 1 ? "read_repository_file" : "list_repository_files";
                var toolCall = mapper.createObjectNode().put("id", "call-" + call).put("type", "function");
                toolCall.putObject("function").put("name", toolName).put("arguments", mapper.writeValueAsString(arguments));
                var message = mapper.createObjectNode().put("role", "assistant").putNull("content");
                message.putArray("tool_calls").add(toolCall);
                var root = mapper.createObjectNode();
                root.putArray("choices").addObject().set("message", message);
                result = root.toString();
            } else {
                var message = mapper.createObjectNode().put("role", "assistant").put("content", "The README describes the app.");
                var root = mapper.createObjectNode();
                root.putArray("choices").addObject().set("message", message);
                result = root.toString();
            }
            byte[] response = result.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1",
                    "test-secret", "test-model");
            var reply = client.respond("Summarize the README", List.of("octo/app"), (name, args) -> {
                assertThat(args.path("repository").asText()).isEqualTo("octo/app");
                if ("read_repository_file".equals(name)) {
                    assertThat(args.path("path").asText()).isEqualTo("README.md");
                    return "# DevPilot\nA sample app.";
                }
                assertThat(name).isEqualTo("list_repository_files");
                assertThat(args.path("directory").asText("")).isEmpty();
                return "README.md";
            });
            assertThat(reply).isEqualTo("The README describes the app.");
            assertThat(calls.get()).isEqualTo(3);
            var schema = new ObjectMapper().readTree(firstBody.get()).path("tools");
            var listFunction = java.util.stream.StreamSupport.stream(schema.spliterator(), false)
                    .filter(tool -> tool.path("function").path("name").asText().equals("list_repository_files"))
                    .findFirst().orElseThrow();
            assertThat(listFunction.path("function").path("parameters").path("required").toString()).doesNotContain("directory");
            assertThat(firstBody.get()).contains("list_repository_files");
            assertThat(thirdBody.get()).contains("\"tools\"");
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
