package ai.devpilot.github;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "github_repository_access", uniqueConstraints = {
    @UniqueConstraint(name = "uk_github_repository_access_user_repo", columnNames = {"user_id", "repository_id"})
})
public class GitHubRepositoryAccess {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GitHubRepositoryAccess() {}
    public GitHubRepositoryAccess(Long userId, Long repositoryId, String fullName, boolean enabled) {
        this.userId = userId; this.repositoryId = repositoryId; this.fullName = fullName;
        this.enabled = enabled; this.updatedAt = Instant.now();
    }
    public Long getRepositoryId() { return repositoryId; }
    public String getFullName() { return fullName; }
    public boolean isEnabled() { return enabled; }
    public void update(String fullName, boolean enabled) {
        this.fullName = fullName; this.enabled = enabled; this.updatedAt = Instant.now();
    }
}
