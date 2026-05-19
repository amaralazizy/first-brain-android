"""
Evaluation module for First Brain ML pipeline.

Computes offline evaluation metrics used to compare:
  - Heuristic baseline
  - Logistic Regression baseline
  - XGBoost primary model

Metrics
-------
  roc_auc        : Area under ROC curve
  precision_at_k : Precision of top-K ranked predictions
  recall_at_k    : Recall of top-K ranked predictions
  f1             : F1 score at threshold 0.5
  avg_precision  : Average precision (area under PR curve)
  calibration    : Mean absolute calibration error
"""

from __future__ import annotations

import warnings

import numpy as np
from sklearn.metrics import (
    roc_auc_score,
    f1_score,
    average_precision_score,
)
from typing import Any


def precision_at_k(y_true: np.ndarray, scores: np.ndarray, k: int) -> float:
    """Fraction of true positives in the top-K ranked items.

    Parameters
    ----------
    y_true:
        Ground-truth binary labels (0 or 1).
    scores:
        Predicted probability scores.
    k:
        Number of top items to consider.

    Returns
    -------
    float
        Precision@K value in [0, 1].
    """
    k = min(k, len(scores))
    top_k_idx = np.argsort(scores)[::-1][:k]
    return float(np.mean(y_true[top_k_idx]))


def recall_at_k(y_true: np.ndarray, scores: np.ndarray, k: int) -> float:
    """Fraction of true positives captured in the top-K ranked items.

    Parameters
    ----------
    y_true:
        Ground-truth binary labels.
    scores:
        Predicted probability scores.
    k:
        Number of top items to consider.

    Returns
    -------
    float
        Recall@K value in [0, 1], or 0.0 if no positives exist.
    """
    total_positives = int(np.sum(y_true))
    if total_positives == 0:
        return 0.0
    k = min(k, len(scores))
    top_k_idx = np.argsort(scores)[::-1][:k]
    return float(np.sum(y_true[top_k_idx]) / total_positives)


def calibration_error(
    y_true: np.ndarray,
    scores: np.ndarray,
    n_bins: int = 10,
) -> float:
    """Mean absolute calibration error (simplified ECE).

    Bins predictions into *n_bins* equal-width intervals and measures
    the mean absolute difference between predicted probability and
    observed frequency.

    Parameters
    ----------
    y_true:
        Ground-truth binary labels.
    scores:
        Predicted probability scores.
    n_bins:
        Number of equal-width bins.

    Returns
    -------
    float
        Mean absolute calibration error in [0, 1].
    """
    bins = np.linspace(0.0, 1.0, n_bins + 1)
    errors = []
    for i, (lo, hi) in enumerate(zip(bins[:-1], bins[1:])):
        # Include the right boundary for the last bin to capture scores == 1.0
        if i == len(bins) - 2:
            mask = (scores >= lo) & (scores <= hi)
        else:
            mask = (scores >= lo) & (scores < hi)
        if mask.sum() == 0:
            continue
        pred_mean = float(scores[mask].mean())
        true_mean = float(y_true[mask].mean())
        errors.append(abs(pred_mean - true_mean))
    return float(np.mean(errors)) if errors else 0.0


def evaluate(
    y_true: np.ndarray,
    scores: np.ndarray,
    k: int = 5,
    threshold: float = 0.5,
) -> dict[str, Any]:
    """Compute all evaluation metrics for one model.

    Parameters
    ----------
    y_true:
        Ground-truth binary labels.
    scores:
        Predicted probability scores from the model.
    k:
        K value for Precision@K and Recall@K.
    threshold:
        Decision threshold used to binarise scores for F1.

    Returns
    -------
    dict
        Dictionary with keys: roc_auc, precision_at_k, recall_at_k,
        f1, avg_precision, calibration_error.
    """
    y_pred = (scores >= threshold).astype(int)
    n_classes = len(np.unique(y_true))
    if n_classes < 2:
        warnings.warn(
            "Only one class present in y_true. ROC-AUC and avg_precision are undefined; "
            "returning 0.5 as a neutral placeholder.",
            UserWarning,
            stacklevel=2,
        )
        return {
            "roc_auc": 0.5,
            f"precision_at_{k}": precision_at_k(y_true, scores, k),
            f"recall_at_{k}": recall_at_k(y_true, scores, k),
            "f1": float(f1_score(y_true, y_pred, zero_division=0)),
            "avg_precision": 0.5,
            "calibration_error": calibration_error(y_true, scores),
        }
    return {
        "roc_auc": float(roc_auc_score(y_true, scores)),
        f"precision_at_{k}": precision_at_k(y_true, scores, k),
        f"recall_at_{k}": recall_at_k(y_true, scores, k),
        "f1": float(f1_score(y_true, y_pred, zero_division=0)),
        "avg_precision": float(average_precision_score(y_true, scores)),
        "calibration_error": calibration_error(y_true, scores),
    }


def compare_models(results: dict[str, dict[str, Any]]) -> None:
    """Print a formatted comparison table for multiple model results.

    Parameters
    ----------
    results:
        Mapping of model name → metrics dict (as returned by ``evaluate``).
    """
    if not results:
        return

    metrics = list(next(iter(results.values())).keys())
    col_width = max(len(m) for m in metrics) + 2
    model_width = max(len(name) for name in results) + 2

    header = f"{'Model':<{model_width}}" + "".join(f"{m:>{col_width}}" for m in metrics)
    print(header)
    print("-" * len(header))
    for model_name, metric_dict in results.items():
        row = f"{model_name:<{model_width}}"
        for m in metrics:
            row += f"{metric_dict[m]:>{col_width}.4f}"
        print(row)
