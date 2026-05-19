"""Tests for the models module."""

import numpy as np
import pandas as pd
import pytest

from data_simulation import simulate_dataset
from features import FeatureEngineer
from models import XGBoostModel


@pytest.fixture
def data():
    df = simulate_dataset(n_tasks=60, n_days=30, seed=20)
    engineer = FeatureEngineer()
    X = engineer.fit_transform(df)
    y = df["label"].to_numpy()
    return df, X, y

# XGBoostModel
# ---------------------------------------------------------------------------

class TestXGBoostModel:
    def test_fit_predict_proba(self, data):
        _, X, y = data
        model = XGBoostModel(n_estimators=50)
        model.fit(X, y)
        scores = model.predict_proba(X)
        assert scores.shape == (len(y),)

    def test_predict_proba_in_unit_interval(self, data):
        _, X, y = data
        model = XGBoostModel(n_estimators=50)
        model.fit(X, y)
        scores = model.predict_proba(X)
        assert np.all(scores >= 0) and np.all(scores <= 1)

    def test_predict_returns_binary(self, data):
        _, X, y = data
        model = XGBoostModel(n_estimators=50)
        model.fit(X, y)
        preds = model.predict(X)
        assert set(preds).issubset({0, 1})

    def test_fit_returns_self(self, data):
        _, X, y = data
        model = XGBoostModel(n_estimators=50)
        result = model.fit(X, y)
        assert result is model

    def test_feature_importances_shape(self, data):
        _, X, y = data
        model = XGBoostModel(n_estimators=50)
        model.fit(X, y)
        assert model.feature_importances_.shape == (X.shape[1],)

    def test_fit_with_validation_set(self, data):
        _, X, y = data
        split = len(X) // 2
        model = XGBoostModel(n_estimators=50)
        model.fit(X[:split], y[:split], X_val=X[split:], y_val=y[split:])
        scores = model.predict_proba(X[split:])
        assert scores.shape == (len(X[split:]),)
