"""Tests for the end-to-end pipeline."""

import pytest

from pipeline import run_pipeline, build_time_split
from data_simulation import simulate_dataset


def test_build_time_split_no_overlap():
    df = simulate_dataset(n_tasks=50, n_days=30, seed=40)
    train, val, test = build_time_split(df)
    assert train["observation_day"].max() < val["observation_day"].min()
    assert val["observation_day"].max() < test["observation_day"].min()


def test_build_time_split_covers_all_rows():
    df = simulate_dataset(n_tasks=50, n_days=30, seed=41)
    train, val, test = build_time_split(df)
    assert len(train) + len(val) + len(test) == len(df)


def test_run_pipeline_returns_three_models():
    results = run_pipeline(n_tasks=60, n_days=30, k=3, seed=50, verbose=False)
    assert set(results.keys()) == {"Heuristic", "LogisticRegression", "XGBoost"}


def test_run_pipeline_metrics_keys():
    results = run_pipeline(n_tasks=60, n_days=30, k=3, seed=51, verbose=False)
    expected = {"roc_auc", "precision_at_3", "recall_at_3", "f1", "avg_precision", "calibration_error"}
    for model_name, metrics in results.items():
        assert expected == set(metrics.keys()), f"Missing keys for {model_name}"


def test_run_pipeline_roc_auc_range():
    results = run_pipeline(n_tasks=150, n_days=60, k=3, seed=52, verbose=False)
    for model_name, metrics in results.items():
        assert 0.0 <= metrics["roc_auc"] <= 1.0, f"ROC-AUC out of range for {model_name}"


def test_run_pipeline_xgboost_not_worse_than_heuristic():
    """XGBoost should achieve at least comparable ROC-AUC to the heuristic baseline."""
    results = run_pipeline(n_tasks=300, n_days=90, k=5, seed=53, verbose=False)
    # XGBoost should be >= heuristic - small tolerance for dataset variance
    assert results["XGBoost"]["roc_auc"] >= results["Heuristic"]["roc_auc"] - 0.10
