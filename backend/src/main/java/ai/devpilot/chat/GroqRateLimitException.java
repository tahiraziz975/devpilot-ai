package ai.devpilot.chat;

public class GroqRateLimitException extends GroqUpstreamException {
    private final long retryAfterSeconds;

    public GroqRateLimitException(long retryAfterSeconds) {
        super("Groq token limit reached");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }
}
