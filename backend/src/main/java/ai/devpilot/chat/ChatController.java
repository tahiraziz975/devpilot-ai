package ai.devpilot.chat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) { this.chatService = chatService; }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null || session.getAttribute("devpilotUserId") == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to use DevPilot chat");
        }
        try {
            return chatService.reply(request);
        } catch (GroqConfigurationException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Groq is not configured; set GROQ_API_KEY on the backend", exception);
        } catch (GroqUpstreamException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Groq could not complete the request; check the backend log", exception);
        }
    }
}
