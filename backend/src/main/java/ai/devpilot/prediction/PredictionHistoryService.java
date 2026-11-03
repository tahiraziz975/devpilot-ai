package ai.devpilot.prediction;

import ai.devpilot.backend.PredictionRequest;
import ai.devpilot.backend.PredictionResponse;
import ai.devpilot.user.User;
import ai.devpilot.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PredictionHistoryService {
    private static final String USER_ID_SESSION_KEY = "devpilotUserId";

    private final PredictionHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PredictionHistoryService(
            PredictionHistoryRepository historyRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveForSignedInUser(
            HttpServletRequest request,
            PredictionRequest input,
            PredictionResponse prediction) {
        Long userId = sessionUserId(request, false);
        if (userId == null) {
            return;
        }

        try {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "User account no longer exists"));
            PredictionHistory entry = new PredictionHistory();
            entry.setUser(user);
            entry.setIssueTitle(input.title());
            entry.setIssueBody(input.body());
            entry.setPredictedLabel(prediction.label());
            entry.setProbabilities(objectMapper.writeValueAsString(prediction.probabilities()));
            historyRepository.save(entry);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store prediction probabilities", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<PredictionHistoryItem> findForCurrentUser(HttpServletRequest request) {
        Long userId = sessionUserId(request, true);
        return historyRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
            .map(this::toItem)
            .toList();
    }

    private PredictionHistoryItem toItem(PredictionHistory entry) {
        try {
            Map<String, Double> probabilities = objectMapper.readValue(
                entry.getProbabilities(), new TypeReference<>() {});
            return new PredictionHistoryItem(
                entry.getId(), entry.getIssueTitle(), entry.getIssueBody(),
                entry.getPredictedLabel(), probabilities, entry.getCreatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read stored prediction probabilities", exception);
        }
    }

    private Long sessionUserId(HttpServletRequest request, boolean required) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute(USER_ID_SESSION_KEY);
        if (value instanceof Long userId) {
            return userId;
        }
        if (required) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to view prediction history");
        }
        return null;
    }
}
