package ai.devpilot.prediction;

import java.time.LocalDateTime;
import java.util.Map;

public record PredictionHistoryItem(
        Long id,
        String title,
        String body,
        String label,
        Map<String, Double> probabilities,
        LocalDateTime createdAt) {
}
