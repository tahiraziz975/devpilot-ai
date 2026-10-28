package ai.devpilot.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MlPredictionClientTest {
    @Test
    void sendsJsonBodyToPythonAndReturnsItsPrediction() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> receivedBody = new AtomicReference<>();
        server.createContext("/predict", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"label\":\"bug\",\"probabilities\":{\"bug\":0.9,\"feature\":0.1}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            MlPredictionClient client = new MlPredictionClient(
                    new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort());
            PredictionResponse response = client.predict(new PredictionRequest("App crashes", "On startup"));

            assertThat(receivedBody.get()).isEqualTo("{\"title\":\"App crashes\",\"body\":\"On startup\"}");
            assertThat(response.label()).isEqualTo("bug");
            assertThat(response.probabilities()).containsEntry("bug", 0.9).containsEntry("feature", 0.1);
        } finally {
            server.stop(0);
        }
    }
}
