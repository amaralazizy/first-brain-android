# ADR-001: Local-only MVVM + Room

## Status

Accepted — 2026-05-18.

## Context

CSE461 Spring 2026 requires a native Android app built on Kotlin + MVVM +
Fragments + XML + Room, with all DB / network work on coroutines and the
IO dispatcher. Remote data is an *optional* extension scored under the
same offline-first rubric.

The First Brain concept originally had a TanStack Start web app and a
Python XGBoost service backing it. For the CSE461 deliverable we wanted a
single repository that the instructor could open in Android Studio and
build with no external services running.

## Decision

Ship a fully local Android app:

- **Room is the only data store.** Two related entities: `tasks` and
  `task_interactions` (1:N, cascade delete).
- **No Retrofit, no OkHttp, no INTERNET permission.** The Manifest
  declares no permissions at all.
- **Ranking is a local Kotlin heuristic** (`RankingHeuristic.kt`) that
  decomposes a task's score into per-feature contributions. The Insights
  screen renders those contributions as the local analogue of the SHAP
  panel from the original web version.
- **Hilt** for DI, **Navigation Component** for the seven-fragment graph,
  **ViewBinding** for view access (no `findViewById`), **Coroutines**
  with `Dispatchers.IO` for every DB write.

## Alternatives considered

| Option | Why rejected |
|---|---|
| Bundle the Python ML service + REST passthrough | Adds two more runtimes the grader has to start; service availability becomes a demo risk; nothing in the rubric requires a server. |
| Ktor / Spring backend over a network call | Same dependency-on-running-process problem with no rubric upside. |
| Direct Postgres (Neon) from the device | Leaks credentials, violates the "only necessary permissions" rule. |
| Jetpack Compose UI | Explicitly forbidden by the course guidelines. |
| Single Activity, no Fragments | Explicitly forbidden by the course guidelines. |

## Consequences

### Positive

- Zero external dependencies. Clone, sync, run.
- Offline-first by construction — there is no "online" mode to fall back
  from.
- Every rubric item is enforced by the code, not by deployment.
- Ranking is fully explainable: the Insights screen literally lists the
  numbers that produced the score.

### Negative

- Ranking quality is lower than a trained XGBoost model. The heuristic is
  hand-tuned, not learned.
- No cross-device sync. A user re-installing the app loses their tasks.
  Acceptable for a single-user productivity tool; would need a backend
  for a production rollout.
- The XGBoost / SHAP narrative from the web version doesn't carry over —
  Insights is a deliberate downgrade with no model artefacts on device.

## Notes

If a future course or product requirement adds sync, the path is:

1. Add a `data/remote/` package with Retrofit + DTOs.
2. Restore `INTERNET` permission in the Manifest.
3. Wrap repository writes in optimistic local-first + best-effort remote
   patterns (the existing `mutate` helper is the right shape for this).
4. Keep the local heuristic as the offline fallback.
