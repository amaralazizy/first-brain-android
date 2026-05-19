"""Tests for the data simulation module."""

import numpy as np
import pandas as pd
import pytest

from data_simulation import simulate_dataset, TASK_TYPES, URGENCY_LEVELS


def test_simulate_dataset_returns_dataframe():
    df = simulate_dataset(n_tasks=20, n_days=15, seed=0)
    assert isinstance(df, pd.DataFrame)


def test_simulate_dataset_has_expected_columns():
    df = simulate_dataset(n_tasks=20, n_days=15, seed=0)
    required = {
        "task_id",
        "observation_day",
        "task_type",
        "urgency",
        "estimated_effort",
        "has_deadline",
        "days_since_creation",
        "days_since_last_interaction",
        "days_until_deadline",
        "is_overdue",
        "deadline_proximity",
        "skip_count",
        "weekday",
        "is_weekend",
        "label",
    }
    assert required.issubset(set(df.columns))


def test_simulate_dataset_label_is_binary():
    df = simulate_dataset(n_tasks=50, n_days=20, seed=1)
    assert set(df["label"].unique()).issubset({0, 1})


def test_simulate_dataset_task_types_valid():
    df = simulate_dataset(n_tasks=50, n_days=20, seed=2)
    assert set(df["task_type"].unique()).issubset(set(TASK_TYPES))


def test_simulate_dataset_urgency_levels_valid():
    df = simulate_dataset(n_tasks=50, n_days=20, seed=3)
    assert set(df["urgency"].unique()).issubset(set(URGENCY_LEVELS))


def test_simulate_dataset_observation_day_ordered():
    df = simulate_dataset(n_tasks=30, n_days=20, seed=4)
    days = df["observation_day"].to_numpy()
    assert (days[1:] >= days[:-1]).all()


def test_simulate_dataset_reproducible():
    df1 = simulate_dataset(n_tasks=30, n_days=20, seed=99)
    df2 = simulate_dataset(n_tasks=30, n_days=20, seed=99)
    pd.testing.assert_frame_equal(df1, df2)


def test_simulate_dataset_different_seeds_differ():
    df1 = simulate_dataset(n_tasks=30, n_days=20, seed=1)
    df2 = simulate_dataset(n_tasks=30, n_days=20, seed=2)
    assert not df1["label"].equals(df2["label"])


def test_simulate_dataset_non_empty():
    df = simulate_dataset(n_tasks=10, n_days=10, seed=5)
    assert len(df) > 0


def test_simulate_dataset_deadline_proximity_bounded():
    df = simulate_dataset(n_tasks=50, n_days=30, seed=6)
    assert df["deadline_proximity"].between(0.0, 1.0).all()


def test_simulate_dataset_days_since_creation_non_negative():
    df = simulate_dataset(n_tasks=50, n_days=30, seed=7)
    assert (df["days_since_creation"] >= 0).all()


def test_simulate_dataset_weekday_bounded():
    df = simulate_dataset(n_tasks=50, n_days=30, seed=8)
    assert df["weekday"].between(0, 6).all()
