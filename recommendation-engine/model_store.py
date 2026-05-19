"""
Persistence utilities for trained model artifacts.

Artifacts are stored in a `models/` directory relative to this file:
  models/xgb_model.joblib        — fitted XGBoostModel
  models/feature_engineer.joblib — fitted FeatureEngineer
  models/metrics.json            — last training metrics dict
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

import joblib

from features import FeatureEngineer
from models import XGBoostModel

logger = logging.getLogger("first-brain.model_store")

_MODELS_DIR = Path(__file__).parent / "models"
_MODEL_PATH = _MODELS_DIR / "xgb_model.joblib"
_ENGINEER_PATH = _MODELS_DIR / "feature_engineer.joblib"
_METRICS_PATH = _MODELS_DIR / "metrics.json"


def save(model: XGBoostModel, engineer: FeatureEngineer, metrics: dict[str, Any]) -> None:
    _MODELS_DIR.mkdir(exist_ok=True)
    joblib.dump(model, _MODEL_PATH)
    joblib.dump(engineer, _ENGINEER_PATH)
    _METRICS_PATH.write_text(json.dumps(metrics, indent=2))
    logger.info("Saved model artifacts to %s", _MODELS_DIR)


def load() -> tuple[XGBoostModel, FeatureEngineer, dict[str, Any]]:
    """Load persisted artifacts. Raises FileNotFoundError if not found."""
    model: XGBoostModel = joblib.load(_MODEL_PATH)
    engineer: FeatureEngineer = joblib.load(_ENGINEER_PATH)
    metrics: dict[str, Any] = json.loads(_METRICS_PATH.read_text())
    logger.info("Loaded model artifacts from %s", _MODELS_DIR)
    return model, engineer, metrics


def exists() -> bool:
    return _MODEL_PATH.exists() and _ENGINEER_PATH.exists()
