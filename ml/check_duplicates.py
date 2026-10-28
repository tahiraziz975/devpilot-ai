
from pathlib import Path

import pandas as pd

DATA_DIR = (
    Path(__file__).resolve().parents[1]
    / "datasets"
    / "raw"
)

FILES = [
    DATA_DIR / "nlbse23-issue-classification-train.csv",
    DATA_DIR / "nlbse23-issue-classification-test.csv",
]

for file_path in FILES:
    print(f"\nChecking {file_path.name}")

    # Keep only the columns needed for this investigation.
    df = pd.read_csv(
        file_path,
        usecols=["id", "labels"],
        dtype={"id": "int64", "labels": "string"},
    )

    duplicated = df[df.duplicated("id", keep=False)]

    print(f"Total rows: {len(df):,}")
    print(f"Unique IDs: {df['id'].nunique():,}")
    print(f"Rows belonging to repeated IDs: {len(duplicated):,}")
    print(
        "Number of IDs appearing more than once: "
        f"{duplicated['id'].nunique():,}"
    )

    label_counts = duplicated.groupby("id")["labels"].nunique()
    conflicting_ids = label_counts[label_counts > 1].index

    print(f"Repeated IDs with conflicting labels: {len(conflicting_ids):,}")

    if len(conflicting_ids):
        print("\nExamples of conflicting IDs:")
        print(
            duplicated[
                duplicated["id"].isin(conflicting_ids)
            ].sort_values("id").head(10).to_string(index=False)
        )

    del df, duplicated
