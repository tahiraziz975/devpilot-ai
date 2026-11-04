package ai.devpilot.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record UpdateThemeRequest(
        @NotBlank(message = "theme is required")
        @Pattern(regexp = "dark|light", message = "theme must be dark or light")
        String theme) {
}
