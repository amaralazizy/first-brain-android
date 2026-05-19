"""
Data simulation module for First Brain ML pipeline.

Generates a synthetic dataset of task-day pairs that mimics realistic
user behavior patterns: task creation, skips, completions, and deadlines.
Each row represents a (task, day) observation used for model training.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from datetime import datetime, timedelta
from typing import Optional


TASK_TYPES = ["Do", "Learn", "Life", "Idea"]
URGENCY_LEVELS = ["Low", "Medium", "High"]

# Completion probability priors per task type and urgency
_TYPE_COMPLETION_PRIOR = {"Do": 0.55, "Learn": 0.45, "Life": 0.50, "Idea": 0.25}
_URGENCY_COMPLETION_PRIOR = {"Low": 0.30, "Medium": 0.55, "High": 0.75}


def simulate_dataset(
    n_tasks: int = 200,
    n_days: int = 60,
    seed: int = 42,
) -> pd.DataFrame:
    """Generate a synthetic task-day dataset.

    For each (task, observation_day) pair up to the task's completion or
    simulation end, one row is produced with:
      - task metadata features
      - time-based features computed at that observation day
      - behavioral features accumulated up to that day
      - a binary label: 1 if the task was completed within the next 24 h

    Parameters
    ----------
    n_tasks:
        Number of tasks to simulate.
    n_days:
        Total simulation horizon in days.
    seed:
        Random seed for reproducibility.

    Returns
    -------
    pd.DataFrame
        A DataFrame where each row is one (task, day) observation.
    """
    rng = np.random.default_rng(seed)
    simulation_start = datetime(2024, 1, 1)

    tasks = _generate_tasks(n_tasks, n_days, rng, simulation_start)
    rows = _simulate_interactions(tasks, n_days, rng, simulation_start)

    df = pd.DataFrame(rows)
    df = df.sort_values(["observation_day", "task_id"]).reset_index(drop=True)
    return df


def _generate_tasks(
    n_tasks: int,
    n_days: int,
    rng: np.random.Generator,
    simulation_start: datetime,
) -> list[dict]:
    """Create task metadata for all simulated tasks."""
    tasks = []
    for i in range(n_tasks):
        task_type = rng.choice(TASK_TYPES)
        urgency = rng.choice(URGENCY_LEVELS, p=[0.4, 0.4, 0.2])
        creation_day = int(rng.integers(0, max(1, n_days // 2)))
        has_deadline = rng.random() < 0.6
        deadline_day: Optional[int] = None
        if has_deadline:
            deadline_day = creation_day + int(rng.integers(3, n_days - creation_day + 1))
            deadline_day = min(deadline_day, n_days + 5)
        estimated_effort = int(rng.choice([1, 2, 3, 5, 8]))

        base_prob = (
            _TYPE_COMPLETION_PRIOR[task_type] * 0.5
            + _URGENCY_COMPLETION_PRIOR[urgency] * 0.5
        )
        tasks.append(
            {
                "task_id": i,
                "task_type": task_type,
                "urgency": urgency,
                "creation_day": creation_day,
                "has_deadline": has_deadline,
                "deadline_day": deadline_day,
                "estimated_effort": estimated_effort,
                "base_completion_prob": base_prob,
            }
        )
    return tasks


def _simulate_interactions(
    tasks: list[dict],
    n_days: int,
    rng: np.random.Generator,
    simulation_start: datetime,
) -> list[dict]:
    """Simulate day-by-day interactions for each task and collect rows.

    A task is observed on every day from its creation to the end of the
    simulation. If the user completes the task on a given day (label=1),
    the skip count and last-interaction timestamp are reset so the task
    can be recommended again on subsequent days.
    """
    rows = []

    for task in tasks:
        task_id = task["task_id"]
        creation_day = task["creation_day"]
        base_prob = task["base_completion_prob"]
        deadline_day = task["deadline_day"]

        skip_count = 0
        last_interaction_day: Optional[int] = None

        for day in range(creation_day, n_days):
            days_since_creation = day - creation_day
            days_since_last = (
                day - last_interaction_day if last_interaction_day is not None else days_since_creation
            )

            # Deadline proximity modifier
            deadline_proximity = 0.0
            days_until_deadline = None
            is_overdue = False
            if deadline_day is not None:
                days_until_deadline = deadline_day - day
                is_overdue = days_until_deadline < 0
                if days_until_deadline <= 0:
                    deadline_proximity = 1.0
                elif days_until_deadline <= 3:
                    deadline_proximity = 0.8
                elif days_until_deadline <= 7:
                    deadline_proximity = 0.5
                else:
                    deadline_proximity = max(0.0, 1.0 - days_until_deadline / 30.0)

            # Forgotten task: increases resurfacing probability
            forgotten_boost = min(0.3, days_since_last * 0.02)

            # Skip penalty
            skip_penalty = min(0.4, skip_count * 0.05)

            # Weekday productivity
            obs_date = simulation_start + timedelta(days=day)
            weekday = obs_date.weekday()  # 0=Monday, 6=Sunday
            is_weekend = weekday >= 5
            weekday_prod = 0.6 if is_weekend else 1.0

            completion_prob = min(
                0.95,
                (base_prob + deadline_proximity * 0.3 + forgotten_boost) * weekday_prod
                - skip_penalty,
            )
            completion_prob = max(0.05, completion_prob)

            label = int(rng.random() < completion_prob)

            rows.append(
                {
                    "task_id": task_id,
                    "observation_day": day,
                    "task_type": task["task_type"],
                    "urgency": task["urgency"],
                    "estimated_effort": task["estimated_effort"],
                    "has_deadline": int(task["has_deadline"]),
                    "days_since_creation": days_since_creation,
                    "days_since_last_interaction": days_since_last,
                    "days_until_deadline": days_until_deadline if days_until_deadline is not None else -1,
                    "is_overdue": int(is_overdue),
                    "deadline_proximity": deadline_proximity,
                    "skip_count": skip_count,
                    "weekday": weekday,
                    "is_weekend": int(is_weekend),
                    "label": label,
                }
            )

            if label == 1:
                # Task completed: reset interaction state for next cycle
                last_interaction_day = day
                skip_count = 0
            else:
                # Simulate skip vs ignore
                if rng.random() < 0.4:
                    skip_count += 1
                    last_interaction_day = day

    return rows
