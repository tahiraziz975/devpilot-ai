import asyncio

import joblib
import numpy as np
import pytest
from httpx import ASGITransport, AsyncClient
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression

from devpilot_ml.api import create_app, load_predictor


async def request(app, method, path, **kwargs):
    async with app.router.lifespan_context(app):
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            return await client.request(method, path, **kwargs)


@pytest.fixture
def artifact(tmp_path):
    texts = ["bug crash", "feature add", "question how", "documentation guide"] * 4
    labels = ["bug", "feature", "question", "documentation"] * 4
    vectorizer = TfidfVectorizer()
    matrix = vectorizer.fit_transform(texts)
    model = LogisticRegression(random_state=42).fit(matrix, labels)
    path = tmp_path / "bundle.joblib"
    joblib.dump({
        "artifact_version": 1,
        "input_mode": "title_plus_body",
        "vectorizer": vectorizer,
        "model": model,
        "classes": model.classes_.tolist(),
        "n_features": matrix.shape[1],
    }, path)
    return path


def test_health_and_prediction_return_all_class_probabilities(artifact):
    app = create_app(artifact)
    async def exercise():
        async with app.router.lifespan_context(app):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
                health = await client.get("/health")
                response = await client.post("/predict", json={"title": "crash on startup", "body": "app crashes"})
                return health, response
    health, response = asyncio.run(exercise())
    assert health.status_code == 200
    assert health.json() == {"status": "ok", "model_loaded": True}
    assert response.status_code == 200
    data = response.json()
    assert data["label"] in data["probabilities"]
    assert set(data["probabilities"]) == {"bug", "feature", "question", "documentation"}
    assert all(0 <= p <= 1 for p in data["probabilities"].values())
    assert np.isclose(sum(data["probabilities"].values()), 1.0)


def test_missing_model_is_reported_and_prediction_returns_503(tmp_path):
    app = create_app(tmp_path / "missing.joblib")
    async def exercise():
        async with app.router.lifespan_context(app):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
                health = await client.get("/health")
                response = await client.post("/predict", json={"title": "issue", "body": "details"})
                return health, response
    health, response = asyncio.run(exercise())
    assert health.json()["model_loaded"] is False
    assert response.status_code == 503


def test_empty_request_text_is_rejected(artifact):
    response = asyncio.run(request(create_app(artifact), "POST", "/predict", json={"title": "  ", "body": ""}))
    assert response.status_code == 422


def test_incompatible_vectorizer_model_pair_is_rejected(tmp_path):
    vectorizer = TfidfVectorizer().fit(["alpha beta", "gamma delta"])
    model = LogisticRegression().fit(np.ones((4, 2)), ["a", "b", "a", "b"])
    path = tmp_path / "bad.joblib"
    joblib.dump({
        "artifact_version": 1,
        "input_mode": "title_plus_body",
        "vectorizer": vectorizer,
        "model": model,
        "classes": model.classes_.tolist(),
        "n_features": len(vectorizer.vocabulary_),
    }, path)
    with pytest.raises(ValueError, match="dimensions do not match"):
        load_predictor(path)
