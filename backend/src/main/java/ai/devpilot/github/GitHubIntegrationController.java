package ai.devpilot.github;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/integrations/github")
public class GitHubIntegrationController {
    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationController.class);
    private final GitHubIntegrationService service;
    private final GitHubRepositoryService repositories;
    private final String frontendUrl;
    public GitHubIntegrationController(GitHubIntegrationService service, GitHubRepositoryService repositories,
            @Value("${devpilot.oauth.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.service = service; this.repositories = repositories; this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }
    @PostMapping("/connect")
    public ResponseEntity<Map<String, String>> connect(HttpServletRequest request) {
        try {
            log.info("GitHub App installation flow started");
            return ResponseEntity.ok(Map.of("installationUrl", service.begin(request)));
        } catch (RuntimeException ex) {
            log.warn("GitHub App installation flow could not start");
            return ResponseEntity.status(503).body(Map.of("error", "github_connection_unavailable"));
        }
    }
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(HttpServletRequest request, @RequestParam(required = false) String state,
            @RequestParam(name = "installation_id", required = false) Long installationId,
            @RequestParam(name = "setup_action", required = false) String action) {
        log.info("GitHub App setup callback received");
        try {
            service.complete(request, state, installationId, action);
            log.info("GitHub App setup callback completed");
            return ResponseEntity.status(303).location(URI.create(frontendUrl + "/settings?github=connected")).build();
        } catch (ResponseStatusException ex) {
            log.warn("GitHub App setup callback rejected with HTTP status {}", ex.getStatusCode().value());
            return callbackFailure();
        } catch (RuntimeException ex) {
            log.error("GitHub App setup callback failed ({})", ex.getClass().getSimpleName());
            return callbackFailure();
        }
    }
    private ResponseEntity<Void> callbackFailure() {
        return ResponseEntity.status(303).location(URI.create(frontendUrl + "/settings?github=error&code=callback_failed")).build();
    }
    @GetMapping
    public GitHubIntegrationService.Status status(HttpServletRequest request) { return service.status(request); }
    @GetMapping("/repositories")
    public java.util.List<GitHubRepositoryService.RepositoryStatus> repositories(HttpServletRequest request) {
        return repositories.list(request);
    }
    @PutMapping("/repositories/{repositoryId}/access")
    public GitHubRepositoryService.RepositoryStatus setRepositoryAccess(HttpServletRequest request,
            @PathVariable long repositoryId, @RequestBody RepositoryAccessRequest body) {
        return repositories.setAgentAccess(request, repositoryId, body.enabled());
    }
    public record RepositoryAccessRequest(boolean enabled) {}
    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(HttpServletRequest request) { service.disconnect(request); return ResponseEntity.noContent().build(); }
}
