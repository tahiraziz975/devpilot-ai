package ai.devpilot.chat;

import ai.devpilot.github.GitHubRepositoryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final GroqChatClient client;
    private final GitHubRepositoryService repositories;

    public ChatService(GroqChatClient client, GitHubRepositoryService repositories) {
        this.client = client; this.repositories = repositories;
    }

    public ChatResponse reply(ChatRequest request, HttpServletRequest servletRequest) {
        GitHubRepositoryService.AgentSession session = repositories.openAgentSession(servletRequest);
        List<String> names = List.copyOf(session.repositories().keySet());
        return new ChatResponse(client.respond(request.message().trim(), names, (tool, args) -> {
            if (session.repositories().isEmpty()) return "No repositories are enabled for agent access. Select one in Settings → GitHub first.";
            String repo = args.path("repository").asText("");
            if ("list_repository_files".equals(tool))
                return repositories.listFiles(session, repo, args.path("directory").asText(""));
            if ("read_repository_file".equals(tool))
                return repositories.readFile(session, repo, args.path("path").asText(""));
            return "Unsupported repository tool.";
        }));
    }
}
