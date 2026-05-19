"""
Models module for First Brain ML pipeline.

Exposes the single production model:
  XGBoostModel — gradient-boosted trees, chosen for tabular behavioral
                 features and reliable performance on small-to-medium data.

Interface:
  .fit(X, y, X_val=, y_val=)  — train
  .predict_proba(X)            — probability scores for class 1
  .predict(X, threshold)       — binary predictions
"""

from __future__ import annotations

from typing import Optional

import numpy as np
from xgboost import XGBClassifier


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
