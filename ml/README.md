# ML API and title + body model

From the repository root, generate the reproducible title + body artifact:

```bash
ml/.venv/bin/python ml/train_title_body.py
```

The script uses the existing prepared train and validation splits and the
50,000/15,000 stratified sample configuration from `compare_text_inputs.py`.
It saves the vectorizer and classifier together in
`ml/artifacts/title_body_bundle.joblib` so they cannot be accidentally mixed.

Run the API with:

```bash
PYTHONPATH=ml/src ml/.venv/bin/uvicorn devpilot_ml.api:app --host 127.0.0.1 --port 8000
```

The service exposes `GET /health`, `POST /predict`, and FastAPI's `/docs`.
Prediction requests use JSON with `title` and `body` strings. Responses include
the predicted `label` and probabilities for every trained class. If the model
artifact is not present, health reports `model_loaded: false` and predictions
return HTTP 503 until training is run.

Run tests from the repository root:

```bash
PYTHONPATH=ml/src ml/.venv/bin/pytest ml/tests
```
