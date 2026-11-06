package ai.devpilot.chat;

public class GroqUpstreamException extends RuntimeException {
    public GroqUpstreamException(String message) { super(message); }
    public GroqUpstreamException(String message, Throwable cause) { super(message, cause); }
}
