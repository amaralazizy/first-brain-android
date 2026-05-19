"""Tests for the evaluation module."""

import numpy as np
import pytest

from evaluation import (
    precision_at_k,
    recall_at_k,
    calibration_error,
    evaluate,
    compare_models,
)


@pytest.fixture
def sample_labels_scores():
    rng = np.random.default_rng(30)
    y = rng.integers(0, 2, size=100)
    scores = rng.uniform(0, 1, size=100)
    return y, scores


def test_precision_at_k_range(sample_labels_scores):
    y, scores = sample_labels_scores
    p = precision_at_k(y, scores, k=5)
    assert 0.0 <= p <= 1.0


def test_precision_at_k_all_positive():
    y = np.ones(10, dtype=int)
    scores = np.arange(10, dtype=float)
    assert precision_at_k(y, scores, k=5) == 1.0


def test_precision_at_k_all_negative():
    y = np.zeros(10, dtype=int)
    scores = np.arange(10, dtype=float)
    assert precision_at_k(y, scores, k=5) == 0.0


def test_precision_at_k_k_larger_than_n():
    y = np.array([1, 0, 1])
    scores = np.array([0.9, 0.8, 0.7])
    # k=10 > len=3, should clip to len
    p = precision_at_k(y, scores, k=10)
    assert 0.0 <= p <= 1.0


def test_recall_at_k_range(sample_labels_scores):
    y, scores = sample_labels_scores
    r = recall_at_k(y, scores, k=5)
    assert 0.0 <= r <= 1.0


def test_recall_at_k_no_positives():
    y = np.zeros(10, dtype=int)
    scores = np.arange(10, dtype=float)
    assert recall_at_k(y, scores, k=5) == 0.0


def test_recall_at_k_all_positives_in_top():
    y = np.array([1, 1, 1, 0, 0])
    scores = np.array([0.9, 0.8, 0.7, 0.6, 0.5])
    assert recall_at_k(y, scores, k=3) == pytest.approx(1.0)


def test_calibration_error_perfect():
    # Perfect calibration: score equals label (only 0 or 1)
    y = np.array([0, 0, 1, 1] * 25)
    scores = y.astype(float)
    error = calibration_error(y, scores)
    assert error == pytest.approx(0.0, abs=1e-6)


def test_calibration_error_range(sample_labels_scores):
    y, scores = sample_labels_scores
    error = calibration_error(y, scores)
    assert 0.0 <= error <= 1.0


def test_calibration_error_empty_bins():
    y = np.array([0, 1])
    scores = np.array([0.0, 1.0])
    error = calibration_error(y, scores, n_bins=20)
    assert 0.0 <= error <= 1.0


def test_evaluate_returns_expected_keys(sample_labels_scores):
    y, scores = sample_labels_scores
    result = evaluate(y, scores, k=5)
    expected_keys = {"roc_auc", "precision_at_5", "recall_at_5", "f1", "avg_precision", "calibration_error"}
    assert expected_keys == set(result.keys())


def test_evaluate_roc_auc_range(sample_labels_scores):
    y, scores = sample_labels_scores
    result = evaluate(y, scores, k=5)
    assert 0.0 <= result["roc_auc"] <= 1.0


def test_evaluate_f1_range(sample_labels_scores):
    y, scores = sample_labels_scores
    result = evaluate(y, scores, k=5)
    assert 0.0 <= result["f1"] <= 1.0


def test_compare_models_runs_without_error(capsys, sample_labels_scores):
    y, scores = sample_labels_scores
    results = {
        "ModelA": evaluate(y, scores, k=5),
        "ModelB": evaluate(y, scores, k=5),
    }
    compare_models(results)
    captured = capsys.readouterr()
    assert "ModelA" in captured.out
    assert "ModelB" in captured.out


def test_calibration_error_with_continuous_scores():
    """Test calibration with realistic continuous probability scores."""
    rng = np.random.default_rng(42)
    scores = rng.uniform(0, 1, size=500)
    # Labels drawn from Bernoulli(scores) → well-calibrated predictor
    y = rng.binomial(1, scores)
    error = calibration_error(y, scores)
    # Well-calibrated scores should have low calibration error
    assert error < 0.15


def test_calibration_error_boundary_score_one():
    """Scores exactly at 1.0 must be included in the calibration bins."""
    y = np.array([1, 1, 0])
    scores = np.array([1.0, 1.0, 0.5])
    # Should not raise and should return a finite number
    error = calibration_error(y, scores)
    assert np.isfinite(error)


def test_compare_models_empty_dict(capsys):
    compare_models({})
    captured = capsys.readouterr()
    assert captured.out == ""

