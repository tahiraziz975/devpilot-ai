"""FastAPI prediction service for the paired title + body classifier."""

import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Dict

import joblib
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)
DEFAULT_ARTIFACT = Path(__file__).resolve().parents[2] / "artifacts" / "title_body_bundle.joblib"


class PredictionRequest(BaseModel):
    title: str = Field(..., max_length=10_000)
    body: str = Field(..., max_length=500_000)


class PredictionResponse(BaseModel):
    label: str
    probabilities: Dict[str, float]


def load_predictor(path: Path):
    if not path.is_file():
        raise FileNotFoundError(f"Model artifact not found: {path}")
    bundle = joblib.load(path)
    if not isinstance(bundle, dict) or bundle.get("artifact_version") != 1:
        raise ValueError("Unsupported model artifact; train with ml/train_title_body.py.")
    if bundle.get("input_mode") != "title_plus_body":
        raise ValueError("Artifact is not trained for title + body input.")
    vectorizer, model = bundle.get("vectorizer"), bundle.get("model")
    if vectorizer is None or model is None:
        raise ValueError("Artifact must contain both a vectorizer and a model.")
    feature_count = len(getattr(vectorizer, "vocabulary_", {}))
    model_feature_count = getattr(model, "n_features_in_", None)
    classes = list(getattr(model, "classes_", []))
    if not feature_count or feature_count != bundle.get("n_features"):
        raise ValueError("Artifact vectorizer feature count does not match its metadata.")
    if model_feature_count != feature_count:
        raise ValueError("Vectorizer and classifier feature dimensions do not match.")
    if classes != bundle.get("classes"):
        raise ValueError("Artifact class metadata does not match the classifier.")
    if not hasattr(model, "predict_proba"):
        raise ValueError("Classifier does not support probability predictions.")
    return vectorizer, model


def create_app(artifact_path: Path = DEFAULT_ARTIFACT) -> FastAPI:
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.predictor = None
        app.state.model_error = None
        try:
            app.state.predictor = load_predictor(Path(os.getenv("DEVPILOT_MODEL_PATH", artifact_path)))
        except (FileNotFoundError, ValueError, OSError) as exc:
            app.state.model_error = str(exc)
            logger.error("Could not load prediction model: %s", exc)
        yield

    app = FastAPI(title="DevPilot AI Prediction API", version="1.0.0", lifespan=lifespan)

    @app.get("/health")
    async def health():
        return {"status": "ok", "model_loaded": app.state.predictor is not None}

    @app.post("/predict", response_model=PredictionResponse)
    async def predict(request: PredictionRequest):
        if not request.title.strip() and not request.body.strip():
            raise HTTPException(status_code=422, detail="title or body must contain text")
        if app.state.predictor is None:
            raise HTTPException(status_code=503, detail="prediction model is unavailable")
        vectorizer, model = app.state.predictor
        text = f"{request.title}\n{request.body}"
        features = vectorizer.transform([text])
        values = model.predict_proba(features)[0]
        probabilities = {str(label): float(prob) for label, prob in zip(model.classes_, values)}
        predicted_index = int(values.argmax())
        return PredictionResponse(label=str(model.classes_[predicted_index]), probabilities=probabilities)

    return app


app = create_app()
