package ai.devpilot.prediction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionHistoryRepository extends JpaRepository<PredictionHistory, Long> {
    List<PredictionHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
