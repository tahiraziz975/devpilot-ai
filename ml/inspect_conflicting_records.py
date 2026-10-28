
from pathlib import Path

import pandas as pd

DATA_DIR = (
    Path(__file__).resolve().parents[1]
    / "datasets"
    / "raw"
)

TRAIN_FILE = DATA_DIR / "nlbse23-issue-classification-train.csv"
TEST_FILE = DATA_DIR / "nlbse23-issue-classification-test.csv"

columns = ["id", "labels", "title", "body"]

train = pd.read_csv(TRAIN_FILE, usecols=columns)
test = pd.read_csv(TEST_FILE, usecols=columns)

# Build the set of labels associated with each ID.
train_labels = train.groupby("id")["labels"].agg(
    lambda values: set(values.dropna())
)
test_labels = test.groupby("id")["labels"].agg(
    lambda values: set(values.dropna())
)

shared_ids = train_labels.index.intersection(test_labels.index)

conflicting_ids = [
    issue_id
    for issue_id in shared_ids
    if train_labels[issue_id] != test_labels[issue_id]
]

print(f"Conflicting shared IDs: {len(conflicting_ids):,}")

for issue_id in conflicting_ids[:10]:
    train_rows = train[train["id"] == issue_id]
    test_rows = test[test["id"] == issue_id]

    print(f"\n{'=' * 70}")
    print(f"ID: {issue_id}")
    print(f"Train labels: {train_labels[issue_id]}")
    print(f"Test labels: {test_labels[issue_id]}")

    print("\nTraining record(s):")
    print(train_rows.to_string(index=False, max_colwidth=180))

    print("\nTest record(s):")
    print(test_rows.to_string(index=False, max_colwidth=180))

for issue_id in conflicting_ids:
    train_rows = train[train["id"] == issue_id]
    test_rows = test[test["id"] == issue_id]

    for _, train_row in train_rows.iterrows():
        for _, test_row in test_rows.iterrows():
            same_title = (
                str(train_row["title"]) == str(test_row["title"])
            )
            same_body = (
                str(train_row["body"]) == str(test_row["body"])
            )

            print(
                f"ID={issue_id} | "
                f"same_title={same_title} | "
                f"same_body={same_body}"
            )
