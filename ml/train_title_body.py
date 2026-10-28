"""Reproducibly train and save the title + body classifier used by the API."""

from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import train_test_split

ROOT_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT_DIR / "datasets" / "processed"
ARTIFACT_DIR = ROOT_DIR / "ml" / "artifacts"
TRAIN_SIZE = 50_000
VALIDATION_SIZE = 15_000
RANDOM_STATE = 42


def load_data(filename: str) -> pd.DataFrame:
    frame = pd.read_csv(
        DATA_DIR / filename,
        usecols=["id", "labels", "title", "body"],
    )
    for column in ("title", "body", "labels"):
        frame[column] = frame[column].fillna("").astype(str)
    return frame


def make_sample(frame: pd.DataFrame, size: int) -> pd.DataFrame:
    if size > len(frame):
        raise ValueError(f"Requested sample ({size}) exceeds dataset size ({len(frame)}).")
    sample, _ = train_test_split(
        frame,
        train_size=size,
        random_state=RANDOM_STATE,
        stratify=frame["labels"],
    )
    return sample


def combine(title: pd.Series, body: pd.Series) -> pd.Series:
    return title.fillna("").astype(str) + "\n" + body.fillna("").astype(str)


def main() -> None:
    train = make_sample(load_data("train_title_body.csv"), TRAIN_SIZE)
    validation = make_sample(load_data("validation_title_body.csv"), VALIDATION_SIZE)
    train_text = combine(train["title"], train["body"])
    validation_text = combine(validation["title"], validation["body"])

    vectorizer = TfidfVectorizer(
        max_features=100_000,
        ngram_range=(1, 2),
        min_df=3,
        max_df=0.95,
        strip_accents="unicode",
        sublinear_tf=True,
        dtype=np.float32,
    )
    train_matrix = vectorizer.fit_transform(train_text)
    validation_matrix = vectorizer.transform(validation_text)
    model = LogisticRegression(
        max_iter=1000,
        class_weight="balanced",
        random_state=RANDOM_STATE,
    )
    model.fit(train_matrix, train["labels"])

    predicted = model.predict(validation_matrix)
    print(f"Features: {train_matrix.shape[1]:,}")
    print(f"Accuracy: {accuracy_score(validation['labels'], predicted):.4f}")
    print(f"Macro F1: {f1_score(validation['labels'], predicted, average='macro'):.4f}")
    print(f"Weighted F1: {f1_score(validation['labels'], predicted, average='weighted'):.4f}")
    print(classification_report(validation["labels"], predicted, zero_division=0))

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    destination = ARTIFACT_DIR / "title_body_bundle.joblib"
    temporary = destination.with_suffix(".joblib.tmp")
    joblib.dump(
        {
            "artifact_version": 1,
            "input_mode": "title_plus_body",
            "vectorizer": vectorizer,
            "model": model,
            "classes": model.classes_.tolist(),
            "n_features": int(train_matrix.shape[1]),
            "random_state": RANDOM_STATE,
            "train_size": TRAIN_SIZE,
            "validation_size": VALIDATION_SIZE,
        },
        temporary,
        compress=3,
    )
    temporary.replace(destination)
    print(f"Paired model/vectorizer saved: {destination}")


if __name__ == "__main__":
    main()
