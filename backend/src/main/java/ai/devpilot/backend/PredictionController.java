package ai.devpilot.backend;

import ai.devpilot.prediction.PredictionHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {
    private final MlPredictionClient mlClient;
    private final PredictionHistoryService historyService;

    public PredictionController(MlPredictionClient mlClient, PredictionHistoryService historyService) {
        this.mlClient = mlClient;
        this.historyService = historyService;
    }

    @PostMapping
    public PredictionResponse predict(
            @Valid @RequestBody PredictionRequest request,
            HttpServletRequest servletRequest) {
        if ((request.title() == null || request.title().isBlank())
                && (request.body() == null || request.body().isBlank())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "title or body must contain text");
        }
        try {
            PredictionResponse prediction = mlClient.predict(request);
            historyService.saveForSignedInUser(servletRequest, request, prediction);
            return prediction;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "The ML prediction service could not complete the request",
                exception
            );
        }
    }
}
