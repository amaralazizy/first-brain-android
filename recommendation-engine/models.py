"""
Models module for First Brain ML pipeline.

Three models are implemented:
  1. HeuristicModel      — rule-based scoring baseline (no training)
  2. LogisticRegressionModel — linear ML baseline
  3. XGBoostModel        — primary model (gradient-boosted trees)

All models expose a common interface:
  .fit(X, y)             — train (no-op for heuristic)
  .predict_proba(X)      — return probability scores for class 1
  .predict(X, threshold) — return binary predictions
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from xgboost import XGBClassifier
from typing import Optional


# ---------------------------------------------------------------------------
# Heuristic (rule-based) baseline
# ---------------------------------------------------------------------------

class HeuristicModel:
    """Rule-based scoring baseline.

    Score = w_urgency * urgency_score
           + w_deadline * deadline_proximity
           + w_age * log(1 + days_since_creation)
           - w_skip * skip_count

    The score is normalised to [0, 1] via a sigmoid so that it can be
    compared with probabilistic models using the same evaluation metrics.

    Parameters
    ----------
    w_urgency, w_deadline, w_age, w_skip:
        Hand-tuned weights. Defaults reflect the PRD priority ordering.
    """

    def __init__(
        self,
        w_urgency: float = 0.35,
        w_deadline: float = 0.40,
        w_age: float = 0.15,
        w_skip: float = 0.10,
    ) -> None:
        self.w_urgency = w_urgency
        self.w_deadline = w_deadline
        self.w_age = w_age
        self.w_skip = w_skip

    # Heuristic needs raw DataFrame columns, not encoded arrays
    def fit(self, df: pd.DataFrame, y: Optional[np.ndarray] = None) -> "HeuristicModel":  # noqa: ARG002
        """No-op — heuristic has no trainable parameters."""
        return self

    def predict_proba(self, df: pd.DataFrame) -> np.ndarray:
        """Compute heuristic priority scores normalised to [0, 1]."""
        urgency_map = {"Low": 1, "Medium": 2, "High": 3}
        urgency_score = df["urgency"].map(urgency_map).fillna(1).to_numpy() / 3.0
        deadline_proximity = df["deadline_proximity"].to_numpy()
        age_score = np.log1p(df["days_since_creation"].to_numpy()) / np.log1p(60)
        skip_score = np.minimum(df["skip_count"].to_numpy() / 10.0, 1.0)

        raw = (
            self.w_urgency * urgency_score
            + self.w_deadline * deadline_proximity
            + self.w_age * age_score
            - self.w_skip * skip_score
        )
        # Sigmoid to map to (0, 1)
        return 1.0 / (1.0 + np.exp(-raw * 6 + 3))

    def predict(self, df: pd.DataFrame, threshold: float = 0.5) -> np.ndarray:
        return (self.predict_proba(df) >= threshold).astype(int)


# ---------------------------------------------------------------------------
# Logistic Regression baseline
# ---------------------------------------------------------------------------

class LogisticRegressionModel:
    """Linear ML baseline using sklearn LogisticRegression.

    Features are standardised internally so raw numeric ranges do not
    dominate the weight vector.
    """

    def __init__(self, C: float = 1.0, max_iter: int = 1000, random_state: int = 42) -> None:
        self._pipeline = Pipeline([
            ("scaler", StandardScaler()),
            ("clf", LogisticRegression(C=C, max_iter=max_iter, random_state=random_state)),
        ])

    def fit(self, X: np.ndarray, y: np.ndarray) -> "LogisticRegressionModel":
        self._pipeline.fit(X, y)
        return self

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        return self._pipeline.predict_proba(X)[:, 1]

    def predict(self, X: np.ndarray, threshold: float = 0.5) -> np.ndarray:
        return (self.predict_proba(X) >= threshold).astype(int)

    @property
    def coef_(self) -> np.ndarray:
        return self._pipeline.named_steps["clf"].coef_[0]


# ---------------------------------------------------------------------------
# XGBoost primary model
# ---------------------------------------------------------------------------

class XGBoostModel:
    """Primary model: gradient-boosted trees via XGBoost.

    Chosen because tabular behavioral features benefit from tree-based
    interaction modelling, and XGBoost generalises well on small-to-medium
    datasets.

    Parameters
    ----------
    n_estimators:
        Number of boosting rounds.
    max_depth:
        Maximum tree depth.
    learning_rate:
        Step-size shrinkage.
    subsample:
        Fraction of training samples used per tree.
    colsample_bytree:
        Fraction of features used per tree.
    early_stopping_rounds:
        Stop early if validation AUC does not improve.
    random_state:
        Random seed.
    """

    def __init__(
        self,
        n_estimators: int = 300,
        max_depth: int = 4,
        learning_rate: float = 0.05,
        subsample: float = 0.8,
        colsample_bytree: float = 0.8,
        early_stopping_rounds: int = 20,
        random_state: int = 42,
    ) -> None:
        self._model = XGBClassifier(
            n_estimators=n_estimators,
            max_depth=max_depth,
            learning_rate=learning_rate,
            subsample=subsample,
            colsample_bytree=colsample_bytree,
            eval_metric="logloss",
            random_state=random_state,
            verbosity=0,
        )
        self.early_stopping_rounds = early_stopping_rounds

    def fit(
        self,
        X_train: np.ndarray,
        y_train: np.ndarray,
        X_val: Optional[np.ndarray] = None,
        y_val: Optional[np.ndarray] = None,
    ) -> "XGBoostModel":
        """Train XGBoost, optionally with early stopping on a validation set."""
        fit_kwargs: dict = {}
        if X_val is not None and y_val is not None:
            fit_kwargs["eval_set"] = [(X_val, y_val)]
            fit_kwargs["verbose"] = False
            self._model.set_params(early_stopping_rounds=self.early_stopping_rounds)
        self._model.fit(X_train, y_train, **fit_kwargs)
        return self

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        return self._model.predict_proba(X)[:, 1]

    def predict(self, X: np.ndarray, threshold: float = 0.5) -> np.ndarray:
        return (self.predict_proba(X) >= threshold).astype(int)

    @property
    def feature_importances_(self) -> np.ndarray:
        return self._model.feature_importances_
