# First Brain

> Self-contained native Android task prioritisation app.
> Submitted as the CSE461 — Mobile Computing project (Spring 2026, EUI).

First Brain helps a user decide what to work on next. Tasks have an urgency,
a type, an estimated effort, and an optional deadline. A local ranking
heuristic scores every pending task on each interaction and surfaces the
top picks on the Today screen. All data stays on the device — there is no
network or backend.

## Stack

| Concern | Choice |
|---|---|
| Language | Kotlin |
| Architecture | MVVM (Fragment → ViewModel → Repository → Room) |
| Local DB | Room 2.6 — `tasks` + `task_interactions` (1:N) |
| UI | XML layouts (ConstraintLayout, LinearLayout), Material 3, ViewBinding |
| Navigation | Jetpack Navigation Component + bottom nav |
| DI | Hilt |
| Async | Kotlin Coroutines on `Dispatchers.IO`, `StateFlow` in ViewModels |
| Lists | RecyclerView + DiffUtil |
| Debug | LeakCanary |
| minSdk / targetSdk | 26 / 35 |
| AGP / Kotlin | 8.7.3 / 2.0.21 |

The course rubric required Kotlin, MVVM with an explicit Repository,
Fragments, Room (≥2 related entities), XML layouts, ViewBinding,
Coroutines on the IO dispatcher, and resources externalised to
`strings.xml` / `colors.xml` / `dimens.xml`. Every item is enforced by the
code in this repo.

## Setup

1. Open the project root in Android Studio Ladybug or later. Sync Gradle.
   On first sync, Android Studio downloads the Gradle wrapper jar and
   writes `local.properties` for you.
2. Headless builds: copy `local.properties.sample` to `local.properties`
   and set `sdk.dir` to your Android SDK path.
3. Build & run on an emulator or device with Android 8.0+ (API 26+):
   ```
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

## Project layout

```
first-brain-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/firstbrain/
│       │   ├── FirstBrainApp.kt         # @HiltAndroidApp
│       │   ├── data/
│       │   │   ├── local/               # Room: entities, DAOs, AppDatabase, converters
│       │   │   └── repo/                # TaskRepository, RankingHeuristic
│       │   ├── di/                      # Hilt modules (Database, Coroutine)
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       ├── common/              # Shared TaskAdapter, formatters
│       │       ├── today/               # TodaysPicksFragment + VM
│       │       ├── tasks/               # TasksFragment + VM
│       │       ├── addedit/             # AddEditTaskFragment + VM
│       │       ├── detail/              # TaskDetailFragment + VM
│       │       ├── analytics/           # AnalyticsFragment + VM
│       │       ├── history/             # HistoryFragment + VM
│       │       └── insights/            # InsightsFragment + VM (heuristic breakdown)
│       └── res/
│           ├── layout/                  # All XML, no Compose
│           ├── navigation/nav_graph.xml
│           ├── menu/bottom_nav_menu.xml
│           ├── drawable/                # Vector icons
│           └── values/                  # strings, colors, dimens, themes
├── docs/adr/                            # Architecture decision records
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/libs.versions.toml            # Version catalog
└── gradle.properties
```

## MVVM walkthrough

| Layer | Responsibility |
|---|---|
| **View** (Fragment) | Inflates the XML layout via ViewBinding. Observes `StateFlow`s from the ViewModel inside `repeatOnLifecycle(STARTED)`. Forwards clicks to the ViewModel. Never touches Room directly. |
| **ViewModel** | Survives configuration changes via the framework. Holds no `Context`. Exposes immutable `StateFlow<UiState>`. Calls into the Repository inside `viewModelScope`. |
| **Repository** (`TaskRepository`) | Single source of truth. Coordinates `TaskDao` + `InteractionDao`. Every write is wrapped in `withContext(io)` so the View layer never blocks on disk. |
| **Model** (Room) | `TaskEntity`, `InteractionEntity`, DAOs, `AppDatabase`. DAOs expose `Flow<List<…>>` for reactive reads and `suspend` functions for writes. |

## Room schema

```
tasks (id PK autogen, title, description, urgency, task_type,
       estimated_effort, deadline, has_deadline, skip_count, status,
       created_at, updated_at, completed_at, last_interacted_at,
       rec_score)
    │
    │ 1
    ▼ N   (ON DELETE CASCADE)
