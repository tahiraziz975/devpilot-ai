
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

ROOT_DIR = Path(__file__).resolve().parents[1]
INPUT_FILE = ROOT_DIR / "datasets" / "processed" / "devpilot_train.csv"
OUTPUT_DIR = ROOT_DIR / "datasets" / "processed"

RANDOM_STATE = 42


def main():
    print("Loading prepared dataset...")

    df = pd.read_csv(INPUT_FILE)

    print(f"Total records: {len(df):,}")
    print(f"Unique IDs: {df['id'].nunique():,}")

    # Ensure each issue ID appears only once.
    assert df["id"].is_unique, "Duplicate issue IDs found!"

    train_df, val_df = train_test_split(
        df,
        test_size=0.20,
        random_state=RANDOM_STATE,
        stratify=df["labels"],
    )

    train_file = OUTPUT_DIR / "train.csv"
    val_file = OUTPUT_DIR / "validation.csv"

    train_df.to_csv(train_file, index=False)
    val_df.to_csv(val_file, index=False)

    print(f"\nTraining records: {len(train_df):,}")
    print(f"Validation records: {len(val_df):,}")

    print("\nTraining class distribution:")
    print(train_df["labels"].value_counts(normalize=True).round(4))

    print("\nValidation class distribution:")
    print(val_df["labels"].value_counts(normalize=True).round(4))

    print(f"\nSaved training data: {train_file}")
    print(f"Saved validation data: {val_file}")


if __name__ == "__main__":
    main()
