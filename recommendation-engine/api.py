"""
FastAPI inference server for the First Brain recommendation engine.

Endpoints
---------
GET  /health     — liveness check + model metadata
POST /recommend  — score task feature vectors, return ranked list with SHAP explanations
POST /train      — retrain from scratch on synthetic data, persist artifacts
GET  /metrics    — evaluation metrics from the last training run
POST /feedback   — record a user interaction (complete / skip) for future retraining

Startup behaviour
-----------------
If a persisted model exists in models/ it is loaded (fast).
Otherwise the model is trained on synthetic data and saved (one-time, ~5 s).

Run from recommendation-engine/:
    uvicorn api:app --reload --port 8000
"""

from __future__ import annotations

import json
import logging
import time
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Literal

import numpy as np
import pandas as pd
import shap
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import model_store
from data_simulation import simulate_dataset
from evaluation import evaluate
from features import FeatureEngineer
from models import XGBoostModel


logging.basicConfig(level=logging.INFO, format="%(levelname)s  %(name)s  %(message)s")
logger = logging.getLogger("first-brain.api")

_FEEDBACK_PATH = Path(__file__).parent / "models" / "feedback.jsonl"

# ── Global model state ────────────────────────────────────────────────────────
_model: XGBoostModel | None = None
_engineer: FeatureEngineer | None = None
_metrics: dict[str, Any] = {}
_trained_at: str = ""
_shap_explainer: shap.TreeExplainer | None = None


def _train_and_persist(n_tasks: int = 300, n_days: int = 90, seed: int = 42) -> dict[str, Any]:
    """Train XGBoost on synthetic data, evaluate, persist, return metrics."""
    global _model, _engineer, _metrics, _trained_at, _shap_explainer

    logger.info("Simulating dataset (%d tasks × %d days) …", n_tasks, n_days)
    df = simulate_dataset(n_tasks=n_tasks, n_days=n_days, seed=seed)

    days = np.sort(df["observation_day"].unique())
    n = len(days)
    train_end = days[int(n * 0.70) - 1]
    val_end = days[int(n * 0.85) - 1]
    train_df = df[df["observation_day"] <= train_end].copy()
    val_df = df[(df["observation_day"] > train_end) & (df["observation_day"] <= val_end)].copy()
    test_df = df[df["observation_day"] > val_end].copy()

    engineer = FeatureEngineer()
    X_train = engineer.fit_transform(train_df)
    X_val = engineer.transform(val_df)
    X_test = engineer.transform(test_df)
    y_train = train_df["label"].to_numpy()
    y_val = val_df["label"].to_numpy()
    y_test = test_df["label"].to_numpy()

    model = XGBoostModel()
    model.fit(X_train, y_train, X_val=X_val, y_val=y_val)

    test_scores = model.predict_proba(X_test)
    metrics = evaluate(y_test, test_scores, k=5)
    metrics["n_train"] = int(len(train_df))
    metrics["n_test"] = int(len(test_df))
    metrics["n_features"] = int(X_train.shape[1])
    metrics["feature_names"] = engineer.feature_names_
    metrics["feature_importances"] = [
        {"feature": name, "importance": round(float(imp), 6)}
        for name, imp in sorted(
            zip(engineer.feature_names_, model.feature_importances_),
            key=lambda x: x[1],
            reverse=True,
        )
    ]

    trained_at = datetime.now(timezone.utc).isoformat()
    metrics["trained_at"] = trained_at

    model_store.save(model, engineer, metrics)

    _model = model
    _engineer = engineer
    _metrics = metrics
    _trained_at = trained_at
    _shap_explainer = shap.TreeExplainer(_model._model)

    logger.info(
        "Training complete — ROC-AUC=%.4f  P@5=%.4f  features=%d",
        metrics["roc_auc"],
        metrics["precision_at_5"],
        X_train.shape[1],
    )
    return metrics


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _model, _engineer, _metrics, _trained_at, _shap_explainer

    if model_store.exists():
        logger.info("Loading persisted model …")
        _model, _engineer, _metrics = model_store.load()
        _trained_at = _metrics.get("trained_at", "unknown")
        _shap_explainer = shap.TreeExplainer(_model._model)
        logger.info("Model loaded — ROC-AUC=%.4f", _metrics.get("roc_auc", 0))
    else:
        logger.info("No persisted model found — training from scratch …")
        _train_and_persist()

    yield


