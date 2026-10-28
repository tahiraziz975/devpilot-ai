
from pathlib import Path

import joblib
import pandas as pd
from sklearn.model_selection import train_test_split

ROOT_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT_DIR / "datasets" / "processed"
ARTIFACT_DIR = ROOT_DIR / "ml" / "artifacts"

LABELS = ["bug", "feature", "question", "documentation"]
VALIDATION_SAMPLE_SIZE = 15_000
RANDOM_STATE = 42


def main():
    print("Loading validation data...")
    df = pd.read_csv(
        DATA_DIR / "validation.csv",
        usecols=["labels", "text"],
    )

    # Reproduce the same validation sample used in training.
    sample, _ = train_test_split(
        df,
        train_size=VALIDATION_SAMPLE_SIZE,
        random_state=RANDOM_STATE,
        stratify=df["labels"],
    )

    model = joblib.load(ARTIFACT_DIR / "balanced_model.joblib")
    vectorizer = joblib.load(
        ARTIFACT_DIR / "balanced_tfidf_vectorizer.joblib"
    )

    X = vectorizer.transform(sample["text"].fillna(""))
    actual = sample["labels"].to_numpy()
    predicted = model.predict(X)
    probabilities = model.predict_proba(X)

    results = pd.DataFrame({
        "actual": actual,
        "predicted": predicted,
        "text": sample["text"].to_numpy(),
    })

    results["confidence"] = probabilities.max(axis=1)
    errors = results[results["actual"] != results["predicted"]].copy()

    print(f"\nValidation examples: {len(results):,}")
    print(f"Misclassified examples: {len(errors):,}")
    print(f"Error rate: {len(errors) / len(results):.2%}")

    print("\nMost common actual -> predicted errors:")
    print(
        errors.groupby(["actual", "predicted"])
        .size()
        .sort_values(ascending=False)
        .head(12)
    )

    # Inspect minority-class mistakes first.
    minority_errors = errors[
        errors["actual"].isin(["question", "documentation"])
    ]

    output_file = DATA_DIR / "error_analysis.csv"
    minority_errors.sample(
        n=min(100, len(minority_errors)),
        random_state=RANDOM_STATE,
    ).to_csv(output_file, index=False)

    print(f"\nSaved up to 100 minority-class errors to: {output_file}")
    print("Open this CSV and compare the actual label, prediction, and text.")


if __name__ == "__main__":
    main()
