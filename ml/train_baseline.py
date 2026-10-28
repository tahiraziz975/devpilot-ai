
from pathlib import Path

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    f1_score,
)
from sklearn.model_selection import train_test_split

ROOT_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT_DIR / "datasets" / "processed"
ARTIFACT_DIR = ROOT_DIR / "ml" / "artifacts"

TRAIN_FILE = DATA_DIR / "train.csv"
VALIDATION_FILE = DATA_DIR / "validation.csv"

TRAIN_SAMPLE_SIZE = 50_000
VALIDATION_SAMPLE_SIZE = 15_000
RANDOM_STATE = 42

LABELS = ["bug", "feature", "question", "documentation"]


def main():
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)

    print("Loading training data...")
    train_df = pd.read_csv(
        TRAIN_FILE, usecols=["labels", "text"]
    )

    print("Loading validation data...")
    val_df = pd.read_csv(
        VALIDATION_FILE, usecols=["labels", "text"]
    )

    # Select reproducible, stratified samples for our first experiment.
    train_sample, _ = train_test_split(
        train_df,
        train_size=TRAIN_SAMPLE_SIZE,
        random_state=RANDOM_STATE,
        stratify=train_df["labels"],
    )

    val_sample, _ = train_test_split(
        val_df,
        train_size=VALIDATION_SAMPLE_SIZE,
        random_state=RANDOM_STATE,
        stratify=val_df["labels"],
    )

    X_train = train_sample["text"].fillna("")
    y_train = train_sample["labels"]

    X_val = val_sample["text"].fillna("")
    y_val = val_sample["labels"]

    print(f"\nTraining examples: {len(X_train):,}")
    print(f"Validation examples: {len(X_val):,}")

    # Convert text into numerical TF-IDF features.
    print("\nStep 1: Building TF-IDF features...")

    vectorizer = TfidfVectorizer(
        lowercase=True,
        strip_accents="unicode",
        ngram_range=(1, 2),
        min_df=3,
        max_df=0.95,
        max_features=100_000,
        sublinear_tf=True,
        dtype="float32",
    )

    X_train_tfidf = vectorizer.fit_transform(X_train)
    X_val_tfidf = vectorizer.transform(X_val)

    print(f"Training matrix shape: {X_train_tfidf.shape}")
    print(f"Validation matrix shape: {X_val_tfidf.shape}")

    # Train the classifier.
    print("\nStep 2: Training Logistic Regression...")

    model = LogisticRegression(
        max_iter=200,
        solver="lbfgs",
        random_state=RANDOM_STATE,
    )

    model.fit(X_train_tfidf, y_train)

    # Evaluate on data the model did not train on.
    print("\nStep 3: Evaluating the model...")

    predictions = model.predict(X_val_tfidf)

    print(f"\nAccuracy: {accuracy_score(y_val, predictions):.4f}")
    print(f"Macro F1: {f1_score(y_val, predictions, average='macro'):.4f}")
    print(f"Weighted F1: {f1_score(y_val, predictions, average='weighted'):.4f}")

    print("\nClassification report:")
    print(
        classification_report(
            y_val,
            predictions,
            labels=LABELS,
            target_names=LABELS,
            zero_division=0,
        )
    )

    print("Confusion matrix (rows = actual, columns = predicted):")
    print(LABELS)
    print(confusion_matrix(y_val, predictions, labels=LABELS))

    # Save both pieces: the trained model and its text vectorizer.
    model_path = ARTIFACT_DIR / "baseline_model.joblib"
    vectorizer_path = ARTIFACT_DIR / "tfidf_vectorizer.joblib"

    joblib.dump(model, model_path)
    joblib.dump(vectorizer, vectorizer_path)

    print(f"\nModel saved: {model_path}")
    print(f"Vectorizer saved: {vectorizer_path}")


if __name__ == "__main__":
    main()
