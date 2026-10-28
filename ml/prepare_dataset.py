
from pathlib import Path

import pandas as pd

ROOT_DIR = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT_DIR / "datasets" / "raw"
OUTPUT_DIR = ROOT_DIR / "datasets" / "processed"

TRAIN_FILE = RAW_DIR / "nlbse23-issue-classification-train.csv"
OUTPUT_FILE = OUTPUT_DIR / "devpilot_train.csv"

CHUNK_SIZE = 50_000
VALID_LABELS = {"bug", "feature", "question", "documentation"}


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # First pass: identify IDs that have conflicting labels.
    labels_by_id = {}

    for chunk in pd.read_csv(
        TRAIN_FILE,
        usecols=["id", "labels"],
        chunksize=CHUNK_SIZE,
    ):
        for issue_id, label in zip(chunk["id"], chunk["labels"]):
            if pd.isna(issue_id) or pd.isna(label):
                continue

            label = str(label).strip().lower()

            if label not in VALID_LABELS:
                continue

            labels_by_id.setdefault(int(issue_id), set()).add(label)

    conflicting_ids = {
        issue_id
        for issue_id, labels in labels_by_id.items()
        if len(labels) > 1
    }

    print(f"IDs with conflicting labels: {len(conflicting_ids):,}")

    # Second pass: retain one record per non-conflicting ID.
    seen_ids = set()
    saved_rows = []

    for chunk in pd.read_csv(
        TRAIN_FILE,
        usecols=["id", "labels", "title", "body"],
        chunksize=CHUNK_SIZE,
    ):
        chunk["labels"] = (
            chunk["labels"].fillna("").str.strip().str.lower()
        )

        chunk = chunk[chunk["labels"].isin(VALID_LABELS)]
        chunk = chunk[~chunk["id"].isin(conflicting_ids)]
        chunk = chunk[~chunk["id"].isin(seen_ids)]

        # Keep only the first occurrence of each ID.
        chunk = chunk.drop_duplicates(subset=["id"], keep="first")

        seen_ids.update(chunk["id"].astype(int).tolist())

        chunk["title"] = chunk["title"].fillna("").astype(str)
        chunk["body"] = chunk["body"].fillna("").astype(str)

        chunk["text"] = (
            chunk["title"].str.strip()
            + "\n"
            + chunk["body"].str.strip()
        ).str.strip()

        chunk = chunk[chunk["text"].str.len() > 0]

        saved_rows.extend(
            chunk[["id", "labels", "text"]].to_dict("records")
        )

    result = pd.DataFrame(saved_rows)
    result.to_csv(OUTPUT_FILE, index=False)

    print(f"\nPrepared records: {len(result):,}")
    print(f"Saved to: {OUTPUT_FILE}")

    print("\nClass distribution:")
    print(result["labels"].value_counts())

    print("\nDataset size:")
    print(f"{OUTPUT_FILE.stat().st_size / (1024 ** 2):.2f} MB")


if __name__ == "__main__":
    main()
