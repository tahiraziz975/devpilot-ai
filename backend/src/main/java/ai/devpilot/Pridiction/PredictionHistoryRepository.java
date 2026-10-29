
package com.devpilot.backend.prediction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PredictionHistoryRepository
        extends JpaRepository<PredictionHistory, Long> {

    List<PredictionHistory> findByUser_IdOrderByCreatedAtDesc(Long userId);
}
