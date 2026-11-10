package ai.devpilot.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GitHubIntegrationServiceTest {
    private final GitHubAppClient github = mock(GitHubAppClient.class);
    private final GitHubIntegrationRepository repository = mock(GitHubIntegrationRepository.class);
    private final GitHubIntegrationService service = new GitHubIntegrationService(github, repository, "devpilot-ai", "http://localhost:5173");

    @Test void unauthenticatedConnectIsRejected() {
        assertEquals(401, assertThrows(ResponseStatusException.class,
            () -> service.begin(new MockHttpServletRequest())).getStatusCode().value());
    }

    @Test void callbackRequiresOneTimeSessionState() {
        MockHttpServletRequest request = authenticated(9L);
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> service.complete(request, "forged", 22L, "install")).getStatusCode().value());
        verifyNoInteractions(github);
    }

    @Test void callbackVerifiesInstallationAndPreventsCrossUserLink() {
        MockHttpServletRequest request = authenticated(9L);
        String state = stateFor(request);
        when(github.verifyInstallation(22L)).thenReturn(new GitHubAppClient.Installation(22L,
            new GitHubAppClient.Account(44L, "octocat", "User"), readOnlyPermissions()));
        when(repository.findByUserId(9L)).thenReturn(java.util.Optional.empty());
        when(repository.findByInstallationId(22L)).thenReturn(java.util.Optional.of(
            new GitHubIntegration(10L, 22L, 44L, "octocat", "User")));
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> service.complete(request, state, 22L, "install")).getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test void callbackPersistsVerifiedMetadataWithoutCredentials() {
        MockHttpServletRequest request = authenticated(9L);
        String state = stateFor(request);
        when(github.verifyInstallation(22L)).thenReturn(new GitHubAppClient.Installation(22L,
            new GitHubAppClient.Account(44L, "octocat", "User"), readOnlyPermissions()));
        when(repository.findByUserId(9L)).thenReturn(java.util.Optional.empty());
        when(repository.findByInstallationId(22L)).thenReturn(java.util.Optional.empty());
        service.complete(request, state, 22L, "install");
        verify(repository).save(argThat(i -> i.getUserId().equals(9L)
            && i.getInstallationId().equals(22L) && i.getAccountLogin().equals("octocat")));
    }

    @Test void callbackRejectsInstallationWithBroaderThanReadOnlyPermissions() {
        MockHttpServletRequest request = authenticated(9L);
        String state = stateFor(request);
        when(github.verifyInstallation(22L)).thenReturn(new GitHubAppClient.Installation(22L,
            new GitHubAppClient.Account(44L, "octocat", "User"),
            java.util.Map.of("metadata", "read", "contents", "write")));
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> service.complete(request, state, 22L, "install")).getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test void stateIsConsumedEvenWhenInstallationVerificationFails() {
        MockHttpServletRequest request = authenticated(9L);
        String state = stateFor(request);
        when(github.verifyInstallation(22L)).thenThrow(new RuntimeException());
        assertThrows(RuntimeException.class, () -> service.complete(request, state, 22L, "install"));
        assertEquals(403, assertThrows(ResponseStatusException.class,
            () -> service.complete(request, state, 22L, "install")).getStatusCode().value());
    }

    private MockHttpServletRequest authenticated(long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://localhost:5173");
        request.getSession(true).setAttribute(GitHubIntegrationService.USER_ID, userId);
        return request;
    }
    private String stateFor(MockHttpServletRequest request) {
        String url = service.begin(request);
        return url.substring(url.indexOf("state=") + 6);
    }
    private java.util.Map<String, String> readOnlyPermissions() {
        return java.util.Map.of("metadata", "read", "contents", "read");
    }
}
