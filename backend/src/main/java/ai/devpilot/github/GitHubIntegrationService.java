package ai.devpilot.github;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.net.URI;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GitHubIntegrationService {
    static final String USER_ID = "devpilotUserId";
    static final String STATE = "devpilotGithubInstallState";
    static final String STATE_CREATED = "devpilotGithubInstallStateCreated";
    private final GitHubAppClient github;
    private final GitHubIntegrationRepository integrations;
    private final String appSlug;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public GitHubIntegrationService(GitHubAppClient github, GitHubIntegrationRepository integrations,
            @Value("${devpilot.github.app-slug:}") String appSlug,
            @Value("${devpilot.oauth.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.github = github; this.integrations = integrations; this.appSlug = appSlug;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public String begin(HttpServletRequest request) {
        Long userId = authenticatedUser(request);
        requireTrustedOrigin(request);
        if (appSlug.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub integration is not configured");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        HttpSession session = request.getSession(false);
        session.setAttribute(STATE, state);
        session.setAttribute(STATE_CREATED, System.currentTimeMillis());
        return "https://github.com/apps/" + appSlug + "/installations/new?state=" + state;
    }

    @Transactional
    public void complete(HttpServletRequest request, String state, Long installationId, String action) {
        Long userId = authenticatedUser(request);
        HttpSession session = request.getSession(false);
        Object expected = session.getAttribute(STATE);
        Object created = session.getAttribute(STATE_CREATED);
        session.removeAttribute(STATE); session.removeAttribute(STATE_CREATED);
        if (!(expected instanceof String s) || !(created instanceof Long time)
                || System.currentTimeMillis() - time > 600_000
                || state == null || !MessageDigest.isEqual(s.getBytes(), state.getBytes())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "GitHub setup validation failed");
        }
        if (installationId == null || installationId <= 0 || !("install".equals(action) || "update".equals(action))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid GitHub installation callback");
        }
        GitHubAppClient.Installation verified = github.verifyInstallation(installationId);
        if (verified == null || !installationId.equals(verified.id()) || verified.account() == null
                || verified.account().id() == null || verified.account().login() == null
                || verified.account().type() == null || !verified.hasReadOnlyCodePermissions()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub installation could not be verified");
        }
        GitHubIntegration existing = integrations.findByUserId(userId).orElse(null);
        GitHubIntegration owner = integrations.findByInstallationId(installationId).orElse(null);
        if (owner != null && !owner.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GitHub installation is already linked");
        }
        if (existing != null && existing.getInstallationId().equals(installationId)) {
            existing.refresh(verified.account().id(), verified.account().login(), verified.account().type());
            integrations.save(existing);
        } else {
            if (existing != null) integrations.delete(existing);
            integrations.save(new GitHubIntegration(userId, installationId, verified.account().id(),
                verified.account().login(), verified.account().type()));
        }
    }

    @Transactional(readOnly = true)
    public Status status(HttpServletRequest request) {
        Long userId = authenticatedUser(request);
        return integrations.findByUserId(userId).map(i -> new Status(true, i.getAccountLogin(), i.getAccountType(), "Metadata: read-only; Contents: read-only", i.getStatus()))
            .orElse(new Status(false, null, null, null, null));
    }

    @Transactional
    public void disconnect(HttpServletRequest request) {
        Long userId = authenticatedUser(request);
        requireTrustedOrigin(request);
        integrations.findByUserId(userId).ifPresent(integrations::delete);
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        String expected = URI.create(frontendUrl).getScheme() + "://" + URI.create(frontendUrl).getAuthority();
        if (origin == null || !expected.equalsIgnoreCase(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Request origin validation failed");
        }
    }

    static Long authenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(USER_ID);
        if (!(id instanceof Long userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to continue");
        return userId;
    }

    public record Status(boolean connected, String username, String accountType, String permissions, String status) {}
}
