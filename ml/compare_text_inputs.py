from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score, classification_report
from sklearn.model_selection import train_test_split

DATA_DIR = Path("datasets/processed")
ARTIFACT_DIR = Path("ml/artifacts")
ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)

TRAIN_SIZE = 50_000
VALIDATION_SIZE = 15_000
RANDOM_STATE = 42


def load_data(filename):
    df = pd.read_csv(
        DATA_DIR / filename,
        usecols=["id", "labels", "title", "body"],
    )

    df["title"] = df["title"].fillna("").astype(str)
    df["body"] = df["body"].fillna("").astype(str)
    df["labels"] = df["labels"].astype(str)

    return df


def make_sample(df, size):
    # Keep the same sampling strategy for every experiment.
    if size > len(df):
        raise ValueError("Requested sample exceeds dataset size.")

    return train_test_split(
        df,
        train_size=size,
        random_state=RANDOM_STATE,
        stratify=df["labels"],
    )[0]


def main():
    train_df = load_data("train_title_body.csv")
    validation_df = load_data("validation_title_body.csv")

    train_sample = make_sample(train_df, TRAIN_SIZE)
    validation_sample = make_sample(validation_df, VALIDATION_SIZE)

    experiments = {
        "title_only": (
            train_sample["title"],
            validation_sample["title"],
        ),
        "body_only": (
            train_sample["body"],
            validation_sample["body"],
        ),
        "title_plus_body": (
            train_sample["title"] + "\n" + train_sample["body"],
            validation_sample["title"] + "\n" + validation_sample["body"],
        ),
    }

    results = []

    for name, (train_text, validation_text) in experiments.items():
        print(f"\n{'=' * 20} {name.upper()} {'=' * 20}")

        vectorizer = TfidfVectorizer(
            max_features=100_000,
            ngram_range=(1, 2),
            min_df=3,
            max_df=0.95,
            strip_accents="unicode",
            sublinear_tf=True,
            dtype=np.float32,
        )

        X_train = vectorizer.fit_transform(train_text)
        X_validation = vectorizer.transform(validation_text)

        model = LogisticRegression(
            max_iter=1000,
            class_weight="balanced",
            random_state=RANDOM_STATE,
        )

        model.fit(X_train, train_sample["labels"])
        predictions = model.predict(X_validation)

        accuracy = accuracy_score(
            validation_sample["labels"], predictions
        )
        macro_f1 = f1_score(
            validation_sample["labels"],
            predictions,
            average="macro",
        )
        weighted_f1 = f1_score(
            validation_sample["labels"],
            predictions,
            average="weighted",
        )

        print(f"Features: {X_train.shape[1]:,}")
        print(f"Accuracy: {accuracy:.4f}")
        print(f"Macro F1: {macro_f1:.4f}")
        print(f"Weighted F1: {weighted_f1:.4f}")

        print("\nClassification report:")
        print(
            classification_report(
                validation_sample["labels"],
                predictions,
                zero_division=0,
            )
        )

        results.append({
            "experiment": name,
            "features": X_train.shape[1],
            "accuracy": accuracy,
            "macro_f1": macro_f1,
            "weighted_f1": weighted_f1,
        })

        # Save only the current experiment's results and release memory.
        del X_train, X_validation, vectorizer, model, predictions

    results_df = pd.DataFrame(results)
    output_path = ARTIFACT_DIR / "text_input_comparison.csv"
    results_df.to_csv(output_path, index=False)

    print("\nFinal comparison:")
    print(results_df.round(4).to_string(index=False))
    print(f"\nResults saved to: {output_path}")


if __name__ == "__main__":
    main()