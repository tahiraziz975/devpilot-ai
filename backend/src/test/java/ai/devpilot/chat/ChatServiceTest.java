package ai.devpilot.chat;

import ai.devpilot.github.GitHubAppClient;
import ai.devpilot.github.GitHubRepositoryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatServiceTest {
    @Test
    void greetingUsesGeneralChatWithoutOpeningRepositorySession() {
        GroqChatClient client = mock(GroqChatClient.class);
        GitHubRepositoryService repositories = mock(GitHubRepositoryService.class);
        ChatService service = new ChatService(client, repositories);
        when(client.respondGeneral("hi")).thenReturn("Hi! How can I help?");

        String frontendTranscript = "Continue this conversation as DevPilot AI, a coding and development assistant. Be clear and practical.\n\n"
                + "Conversation so far:\nDevPilot AI: I can help debug your project code.\nUser: hi";
        ChatResponse response = service.reply(new ChatRequest(frontendTranscript), new MockHttpServletRequest());

        assertThat(response.reply()).isEqualTo("Hi! How can I help?");
        verify(client).respondGeneral("hi");
        verifyNoInteractions(repositories);
    }

    @Test
    void unrelatedQuestionDoesNotInheritAnEarlierProjectConversation() {
        GroqChatClient client = mock(GroqChatClient.class);
        GitHubRepositoryService repositories = mock(GitHubRepositoryService.class);
        ChatService service = new ChatService(client, repositories);
        when(client.respondGeneral("What is HTTP?")).thenReturn("HTTP is the protocol used for web requests.");
        String transcript = "Continue this conversation as DevPilot AI, a coding assistant.\nConversation so far:\n"
                + "User: Why is my DELETE endpoint not working in dockerization repo?\n"
                + "DevPilot AI: I found a controller.\nUser: What is HTTP?";

        ChatResponse response = service.reply(new ChatRequest(transcript), new MockHttpServletRequest());

        assertThat(response.reply()).contains("protocol");
        verify(client).respondGeneral("What is HTTP?");
        verifyNoInteractions(repositories);
    }

    @Test
    void preloadsCodeForFilenameFreeQuestionAndMatchesRepositoryByShortName() {
        GroqChatClient client = mock(GroqChatClient.class);
        GitHubRepositoryService repositories = mock(GitHubRepositoryService.class);
        ChatService service = new ChatService(client, repositories);
        var session = new GitHubRepositoryService.AgentSession("token", Map.of(
                "octo/dockerization", new GitHubRepositoryService.AgentRepository(1L, "octo/dockerization", "main"),
                "acme/other", new GitHubRepositoryService.AgentRepository(2L, "acme/other", "main")));
        when(repositories.openAgentSession(any(HttpServletRequest.class))).thenReturn(session);
        when(repositories.findCodeMatches(session, "octo/dockerization", "delete")).thenReturn(List.of(
                new GitHubAppClient.CodeMatch("src/StudentController.java", "// @DeleteMapping(\"/students/{id}\")")));
        when(repositories.readFile(session, "octo/dockerization", "src/StudentController.java"))
                .thenReturn("// @DeleteMapping(\"/students/{id}\")\npublic void delete() {}");
        when(client.respond(anyString(), anyList(), any(GroqChatClient.ToolExecutor.class), anyString()))
                .thenReturn("The DELETE endpoint is commented out.");

        String message = "Why is my DELETE endpoint not working in dockerization repo?";
        ChatResponse response = service.reply(new ChatRequest(message), new MockHttpServletRequest());

        assertThat(response.reply()).contains("commented out");
        verify(repositories).findCodeMatches(session, "octo/dockerization", "delete");
        verify(repositories).readFile(session, "octo/dockerization", "src/StudentController.java");
        verify(client).respond(eq(message), anyList(),
                any(GroqChatClient.ToolExecutor.class), org.mockito.ArgumentMatchers.contains("<repository-file"));
    }

    @Test
    void keepsProjectContextForAReferentialFollowUpAfterAProjectQuestion() {
        GroqChatClient client = mock(GroqChatClient.class);
        GitHubRepositoryService repositories = mock(GitHubRepositoryService.class);
        ChatService service = new ChatService(client, repositories);
        var session = new GitHubRepositoryService.AgentSession("token", Map.of(
                "octo/dockerization", new GitHubRepositoryService.AgentRepository(1L, "octo/dockerization", "main")));
        when(repositories.openAgentSession(any(HttpServletRequest.class))).thenReturn(session);
        when(repositories.findCodeMatches(session, "octo/dockerization", "delete")).thenReturn(List.of(
                new GitHubAppClient.CodeMatch("src/StudentController.java", "// @DeleteMapping")));
        when(repositories.readFile(session, "octo/dockerization", "src/StudentController.java"))
                .thenReturn("// @DeleteMapping(\"/students/{id}\")");
        when(client.respond(anyString(), anyList(), any(GroqChatClient.ToolExecutor.class), anyString()))
                .thenReturn("Yes, the mapping is commented out.");

        String message = "Continue this conversation as DevPilot AI.\nConversation so far:\n"
                + "User: Investigate why DELETE endpoint is not working in dockerization repo.\n"
                + "DevPilot AI: I will inspect the endpoint.\nUser: Is it commented out?";
        ChatResponse response = service.reply(new ChatRequest(message), new MockHttpServletRequest());

        assertThat(response.reply()).contains("commented out");
        verify(repositories).findCodeMatches(session, "octo/dockerization", "delete");
    }
}
