package ai.devpilot.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "message is required")
        @Size(max = 12000, message = "message must be at most 12000 characters")
        String message) {
}