task_interactions (id PK autogen, task_id FK→tasks.id,
                   action, occurred_at, score)
```

`task_interactions.action` is one of `viewed | completed | skipped |
snoozed | reopened`. The Analytics screen aggregates over the last 7 days
of interactions locally.

## Fragment map

| Fragment | Purpose |
|---|---|
| `TodaysPicksFragment` | Start destination. Pending tasks ranked by `rec_score`. Swipe-to-refresh rescore. |
| `TasksFragment` | Full list, FAB to add. |
| `AddEditTaskFragment` | Form: title, description, urgency, type, effort, optional deadline. |
| `TaskDetailFragment` | Single task + interaction log. Complete / skip / reopen / delete. |
| `AnalyticsFragment` | Totals, completion rate, skip rate, avg effort, 7-day action counts. |
| `HistoryFragment` | Completed + skipped tasks, ordered by most-recently updated. |
| `InsightsFragment` | Breaks down the top pick's score by feature (urgency, deadline, staleness, skip penalty, age decay). |

Navigation between top-level destinations uses the bottom nav menu;
secondary destinations (detail, add/edit) push onto the back stack via
actions defined in `nav_graph.xml`.

## Ranking heuristic

`RankingHeuristic.score(task)` is a deterministic decomposition:

```
score =  urgency_weight              // Low 0.25 · Medium 0.50 · High 0.85 · Critical 1.00
       + deadline_proximity * 0.6    // 0 when no deadline; ramps 0 → 1 as the deadline approaches
       + overdue * 0.8               // hard kick for past-due tasks
       + staleness_boost             // up to +0.3 if the task has not been touched in two weeks
       − skip_penalty                // -0.05 per skip
       − age_decay                   // up to -0.2 for very old, never-touched tasks
```

`RankingHeuristic.breakdown` returns the per-feature contributions so the
Insights screen can render them — the local-only analogue of the SHAP
panel used in the original web version.

## Test plan (for the demo / Q&A)

| Check | What to do |
|---|---|
| ViewModel survives rotation | Open Today, rotate device, list stays. |
| Room persistence | Add a task, kill the app, relaunch — task is still there. |
| Reactive UI | Mark a task complete from Detail — it disappears from Today and Tasks immediately. |
| Heuristic updates | Add a Critical task with a deadline tomorrow — it jumps to the top. |
| Interaction log | Tap a task to open detail (logs `viewed`), then complete it — both entries appear in the interaction list. |
| DiffUtil | Scroll Today during a rescore — items animate, no flicker. |
| No `findViewById` | `grep -r findViewById app/src/main` returns nothing. |
| No hardcoded XML strings | `grep -r 'android:text="[^@]' app/src/main/res/layout` returns nothing. |

## Academic integrity

This project was scaffolded with AI assistance (Claude). Per §5 of the
course guidelines:

- The Room schema, Fragment map, MVVM layering, and ranking heuristic are
  the team's own design and can be defended in Q&A.
- AI-assisted code blocks are annotated `// AI-assisted` where the model
  produced the first draft verbatim.

## Course-required artefacts checklist

- [x] Kotlin only (no Java)
- [x] MVVM with explicit Repository layer
- [x] Room with ≥2 related entities + DAO + `@Database`
- [x] Fragments + Navigation Component
- [x] XML layouts only (no Compose)
- [x] ViewBinding (no `findViewById`)
- [x] Coroutines on `Dispatchers.IO` for all DB writes
- [x] Strings, colors, dimens externalised
- [x] minSdk 26 (Android 8.0+)
- [x] RecyclerView + DiffUtil
- [x] Hilt for DI (extra credit)
- [x] LeakCanary in debug builds
- [ ] Signed debug APK — generated by Android Studio (`Build → Build APK(s)`) at submission time
- [ ] PDF report — written separately, not in repo
- [ ] Video walkthrough — recorded at submission time
