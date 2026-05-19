"""
End-to-end training and evaluation pipeline for First Brain ML Checkpoint 1.

Workflow
--------
1. Simulate a synthetic task-day dataset.
2. Apply feature engineering.
3. Time-aware train / validation / test split.
4. Train and evaluate three models:
      - Heuristic (rule-based baseline)
      - Logistic Regression (linear baseline)
      - XGBoost (primary model)
5. Print a comparison table of offline evaluation metrics.

Run as a script
---------------
    python -m ml.pipeline

Or import and call ``run_pipeline()`` programmatically.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from data_simulation import simulate_dataset
from features import FeatureEngineer
from models import HeuristicModel, LogisticRegressionModel, XGBoostModel
from evaluation import evaluate, compare_models


def build_time_split(
    df: pd.DataFrame,
    train_frac: float = 0.70,
    val_frac: float = 0.15,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Split the dataset chronologically to prevent data leakage.

    The split is made on ``observation_day`` so that the model is never
    trained on future observations — a requirement for realistic evaluation
    of a time-series recommender system.

    Parameters
    ----------
    df:
        Full task-day DataFrame produced by ``simulate_dataset``.
    train_frac:
        Fraction of days allocated to training.
    val_frac:
        Fraction of days allocated to validation.
        The rest becomes the test set.

    Returns
    -------
    (train_df, val_df, test_df) : tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]
    """
    days = df["observation_day"].unique()
    days_sorted = np.sort(days)
    n = len(days_sorted)

    train_end = days_sorted[int(n * train_frac) - 1]
    val_end = days_sorted[int(n * (train_frac + val_frac)) - 1]

    train_df = df[df["observation_day"] <= train_end].copy()
    val_df = df[(df["observation_day"] > train_end) & (df["observation_day"] <= val_end)].copy()
    test_df = df[df["observation_day"] > val_end].copy()
    return train_df, val_df, test_df


def run_pipeline(
    n_tasks: int = 200,
    n_days: int = 90,
    k: int = 5,
    seed: int = 42,
    verbose: bool = True,
) -> dict[str, dict]:
    """Run the complete ML pipeline and return evaluation results.

    Parameters
    ----------
    n_tasks:
        Number of tasks to simulate.
    n_days:
        Simulation horizon in days.
    k:
        K value used for Precision@K and Recall@K.
    seed:
        Random seed for reproducibility.
    verbose:
        If True, print progress and results to stdout.

    Returns
    -------
    dict
        Mapping of model name → metrics dict.
    """
    # ------------------------------------------------------------------
    # 1. Data simulation
    # ------------------------------------------------------------------
    if verbose:
        print(f"Simulating dataset: {n_tasks} tasks × {n_days} days ...")
    df = simulate_dataset(n_tasks=n_tasks, n_days=n_days, seed=seed)
    if verbose:
        pos_rate = df["label"].mean()
        print(f"  Total observations : {len(df):,}")
        print(f"  Positive rate      : {pos_rate:.2%}")

    # ------------------------------------------------------------------
    # 2. Time-aware split
    # ------------------------------------------------------------------
    train_df, val_df, test_df = build_time_split(df)
    if verbose:
        print(
            f"  Train / Val / Test : {len(train_df):,} / {len(val_df):,} / {len(test_df):,} rows"
        )

    # ------------------------------------------------------------------
    # 3. Feature engineering
    # ------------------------------------------------------------------
    engineer = FeatureEngineer()
    X_train = engineer.fit_transform(train_df)
    X_val = engineer.transform(val_df)
    X_test = engineer.transform(test_df)

    y_train = train_df["label"].to_numpy()
    y_val = val_df["label"].to_numpy()
    y_test = test_df["label"].to_numpy()

    # ------------------------------------------------------------------
    # 4. Train models
    # ------------------------------------------------------------------
    if verbose:
        print("\nTraining models ...")

    heuristic = HeuristicModel()
    heuristic.fit(train_df, y_train)

    logreg = LogisticRegressionModel()
    logreg.fit(X_train, y_train)

    xgb = XGBoostModel()
    xgb.fit(X_train, y_train, X_val=X_val, y_val=y_val)

    # ------------------------------------------------------------------
    # 5. Evaluate on test set
    # ------------------------------------------------------------------
    if verbose:
        print("Evaluating on test set ...")

    heuristic_scores = heuristic.predict_proba(test_df)
    logreg_scores = logreg.predict_proba(X_test)
    xgb_scores = xgb.predict_proba(X_test)

    results = {
        "Heuristic": evaluate(y_test, heuristic_scores, k=k),
        "LogisticRegression": evaluate(y_test, logreg_scores, k=k),
        "XGBoost": evaluate(y_test, xgb_scores, k=k),
    }

    if verbose:
        print("\n=== Evaluation Results (Test Set) ===\n")
        compare_models(results)

        # Feature importance for XGBoost
        importance = xgb.feature_importances_
        feature_names = engineer.feature_names_
        top_idx = np.argsort(importance)[::-1][:10]
        print("\n=== XGBoost Top-10 Feature Importances ===")
        for rank, i in enumerate(top_idx, 1):
            print(f"  {rank:2d}. {feature_names[i]:<35s} {importance[i]:.4f}")

    return results


if __name__ == "__main__":
    run_pipeline()
