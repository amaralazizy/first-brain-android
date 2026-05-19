"""
Feature engineering module for First Brain ML pipeline.

Transforms raw task-day observations into a numeric feature matrix
ready for model training and inference.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.preprocessing import OneHotEncoder
from typing import Optional


TASK_TYPES = ["Do", "Learn", "Life", "Idea"]
URGENCY_LEVELS = ["Low", "Medium", "High"]

# Numeric columns passed through as-is
_NUMERIC_COLS = [
    "days_since_creation",
    "days_since_last_interaction",
    "days_until_deadline",
    "is_overdue",
    "deadline_proximity",
    "skip_count",
    "estimated_effort",
    "has_deadline",
    "weekday",
    "is_weekend",
]

# Categorical columns to one-hot encode
_CAT_COLS = ["task_type", "urgency"]


class FeatureEngineer:
    """Fit-transform pipeline for task-day feature extraction.

    Usage
    -----
    engineer = FeatureEngineer()
    X_train = engineer.fit_transform(train_df)
    X_test  = engineer.transform(test_df)
    feature_names = engineer.feature_names_
    """

    def __init__(self) -> None:
        self._encoder: Optional[OneHotEncoder] = None
        self.feature_names_: list[str] = []

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def fit_transform(self, df: pd.DataFrame) -> np.ndarray:
        """Fit encoder on *df* and return the feature matrix."""
        self._encoder = OneHotEncoder(
            categories=[TASK_TYPES, URGENCY_LEVELS],
            sparse_output=False,
            handle_unknown="ignore",
        )
        cat_matrix = self._encoder.fit_transform(df[_CAT_COLS])
        cat_names = self._encoder.get_feature_names_out(_CAT_COLS).tolist()

        numeric_matrix = df[_NUMERIC_COLS].to_numpy(dtype=np.float64)
        self.feature_names_ = _NUMERIC_COLS + cat_names
        return np.hstack([numeric_matrix, cat_matrix])

    def transform(self, df: pd.DataFrame) -> np.ndarray:
        """Transform *df* using the already-fitted encoder."""
        if self._encoder is None:
            raise RuntimeError("Call fit_transform before transform.")
        cat_matrix = self._encoder.transform(df[_CAT_COLS])
        numeric_matrix = df[_NUMERIC_COLS].to_numpy(dtype=np.float64)
        return np.hstack([numeric_matrix, cat_matrix])

    # ------------------------------------------------------------------
    # Derived / aggregated features (optional enrichment)
    # ------------------------------------------------------------------

    @staticmethod
    def add_derived_features(df: pd.DataFrame) -> pd.DataFrame:
        """Add higher-order derived features to a raw task-day DataFrame.

        These features are computed from the existing columns and appended
        as new columns in a copy of the input DataFrame.

        Parameters
        ----------
        df:
            Raw task-day DataFrame as produced by ``data_simulation.simulate_dataset``.

        Returns
        -------
        pd.DataFrame
            Enriched copy of *df* with extra columns.
        """
        df = df.copy()

        # Urgency score: Low=1, Medium=2, High=3
        urgency_map = {"Low": 1, "Medium": 2, "High": 3}
        df["urgency_score"] = df["urgency"].map(urgency_map).fillna(1)

        # Staleness: tasks not interacted with for a long time
        df["staleness"] = np.log1p(df["days_since_last_interaction"])

        # Deadline urgency: inverse of days_until_deadline (clamped)
        df["deadline_urgency"] = np.where(
            df["has_deadline"] == 1,
            1.0 / np.maximum(1, df["days_until_deadline"] + 1),
            0.0,
        )

        # Task age bucket
        df["task_age_bucket"] = pd.cut(
            df["days_since_creation"],
            bins=[-1, 3, 7, 14, 30, np.inf],
            labels=[0, 1, 2, 3, 4],
        ).astype(int)

        return df
