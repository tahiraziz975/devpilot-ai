package ai.devpilot.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GitHubRepositoryServiceTest {
    private final GitHubAppClient github = mock(GitHubAppClient.class);
    private final GitHubIntegrationRepository integrations = mock(GitHubIntegrationRepository.class);
    private final GitHubRepositoryAccessRepository access = mock(GitHubRepositoryAccessRepository.class);
    private final GitHubRepositoryService service = new GitHubRepositoryService(github, integrations, access, "http://localhost:5173");

    @Test void listingReportsAppAccessAndSeparateAgentOptIn() {
        MockHttpServletRequest request = authenticated("http://localhost:5173");
        when(integrations.findByUserId(9L)).thenReturn(Optional.of(integration()));
        when(access.findByUserId(9L)).thenReturn(List.of(new GitHubRepositoryAccess(9L, 11L, "octo/app", true)));
        when(github.createInstallationToken(22L)).thenReturn("short-lived-token");
        when(github.listInstallationRepositories("short-lived-token")).thenReturn(List.of(
            new GitHubAppClient.Repository(11L, "app", "octo/app", true, "main", Map.of("contents", "read")),
            new GitHubAppClient.Repository(12L, "docs", "octo/docs", false, "main", Map.of("contents", "read"))));
        var repositories = service.list(request);
        assertEquals(2, repositories.size());
        assertEquals("READ_ONLY", repositories.get(0).appAccess());
        assertTrue(repositories.get(0).agentEnabled());
        assertFalse(repositories.get(1).agentEnabled());
    }

    @Test void agentSessionContainsOnlyEnabledRepositoriesStillInstalled() {
        MockHttpServletRequest request = authenticated("http://localhost:5173");
        when(integrations.findByUserId(9L)).thenReturn(Optional.of(integration()));
        when(access.findByUserId(9L)).thenReturn(List.of(
            new GitHubRepositoryAccess(9L, 11L, "octo/app", true),
            new GitHubRepositoryAccess(9L, 12L, "octo/docs", false)));
        when(github.createInstallationToken(22L)).thenReturn("short-lived-token");
        when(github.listInstallationRepositories("short-lived-token")).thenReturn(List.of(
            new GitHubAppClient.Repository(11L, "app", "octo/app", true, "main", Map.of()),
            new GitHubAppClient.Repository(12L, "docs", "octo/docs", false, "main", Map.of())));
        var session = service.openAgentSession(request);
        assertEquals("short-lived-token", session.token());
        assertEquals(List.of("octo/app"), List.copyOf(session.repositories().keySet()));
    }

    @Test void changingAgentAccessRequiresTrustedOrigin() {
        MockHttpServletRequest request = authenticated("https://attacker.example");
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> service.setAgentAccess(request, 11L, true)).getStatusCode().value());
        verifyNoInteractions(github, access);
    }

    private MockHttpServletRequest authenticated(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", origin);
        request.getSession(true).setAttribute(GitHubIntegrationService.USER_ID, 9L);
        return request;
    }
    private GitHubIntegration integration() { return new GitHubIntegration(9L, 22L, 33L, "octo", "Organization"); }
}
