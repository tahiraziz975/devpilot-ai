# DevPilot AI

An ML-powered developer intelligence platform for issue classification,
similar issue detection, explainable predictions, and model experimentation.

## Project Structure

- `frontend/` — React + TypeScript user interface
- `backend/` — Java + Spring Boot application API
- `ml/` — Python machine learning service and experiments
- `datasets/` — Dataset setup and documentation
- `docs/` — Architecture and experiment documentation

## Development Status

The ML service supports issue classification with a title + body TF-IDF and
balanced Logistic Regression model.

## Run the ML Prediction API

From the repository root, first build the paired model/vectorizer artifact from
the processed title/body splits:

```bash
ml/.venv/bin/python ml/train_title_body.py
```

This writes `ml/artifacts/title_body_bundle.joblib`. The existing
`balanced_model.joblib` artifacts come from an earlier experiment and are not
used by this API.

Start the API:

```bash
PYTHONPATH=ml/src ml/.venv/bin/uvicorn devpilot_ml.api:app --host 127.0.0.1 --port 8000
```

Check `http://127.0.0.1:8000/health`, open `http://127.0.0.1:8000/docs`, or
submit a prediction:

```bash
curl -X POST http://127.0.0.1:8000/predict \
  -H 'Content-Type: application/json' \
  -d '{"title":"Application crashes on startup","body":"The CLI exits with an unexpected exception."}'
```

Install dependencies with `ml/.venv/bin/pip install -r ml/requirements.txt` if
needed. Run the API tests with:

```bash
PYTHONPATH=ml/src ml/.venv/bin/pytest ml/tests
```

Set `DEVPILOT_MODEL_PATH` to use the paired artifact at another path.

## Run the Spring Boot backend

The backend exposes `POST /api/predictions` and forwards the title and body to
the Python API at `/predict`. Start the Python API as described above, then in
a second terminal run (Java 17 and Maven required):

```bash
cd backend
mvn spring-boot:run
```

By default, the Java service calls `http://127.0.0.1:8000`. To point it to a
different Python service URL, set `DEVPILOT_ML_BASE_URL` before starting the
backend. For example:

```bash
DEVPILOT_ML_BASE_URL=http://localhost:8000 mvn spring-boot:run
```

Send a prediction to the Java API on port 8080:

```bash
curl -X POST http://127.0.0.1:8080/api/predictions \
  -H 'Content-Type: application/json' \
  -d '{"title":"Application crashes on startup","body":"The CLI exits with an unexpected exception."}'
```

The response contains the predicted `label` and class `probabilities`. Run the
backend checks from `backend/` with `mvn test`; the mock HTTP test verifies the
request and response between Java and Python without starting the ML service.

## Planned Capabilities

- Software issue classification
- Similar issue retrieval
- Explainable predictions
- Model evaluation and experiment tracking
EOF
