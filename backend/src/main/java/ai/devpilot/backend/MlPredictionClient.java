package ai.devpilot.backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class MlPredictionClient {
    private static final Logger logger = LoggerFactory.getLogger(MlPredictionClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI predictionUri;

    public MlPredictionClient(ObjectMapper objectMapper,
                              @Value("${devpilot.ml.base-url:http://127.0.0.1:8000}") String baseUrl) {
        // Uvicorn serves plain HTTP/1.1 here; avoid Java's h2c upgrade attempt.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.objectMapper = objectMapper;
        this.predictionUri = URI.create(baseUrl.replaceAll("/+$", "") + "/predict");
        logger.info("DevPilot ML prediction endpoint: {}", predictionUri);
    }

    public PredictionResponse predict(PredictionRequest request) {
        final byte[] jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize prediction request", exception);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(predictionUri)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RestClientException("ML service returned HTTP " + response.statusCode()
                        + ": " + response.body());
            }
            return objectMapper.readValue(response.body(), PredictionResponse.class);
        } catch (IOException exception) {
            throw new RestClientException("Could not communicate with the ML prediction service", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Interrupted while waiting for the ML prediction service", exception);
        }
    }
}
