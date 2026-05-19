"""Tests for the feature engineering module."""

import numpy as np
import pandas as pd
import pytest

from data_simulation import simulate_dataset
from features import FeatureEngineer, TASK_TYPES, URGENCY_LEVELS, _NUMERIC_COLS, _CAT_COLS


@pytest.fixture
def sample_df():
    return simulate_dataset(n_tasks=40, n_days=20, seed=10)


def test_fit_transform_returns_ndarray(sample_df):
    engineer = FeatureEngineer()
    X = engineer.fit_transform(sample_df)
    assert isinstance(X, np.ndarray)


def test_fit_transform_correct_shape(sample_df):
    engineer = FeatureEngineer()
    X = engineer.fit_transform(sample_df)
    expected_cols = len(_NUMERIC_COLS) + len(TASK_TYPES) + len(URGENCY_LEVELS)
    assert X.shape == (len(sample_df), expected_cols)


def test_feature_names_set_after_fit(sample_df):
    engineer = FeatureEngineer()
    engineer.fit_transform(sample_df)
    assert len(engineer.feature_names_) > 0


def test_transform_before_fit_raises(sample_df):
    engineer = FeatureEngineer()
    with pytest.raises(RuntimeError):
        engineer.transform(sample_df)


def test_transform_matches_fit_transform_shape(sample_df):
    engineer = FeatureEngineer()
    X_fit = engineer.fit_transform(sample_df)
    X_tr = engineer.transform(sample_df)
    assert X_fit.shape == X_tr.shape


def test_transform_same_data_identical(sample_df):
    engineer = FeatureEngineer()
    X_fit = engineer.fit_transform(sample_df)
    X_tr = engineer.transform(sample_df)
    np.testing.assert_array_almost_equal(X_fit, X_tr)


def test_add_derived_features_does_not_mutate(sample_df):
    original_cols = set(sample_df.columns)
    FeatureEngineer.add_derived_features(sample_df)
    # original df unchanged
    assert set(sample_df.columns) == original_cols


def test_add_derived_features_adds_columns(sample_df):
    enriched = FeatureEngineer.add_derived_features(sample_df)
    new_cols = {"urgency_score", "staleness", "deadline_urgency", "task_age_bucket"}
    assert new_cols.issubset(set(enriched.columns))


def test_add_derived_features_urgency_score_range(sample_df):
    enriched = FeatureEngineer.add_derived_features(sample_df)
    assert enriched["urgency_score"].between(1, 3).all()


def test_add_derived_features_staleness_non_negative(sample_df):
    enriched = FeatureEngineer.add_derived_features(sample_df)
    assert (enriched["staleness"] >= 0).all()


def test_add_derived_features_deadline_urgency_non_negative(sample_df):
    enriched = FeatureEngineer.add_derived_features(sample_df)
    assert (enriched["deadline_urgency"] >= 0).all()


def test_no_nan_in_feature_matrix(sample_df):
    engineer = FeatureEngineer()
    X = engineer.fit_transform(sample_df)
    assert not np.isnan(X).any()
