from pathlib import Path

import pandas as pd

DATA_DIR = Path(__file__).resolve().parents[1] / "datasets" / "raw"

TRAIN_FILE = DATA_DIR / "nlbse23-issue-classification-train.csv"
TEST_FILE = DATA_DIR / "nlbse23-issue-classification-test.csv"


def inspect_file(file_path: Path) -> None:
    print(f"\n{'=' * 70}")
    print(f"File: {file_path.name}")
    print(f"Size: {file_path.stat().st_size / (1024**3):.2f} GB")

    # Read only a small sample; don't load the entire file into memory.
    df = pd.read_csv(file_path, nrows=5)

    print("\nColumns:")
    print(df.columns.tolist())

    print("\nData types:")
    print(df.dtypes)

    print("\nSample records:")
    print(df.head(3).to_string(index=False))

    print("\nMissing values in sample:")
    print(df.isna().sum())


if __name__ == "__main__":
    inspect_file(TRAIN_FILE)
    inspect_file(TEST_FILE)