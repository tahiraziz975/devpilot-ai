package ai.devpilot.github;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class GitHubRepositoryService {
    private final GitHubAppClient github;
    private final GitHubIntegrationRepository integrations;
    private final GitHubRepositoryAccessRepository access;
    private final String frontendUrl;

    public GitHubRepositoryService(GitHubAppClient github, GitHubIntegrationRepository integrations,
            GitHubRepositoryAccessRepository access,
            @org.springframework.beans.factory.annotation.Value("${devpilot.oauth.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.github = github; this.integrations = integrations; this.access = access;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional(readOnly = true)
    public List<RepositoryStatus> list(HttpServletRequest request) {
        Long userId = GitHubIntegrationService.authenticatedUser(request);
        GitHubIntegration integration = integrations.findByUserId(userId).orElse(null);
        if (integration == null || !"ACTIVE".equals(integration.getStatus())) return List.of();
        String token = github.createInstallationToken(integration.getInstallationId());
        Map<Long, GitHubRepositoryAccess> saved = access.findByUserId(userId).stream()
            .collect(Collectors.toMap(GitHubRepositoryAccess::getRepositoryId, item -> item));
        return github.listInstallationRepositories(token).stream()
            .map(repo -> new RepositoryStatus(repo.id(), repo.name(), repo.fullName(), repo.privateRepository(),
                "READ_ONLY", saved.containsKey(repo.id()) && saved.get(repo.id()).isEnabled()))
            .toList();
    }

    @Transactional
    public RepositoryStatus setAgentAccess(HttpServletRequest request, long repositoryId, boolean enabled) {
        Long userId = GitHubIntegrationService.authenticatedUser(request);
        requireTrustedOrigin(request);
        GitHubIntegration integration = integrations.findByUserId(userId)
            .filter(item -> "ACTIVE".equals(item.getStatus()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connect a GitHub App before selecting repositories"));
        String token = github.createInstallationToken(integration.getInstallationId());
        GitHubAppClient.Repository repo = github.listInstallationRepositories(token).stream()
            .filter(item -> item.id() != null && item.id() == repositoryId).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository is not available to this GitHub App"));
        GitHubRepositoryAccess selected = access.findByUserIdAndRepositoryId(userId, repositoryId).orElse(null);
        if (selected == null) access.save(new GitHubRepositoryAccess(userId, repositoryId, repo.fullName(), enabled));
        else selected.update(repo.fullName(), enabled);
        return new RepositoryStatus(repo.id(), repo.name(), repo.fullName(), repo.privateRepository(), "READ_ONLY", enabled);
    }

    @Transactional(readOnly = true)
    public AgentSession openAgentSession(HttpServletRequest request) {
        Long userId = GitHubIntegrationService.authenticatedUser(request);
        GitHubIntegration integration = integrations.findByUserId(userId).orElse(null);
        if (integration == null || !"ACTIVE".equals(integration.getStatus())) return new AgentSession("", Map.of());
        List<GitHubRepositoryAccess> enabled = access.findByUserId(userId).stream().filter(GitHubRepositoryAccess::isEnabled).toList();
        if (enabled.isEmpty()) return new AgentSession("", Map.of());
        String token = github.createInstallationToken(integration.getInstallationId());
        Map<Long, GitHubAppClient.Repository> current = github.listInstallationRepositories(token).stream()
            .filter(repo -> repo.id() != null).collect(Collectors.toMap(GitHubAppClient.Repository::id, repo -> repo));
        Map<String, AgentRepository> selected = enabled.stream().map(item -> current.get(item.getRepositoryId()))
            .filter(java.util.Objects::nonNull)
            .map(repo -> new AgentRepository(repo.id(), repo.fullName(),
                repo.defaultBranch() == null || repo.defaultBranch().isBlank() ? "main" : repo.defaultBranch()))
            .collect(Collectors.toMap(AgentRepository::fullName, repo -> repo));
        return new AgentSession(token, selected);
    }

    public String listFiles(AgentSession session, String fullName, String directory) {
        AgentRepository repo = authorized(session, fullName);
        List<GitHubAppClient.TreeEntry> files = github.listRepositoryFiles(session.token(), repo.fullName(), repo.defaultBranch(), directory);
        if (files.isEmpty()) return "No files found in that directory.";
        return files.stream().map(file -> "tree".equals(file.type()) ? file.path() + "/ (directory)"
                : file.path() + (file.size() > 0 ? " (" + file.size() + " bytes)" : ""))
            .collect(Collectors.joining("\n"));
    }

    public List<String> findFiles(AgentSession session, String fullName, String fileName) {
        AgentRepository repo = authorized(session, fullName);
        return github.findRepositoryFiles(session.token(), repo.fullName(), repo.defaultBranch(), fileName).stream()
            .map(GitHubAppClient.TreeEntry::path).toList();
    }

    public String searchCode(AgentSession session, String fullName, String query) {
        List<GitHubAppClient.CodeMatch> matches = findCodeMatches(session, fullName, query);
        if (matches.isEmpty()) return "No code matches found. Try a related class, method, endpoint, or domain term.";
        return matches.stream().map(match -> match.path() + (match.fragment().isBlank() ? "" : "\n" + match.fragment()))
            .collect(Collectors.joining("\n---\n"));
    }

    public List<GitHubAppClient.CodeMatch> findCodeMatches(AgentSession session, String fullName, String query) {
        AgentRepository repo = authorized(session, fullName);
        if (query == null || query.isBlank() || query.length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must be between 1 and 120 characters");
        return github.searchRepositoryCode(session.token(), repo.fullName(), query);
    }

    public String readFile(AgentSession session, String fullName, String path) {
        AgentRepository repo = authorized(session, fullName);
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..") || path.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid repository file path");
        String directory = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        boolean exists = github.listRepositoryFiles(session.token(), repo.fullName(), repo.defaultBranch(), directory)
            .stream().anyMatch(file -> "blob".equals(file.type()) && file.path().equals(path));
        if (!exists) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File is not present in the selected repository");
        String content = github.readRepositoryFile(session.token(), repo.fullName(), path, repo.defaultBranch());
        return content.length() > 16_000 ? content.substring(0, 16_000) + "\n[File truncated for the chat context.]" : content;
    }

    private AgentRepository authorized(AgentSession session, String fullName) {
        AgentRepository repo = session.repositories().get(fullName);
        if (repo == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The agent does not have access to this repository");
        return repo;
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        URI frontend = URI.create(frontendUrl);
        String expected = frontend.getScheme() + "://" + frontend.getAuthority();
        if (origin == null || !expected.equalsIgnoreCase(origin))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin validation failed");
    }

    public record RepositoryStatus(Long id, String name, String fullName, boolean privateRepository,
            String appAccess, boolean agentEnabled) {}
    public record AgentRepository(Long id, String fullName, String defaultBranch) {}
    public record AgentSession(String token, Map<String, AgentRepository> repositories) {}
}
