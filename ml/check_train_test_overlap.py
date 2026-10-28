
from pathlib import Path

import pandas as pd

DATA_DIR = (
    Path(__file__).resolve().parents[1]
    / "datasets"
    / "raw"
)

TRAIN_FILE = DATA_DIR / "nlbse23-issue-classification-train.csv"
TEST_FILE = DATA_DIR / "nlbse23-issue-classification-test.csv"

columns = ["id", "labels"]

train = pd.read_csv(
    TRAIN_FILE,
    usecols=columns,
    dtype={"id": "int64", "labels": "string"},
)

test = pd.read_csv(
    TEST_FILE,
    usecols=columns,
    dtype={"id": "int64", "labels": "string"},
)

# One record per ID, with all labels associated with it.
train_labels = train.groupby("id")["labels"].agg(
    lambda labels: set(labels.dropna())
)
test_labels = test.groupby("id")["labels"].agg(
    lambda labels: set(labels.dropna())
)

shared_ids = train_labels.index.intersection(test_labels.index)

print(f"Unique training IDs: {len(train_labels):,}")
print(f"Unique test IDs: {len(test_labels):,}")
print(f"Shared IDs across train and test: {len(shared_ids):,}")

conflicts = [
    issue_id
    for issue_id in shared_ids
    if train_labels[issue_id] != test_labels[issue_id]
]

print(f"Shared IDs with different label sets: {len(conflicts):,}")

if conflicts:
    print("\nExamples:")
    for issue_id in conflicts[:10]:
        print(
            f"ID: {issue_id} | "
            f"Train labels: {train_labels[issue_id]} | "
            f"Test labels: {test_labels[issue_id]}"
        )
