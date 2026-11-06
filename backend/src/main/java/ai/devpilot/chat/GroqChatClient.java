package ai.devpilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class GroqChatClient {
    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);
    private static final String INSTRUCTIONS = "You are DevPilot, a read-only coding assistant. "
            + "Explain code and suggest fixes in chat. Never claim to modify files, commit, push, "
            + "or open pull requests. No repository tools are available in this integration.";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI chatUri;
    private final String apiKey;
    private final String model;

    public GroqChatClient(ObjectMapper objectMapper,
                          @Value("${groq.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                          @Value("${groq.api-key:}") String apiKey,
                          @Value("${groq.model:openai/gpt-oss-20b}") String model) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
        this.chatUri = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
    }

    public String respond(String message) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new GroqConfigurationException("Set GROQ_API_KEY in the backend environment");
        }
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("model", model);
            payload.put("stream", false);
            var messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", INSTRUCTIONS);
            messages.addObject().put("role", "user").put("content", message);

            HttpRequest request = HttpRequest.newBuilder(chatUri)
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = response.body() == null ? "" : response.body();
                if (errorBody.length() > 1000) errorBody = errorBody.substring(0, 1000) + "…";
                log.warn("Groq Chat Completions API returned HTTP {}: {}", response.statusCode(), errorBody);
                throw new GroqUpstreamException("Groq returned HTTP " + response.statusCode());
            }
            JsonNode content = objectMapper.readTree(response.body())
                    .path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new GroqUpstreamException("Groq response did not contain message content");
            }
            return content.asText();
        } catch (GroqConfigurationException | GroqUpstreamException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GroqUpstreamException("Interrupted while waiting for Groq", exception);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Could not complete the Groq request: {}", exception.toString());
            throw new GroqUpstreamException("Could not complete the Groq request", exception);
        }
    }
}
