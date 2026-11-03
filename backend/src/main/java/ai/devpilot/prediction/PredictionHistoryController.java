package ai.devpilot.prediction;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions/history")
public class PredictionHistoryController {
    private final PredictionHistoryService historyService;

    public PredictionHistoryController(PredictionHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<PredictionHistoryItem> getHistory(HttpServletRequest request) {
        return historyService.findForCurrentUser(request);
    }
}
