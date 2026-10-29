package ai.devpilot.auth.dto;

public record AuthResponse(
    Long id,
    String profileId,
    String fullName,
    String email
) {}