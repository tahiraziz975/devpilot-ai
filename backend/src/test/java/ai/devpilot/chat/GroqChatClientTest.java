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
    void generalChatOmitsRepositoryContextAndTools() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/openai/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi! How can I help?\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "test-secret", "test-model");
            assertThat(client.respondGeneral("hi")).isEqualTo("Hi! How can I help?");
            assertThat(body.get()).contains("Answer general questions directly")
                    .contains("\"content\":\"hi\"")
                    .doesNotContain("repository is enabled", "\"tools\"");
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
    void searchesAndReadsRelevantCodeForQuestionWithoutAFileName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        AtomicReference<String> initialBody = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/openai/v1/chat/completions", exchange -> {
            int callNumber = calls.updateAndGet(value -> value + 1);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (callNumber == 1) initialBody.set(body);
            String response;
            if (callNumber <= 2) {
                var arguments = callNumber == 1
                        ? mapper.createObjectNode().put("repository", "octo/app").put("query", "DELETE student")
                        : mapper.createObjectNode().put("repository", "octo/app").put("path", "StudentController.java");
                String toolName = callNumber == 1 ? "search_repository_code" : "read_repository_file";
                var toolCall = mapper.createObjectNode().put("id", "discovery-" + callNumber).put("type", "function");
                toolCall.putObject("function").put("name", toolName).put("arguments", mapper.writeValueAsString(arguments));
                var message = mapper.createObjectNode().put("role", "assistant").putNull("content");
                message.putArray("tool_calls").add(toolCall);
                var root = mapper.createObjectNode();
                root.putArray("choices").addObject().set("message", message);
                response = root.toString();
            } else {
                var root = mapper.createObjectNode();
                root.putArray("choices").addObject().putObject("message").put("role", "assistant")
                        .put("content", "The DELETE mapping is present in StudentController.java.");
                response = root.toString();
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "test-secret", "test-model");
            String answer = client.respond("Why is my DELETE endpoint not working?", List.of("octo/app"), (name, args) -> {
                assertThat(args.path("repository").asText()).isEqualTo("octo/app");
                if ("search_repository_code".equals(name)) {
                    assertThat(args.path("query").asText()).contains("DELETE");
                    return "StudentController.java\n@DeleteMapping(\"/students/{id}\")";
                }
                assertThat(name).isEqualTo("read_repository_file");
                assertThat(args.path("path").asText()).isEqualTo("StudentController.java");
                return "@DeleteMapping(\"/students/{id}\") public void delete(...) { ... }";
            });
            assertThat(answer).contains("StudentController.java");
            assertThat(calls.get()).isEqualTo(3);
            assertThat(initialBody.get()).contains("search_repository_code")
                    .contains("without naming a file")
                    .contains("search code");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotOfferRepositoryToolsWhenNoRepositoryIsEnabled() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/openai/v1/chat/completions", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"No repository is enabled. Select the repository in GitHub settings, then retry.\"}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "test-secret", "test-model");
            String answer = client.respond("Why is my DELETE endpoint not working?", List.of(), (name, args) -> {
                throw new AssertionError("Repository tools must not run without an enabled repository");
            });
            assertThat(answer).contains("No repository is enabled");
            assertThat(body.get()).doesNotContain("\"tools\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void returnsCollectedFilePathsWhenFinalizationFailsWithToolChoiceError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<Integer> calls = new AtomicReference<>(0);
        AtomicReference<String> finalBody = new AtomicReference<>();
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/openai/v1/chat/completions", exchange -> {
            int callNumber = calls.updateAndGet(value -> value + 1);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response;
            if (callNumber <= 5) {
                var arguments = mapper.createObjectNode().put("repository", "octo/app");
                if (callNumber == 1) arguments.put("directory", "");
                else arguments.put("path", "src/StudentController.java");
                String name = callNumber == 1 ? "list_repository_files" : "read_repository_file";
                var toolCall = mapper.createObjectNode().put("id", "loop-" + callNumber).put("type", "function");
                toolCall.putObject("function").put("name", name).put("arguments", mapper.writeValueAsString(arguments));
                var message = mapper.createObjectNode().put("role", "assistant").putNull("content");
                message.putArray("tool_calls").add(toolCall);
                var root = mapper.createObjectNode();
                root.putArray("choices").addObject().set("message", message);
                response = root.toString();
            } else {
                finalBody.set(body);
                byte[] error = "{\"error\":{\"message\":\"Tool choice is none, but model called a tool\",\"code\":\"tool_use_failed\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(400, error.length);
                exchange.getResponseBody().write(error);
                exchange.close();
                return;
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var client = new GroqChatClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1", "test-secret", "test-model");
            String answer = client.respond("Why is my DELETE endpoint not working?", List.of("octo/app"), (name, args) -> {
                if ("read_repository_file".equals(name)) return "@DeleteMapping(\"/students/{id}\")";
                assertThat(name).isEqualTo("list_repository_files");
                return "src/StudentController.java";
            });
            assertThat(answer).contains("model did not complete its analysis")
                    .contains("StudentController.java");
            assertThat(calls.get()).isEqualTo(6);
            assertThat(finalBody.get()).doesNotContain("\"tools\"")
                    .contains("inspection-results")
                    .contains("@DeleteMapping");
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
                    .isInstanceOf(GroqRateLimitException.class)
                    .hasMessageContaining("token limit")
                    .extracting(exception -> ((GroqRateLimitException) exception).retryAfterSeconds())
                    .isEqualTo(30L);
        } finally {
            server.stop(0);
        }
    }
}
