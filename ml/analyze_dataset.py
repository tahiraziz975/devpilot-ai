
from pathlib import Path
from collections import Counter

import pandas as pd

DATA_DIR = (
    Path(__file__).resolve().parents[1]
    / "datasets"
    / "raw"
)

TRAIN_FILE = DATA_DIR / "nlbse23-issue-classification-train.csv"
TEST_FILE = DATA_DIR / "nlbse23-issue-classification-test.csv"

CHUNK_SIZE = 50_000


def analyze_file(file_path: Path) -> None:
    print(f"\nAnalyzing: {file_path.name}")

    label_counts = Counter()
    total_rows = 0
    missing_values = Counter()
    duplicate_ids = 0
    seen_ids = set()

    for chunk in pd.read_csv(file_path, chunksize=CHUNK_SIZE):
        total_rows += len(chunk)

        label_counts.update(
            chunk["labels"].fillna("MISSING_LABEL").astype(str)
        )

        missing_values.update(
            chunk.isna().sum().to_dict()
        )

        ids = chunk["id"].dropna()
        duplicate_ids += int(ids.isin(seen_ids).sum())
        seen_ids.update(ids.tolist())

        if total_rows % 200_000 < len(chunk):
            print(f"Processed {total_rows:,} rows")

    print(f"\nTotal rows: {total_rows:,}")

    print("\nClass distribution:")
    for label, count in label_counts.most_common():
        percentage = count / total_rows * 100
        print(f"{label}: {count:,} ({percentage:.2f}%)")

    print("\nMissing values:")
    for column, count in missing_values.items():
        print(f"{column}: {count:,}")

    print(f"\nDuplicate IDs: {duplicate_ids}")


if __name__ == "__main__":
    analyze_file(TRAIN_FILE)
    analyze_file(TEST_FILE)