# ── App ───────────────────────────────────────────────────────────────────────

app = FastAPI(
    title="First Brain Recommendation Engine",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:3001"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


# ── Schemas ───────────────────────────────────────────────────────────────────

class TaskFeatures(BaseModel):
    id: str
    days_since_creation: float
    days_since_last_interaction: float
    days_until_deadline: float
    is_overdue: int
    deadline_proximity: float
    skip_count: int
    estimated_effort: int
    has_deadline: int
    weekday: int
    is_weekend: int
    task_type: str  # Do | Learn | Life | Idea
    urgency: str    # Low | Medium | High


class RecommendRequest(BaseModel):
    tasks: list[TaskFeatures]
    top_k: int = 5


class FeatureContribution(BaseModel):
    feature: str
    shap_value: float


class ScoredTask(BaseModel):
    id: str
    score: float
    explanation: list[FeatureContribution]


class FeedbackEvent(BaseModel):
    task_id: str
    action: Literal["complete", "skip"]
    score: float | None = None


class TrainResponse(BaseModel):
    metrics: dict[str, Any]
    trained_at: str
    duration_seconds: float


# ── Helpers ───────────────────────────────────────────────────────────────────

def _top_shap(shap_values: np.ndarray, feature_names: list[str], top_n: int = 3) -> list[FeatureContribution]:
    pairs = sorted(
        zip(feature_names, shap_values.tolist()),
        key=lambda x: abs(x[1]),
        reverse=True,
    )
    return [FeatureContribution(feature=f, shap_value=round(v, 4)) for f, v in pairs[:top_n]]


# ── Routes ────────────────────────────────────────────────────────────────────

@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model_ready": _model is not None,
        "trained_at": _trained_at,
        "roc_auc": _metrics.get("roc_auc"),
        "precision_at_5": _metrics.get("precision_at_5"),
    }


@app.get("/metrics")
def metrics() -> dict:
    if not _metrics:
        raise HTTPException(status_code=404, detail="No metrics available yet")
    return _metrics


@app.post("/recommend", response_model=list[ScoredTask])
def recommend(req: RecommendRequest) -> list[ScoredTask]:
    if _model is None or _engineer is None or _shap_explainer is None:
        raise HTTPException(status_code=503, detail="Model not ready")
    if not req.tasks:
        return []

    rows = [t.model_dump() for t in req.tasks]
    df = pd.DataFrame(rows)
    ids = df["id"].tolist()
    X = _engineer.transform(df.drop(columns=["id"]))

    scores: np.ndarray = _model.predict_proba(X)
    shap_values: np.ndarray = _shap_explainer.shap_values(X)

    order = np.argsort(scores)[::-1][: req.top_k]
    feature_names: list[str] = _engineer.feature_names_

    return [
        ScoredTask(
            id=ids[i],
            score=round(float(scores[i]), 4),
            explanation=_top_shap(shap_values[i], feature_names),
        )
        for i in order
    ]


@app.post("/train", response_model=TrainResponse)
def train() -> TrainResponse:
    t0 = time.perf_counter()
    new_metrics = _train_and_persist()
    return TrainResponse(
        metrics=new_metrics,
        trained_at=_trained_at,
        duration_seconds=round(time.perf_counter() - t0, 2),
    )


@app.post("/feedback", status_code=204)
def feedback(event: FeedbackEvent) -> None:
    _FEEDBACK_PATH.parent.mkdir(exist_ok=True)
    record = {
        "task_id": event.task_id,
        "action": event.action,
        "score": event.score,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    with _FEEDBACK_PATH.open("a") as f:
        f.write(json.dumps(record) + "\n")
