package ai.devpilot.backend;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {
    private final MlPredictionClient mlClient;

    public PredictionController(MlPredictionClient mlClient) {
        this.mlClient = mlClient;
    }

    @PostMapping
    public PredictionResponse predict(@Valid @RequestBody PredictionRequest request) {
        if ((request.title() == null || request.title().isBlank())
                && (request.body() == null || request.body().isBlank())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "title or body must contain text");
        }
        try {
            return mlClient.predict(request);
        } catch (RestClientException exception) {
    exception.printStackTrace();

    throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "The ML prediction service could not complete the request",
            exception
    );
}
    }
}
