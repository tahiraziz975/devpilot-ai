package ai.devpilot.github;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubRepositoryAccessRepository extends JpaRepository<GitHubRepositoryAccess, Long> {
    List<GitHubRepositoryAccess> findByUserId(Long userId);
    Optional<GitHubRepositoryAccess> findByUserIdAndRepositoryId(Long userId, Long repositoryId);
    void deleteByUserId(Long userId);
}
