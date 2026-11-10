package ai.devpilot.github;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubIntegrationRepository extends JpaRepository<GitHubIntegration, Long> {
    Optional<GitHubIntegration> findByUserId(Long userId);
    Optional<GitHubIntegration> findByInstallationId(Long installationId);
}
