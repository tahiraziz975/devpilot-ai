
package ai.devpilot.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByProfileId(String profileId);

    boolean existsByEmail(String email);

    boolean existsByProfileId(String profileId);
}
