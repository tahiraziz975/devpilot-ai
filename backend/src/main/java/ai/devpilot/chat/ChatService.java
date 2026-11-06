package ai.devpilot.chat;

import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final GroqChatClient client;

    public ChatService(GroqChatClient client) { this.client = client; }

    public ChatResponse reply(ChatRequest request) {
        return new ChatResponse(client.respond(request.message().trim()));
    }
}
