package ai.devpilot.backend;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PredictionRequest(
        @NotNull(message = "title is required")
        @Size(max = 10_000, message = "title must be at most 10000 characters")
        String title,
        @NotNull(message = "body is required")
        @Size(max = 500_000, message = "body must be at most 500000 characters")
        String body
) {
}
