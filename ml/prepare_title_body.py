from pathlib import Path

import pandas as pd

RAW_PATH = Path("datasets/raw/nlbse23-issue-classification-train.csv")
PROCESSED_DIR = Path("datasets/processed")

SPLITS = {
    "train": PROCESSED_DIR / "train.csv",
    "validation": PROCESSED_DIR / "validation.csv",
}

CHUNK_SIZE = 50_000


def main():
    # Existing split files contain the exact records used in our experiments.
    split_ids = {}

    for split_name, path in SPLITS.items():
        df = pd.read_csv(path, usecols=["id", "labels"])
        split_ids[split_name] = set(df["id"].astype(str))
        print(f"{split_name}: {len(df):,} records loaded")

    rows = {name: [] for name in SPLITS}

    # Read the large raw file in manageable chunks.
    for chunk in pd.read_csv(
        RAW_PATH,
        usecols=["id", "labels", "title", "body"],
        dtype={"id": str},
        chunksize=CHUNK_SIZE,
    ):
        chunk["labels"] = chunk["labels"].astype(str)

        for split_name, ids in split_ids.items():
            matched = chunk[chunk["id"].isin(ids)]

            if not matched.empty:
                rows[split_name].append(
                    matched[["id", "labels", "title", "body"]]
                )

    for split_name, parts in rows.items():
        if not parts:
            raise RuntimeError(f"No records matched for {split_name}")

        result = pd.concat(parts, ignore_index=True)
        result = result.drop_duplicates(subset="id")

        expected_ids = split_ids[split_name]
        found_ids = set(result["id"])

        missing = expected_ids - found_ids
        extra = found_ids - expected_ids

        print(f"\n{split_name}:")
        print(f"Matched records: {len(result):,}")
        print(f"Missing IDs: {len(missing):,}")
        print(f"Unexpected IDs: {len(extra):,}")

        if missing or extra or len(result) != len(expected_ids):
            raise RuntimeError(
                f"{split_name} ID mismatch. "
                "Do not use this output until the mismatch is investigated."
            )

        output = PROCESSED_DIR / f"{split_name}_title_body.csv"
        result.to_csv(output, index=False)
        print(f"Saved: {output}")

    print("\nTitle/body preparation completed successfully.")


if __name__ == "__main__":
    main()