package ai.devpilot.backend;

import java.util.Map;

public record PredictionResponse(String label, Map<String, Double> probabilities) {
}
