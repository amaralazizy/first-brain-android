# First Brain

> Native Android task prioritisation app for the **CSE461 — Mobile Computing**
> Spring 2026 project (EUI / The Knowledge Hub).

First Brain helps a user decide what to work on next. Tasks have an urgency,
a type, an estimated effort, and an optional deadline. A remote XGBoost
recommender scores every pending task and surfaces the top three on the
**Today's Picks** screen, with a per-feature SHAP-style explanation on every
card so the user understands *why* a task ranks where it does.

The app is **offline-first**: every mutation lands in Room first, then sync
to the cloud happens opportunistically when the network is up.

---

## 1. Problem & target users

University students and early-career professionals usually keep a long list
of obligations across school, work, life, and learning. Choosing **what to
actually start now** is the friction point — pure to-do apps surface
everything and leave prioritisation as a manual exercise.

A mobile solution is appropriate because:

- The decision happens *between* contexts (waking up, finishing a meeting,
  end of class). Pocket-first beats desktop-first.
- Background notifications hook the deadlines into the OS scheduler.
- An offline-first store lets the user capture an obligation the moment
  it arrives, with no dependency on connectivity.

Accessibility / usability considerations baked in:

- All hit targets are ≥ 48 dp (Material defaults).
- Colour is never the only signal — every state has text too
  (`✓ Done`, `— Skipped`, `Overdue`, `Priority pending`).
- Dark-first palette to reduce glare at the times people most often
  re-prioritise (early morning, late at night).

---

## 2. Stack

| Concern | Choice |
|---|---|
| Language | **Kotlin** (no Java) |
| Architecture | **MVVM** — Fragment → ViewModel → Repository → Room / Remote |
| Local DB | **Room 2.6** — three entities: `tasks`, `task_interactions`, `feedback_outbox` |
| UI | **XML layouts** (ConstraintLayout / LinearLayout), Material 3, **ViewBinding** |
| Navigation | Jetpack **Navigation Component** + bottom nav |
| DI | **Hilt** |
| Async | **Kotlin Coroutines** on `Dispatchers.IO` (qualified `@IoDispatcher`) ; `StateFlow` between ViewModel and Fragment |
| Lists | **RecyclerView** + `ListAdapter` with **DiffUtil** |
| Networking | **Retrofit 2** + **OkHttp** + **kotlinx-serialization** |
| Background | **WorkManager** (sync + reminders) |
| Auth (remote) | **Neon Auth** (Better Auth) — JWT issued by Stack-Auth-compatible server, Bearer attached by `AuthInterceptor` |
| Remote DB | **Neon Postgres** via the Neon Data API (PostgREST), Row-Level Security keyed on `auth.user_id()` |
| ML | **FastAPI + XGBoost + SHAP** hosted on Hugging Face Spaces |
| Debug | **LeakCanary** (debug builds only) |
| minSdk / targetSdk | **26** (Android 8.0 Oreo) / **35** (Android 15) |

The remote backend is optional — the app remains usable when the device is
offline, and surfaces "Priority will be calculated once you're online"
placeholders for cards that have never been scored.

---

## 3. MVVM architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                            VIEW                                    │
│   Fragments (XML + ViewBinding) — observe StateFlow only           │
│   LoginFragment · SignUpFragment · TodaysPicksFragment             │
│   TasksFragment · TaskDetailFragment · AddEditTaskFragment         │
│   HistoryFragment · AnalyticsFragment                              │
└────────────────────┬───────────────────────────────────────────────┘
                     │ events ↓        ↑ StateFlow
┌────────────────────▼───────────────────────────────────────────────┐
│                         VIEWMODEL (Hilt)                           │
│   AuthViewModel · TodaysPicksViewModel · TasksViewModel            │
│   TaskDetailViewModel · AddEditTaskViewModel · HistoryViewModel    │
│   AnalyticsViewModel                                               │
│   — survives configuration changes via viewModelScope              │
└────────────────────┬───────────────────────────────────────────────┘
                     │ suspend ↓
┌────────────────────▼───────────────────────────────────────────────┐
│                         REPOSITORY                                 │
│   TaskRepository · AuthRepository · SyncRepository                 │
│   — single source of truth, runs on @IoDispatcher                  │
└─────────────┬──────────────────────────────────┬───────────────────┘
              │                                  │
┌─────────────▼─────────────┐    ┌───────────────▼──────────────────┐
│       LOCAL — Room        │    │           REMOTE                 │
│   TaskDao · InteractionDao│    │   NeonAuthApi · NeonTasksApi     │
│   FeedbackOutboxDao       │    │   RecommendationApi              │
│   (suspend funcs / Flow)  │    │   (Retrofit, OkHttp + Bearer JWT)│
└───────────────────────────┘    └──────────────────────────────────┘
                                                │
                                  ┌─────────────▼──────────────────┐
                                  │ Neon Auth · Neon Data API ·    │
                                  │ FastAPI XGBoost on HF Spaces   │
                                  └────────────────────────────────┘
```

**Why MVVM** — the rubric requires it, but it's also the correct fit here:
the View is purely declarative and stateless across rotation, the ViewModel
owns transient UI state, and the Repository hides the fact that there are
both a local Room cache and a remote PostgREST / FastAPI pair. We considered
plain MVC with the Activity holding business logic (rejected — doesn't
survive rotation cleanly) and a Compose + MVI variant (rejected — Compose
is disallowed by the rubric).

### 3.1 ViewModel & rotation

Every Fragment uses `by viewModels()`. The Hilt-provided ViewModel is
scoped to the Fragment, so on rotation the same instance is reused and
`StateFlow` state is preserved. Fragments collect via
`viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`
so subscriptions are torn down at `onStop` and re-created at `onStart`,
preventing leaks across rotation while still surviving config changes.

### 3.2 Coroutines & threading

- `@IoDispatcher` qualifier in `di/Qualifiers.kt` exposes `Dispatchers.IO`.
- `TaskRepository`, `AuthRepository`, `SyncRepository` all wrap their work
  in `withContext(io) { … }`.
- Room `@Dao` methods are all `suspend` (or return `Flow`), so the Room
  thread pool handles them — never the main thread.
- WorkManager workers are `CoroutineWorker` subclasses; the `doWork()`
  body is suspending.

### 3.3 Error handling

- Network failures: `runCatching { … }.onFailure { Log.w(...) }` inside
  `SyncRepository`. WorkManager retries with exponential backoff.
- Room failures: surface as caught exceptions inside repository methods.
  `AddEditTaskViewModel` exposes them as a sealed `Event.Error`.
- HTTP 401: `AuthInterceptor` invalidates the cached JWT and retries
  once; if the session itself is dead, the failure propagates to the
  caller which routes the user back to the Login screen.
- Offline UI: cards show "Priority will be calculated once you're
  online" until the next successful `/recommend` round-trip.

---

## 4. Room schema

### 4.1 Entities

```
┌─────────────────────────────────┐
│             tasks               │
├─────────────────────────────────┤
│ id              TEXT PK (UUID)  │
│ title           TEXT NOT NULL   │
│ description     TEXT            │
│ urgency         ENUM 4-value    │  Low / Medium / High / Critical
│ task_type       ENUM 5-value    │  work / personal / learning / health / other
│ estimated_effort INT            │
│ deadline        INSTANT?        │
│ has_deadline    BOOL            │
│ skip_count      INT             │
│ status          ENUM 3-value    │  pending / completed / skipped
│ rec_score       REAL?           │  cached ML score
│ explanation_json TEXT?          │  cached SHAP top-k from /recommend
│ dirty           BOOL            │  needs push to Neon?
│ deleted         BOOL            │  soft-delete tombstone
│ created_at      INSTANT         │
│ updated_at      INSTANT         │
│ completed_at    INSTANT?        │
│ last_interacted_at INSTANT?     │
└───────────────┬─────────────────┘
                │ 1
                │
                │ N  (cascade delete)
                ▼
┌─────────────────────────────────┐
│       task_interactions         │
├─────────────────────────────────┤
│ id              INT PK auto     │
│ task_id         TEXT FK → tasks │
│ action          ENUM 5-value    │  viewed / completed / skipped / snoozed / reopened
│ occurred_at     INSTANT         │
│ score           REAL?           │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│       feedback_outbox           │  (standalone — drained by SyncWorker)
├─────────────────────────────────┤
│ id              INT PK auto     │
│ task_id         TEXT            │
│ action          TEXT            │  "complete" | "skip"
│ score           REAL?           │
│ created_at      INSTANT         │
└─────────────────────────────────┘
```

`tasks` ↔ `task_interactions` is a one-to-many relationship with
`ForeignKey(onDelete = CASCADE)`. Hard-deleting a task removes its
interaction history; soft-delete just flips a flag so the cross-device
sync can carry the tombstone.

### 4.2 DAO query examples

- `TaskDao.observePicks(): Flow<List<TaskEntity>>` — `LIMIT 3`, ordered
  by `rec_score DESC, urgency DESC`, drives Today's Picks.
- `TaskDao.observeAll(): Flow<List<TaskEntity>>` — drives Tasks tab.
- `TaskDao.dirty(): List<TaskEntity>` — for the push side of sync.
- `TaskDao.softDelete(id, now)` — single-row soft delete.
- `InteractionDao.actionCountsSince(sinceMillis)` — backs the
  Analytics screen via `GROUP BY action`.

The View layer **never** touches a DAO directly. ViewModels inject a
Repository, which is the only class that constructs/calls DAOs.

---

## 5. Fragment map

| Fragment | Role | Nav transitions |
|---|---|---|
| `LoginFragment` | Email/password sign-in | → SignUp · → Home (on success, pops auth stack) |
| `SignUpFragment` | Email/password registration | ← Login · → Home (on success) |
| `TodaysPicksFragment` | Top 3 picks for now | → TaskDetail |
| `TasksFragment` | Full task list | → TaskDetail · → AddEditTask (FAB) |
| `TaskDetailFragment` | Read-only inspection of one task + interactions | → AddEditTask (edit) |
| `AddEditTaskFragment` | Create / edit a task | ← popBackStack on save |
| `HistoryFragment` | Completed + skipped with All / Completed / Skipped tabs | (no outgoing) |
| `AnalyticsFragment` | Bar charts of counts by status / type / urgency | (no outgoing) |

Bottom nav exposes the four top-level destinations (Today, Tasks,
Analytics, History). Auth screens hide the toolbar and bottom nav via an
`OnDestinationChangedListener` in `MainActivity`. Sign-out clears
encrypted prefs, cancels sync workers, **wipes Room**, and bounces back
to `LoginFragment` via `popUpTo(nav_graph, inclusive = true)`.

---

## 6. Setup

### 6.1 Build the Android app

```bash
git clone https://github.com/amaralazizy/first-brain-android.git
cd first-brain-android
# Open in Android Studio → Run on an emulator or device (API 26+).
```

No `local.properties` secrets are required; the three base URLs live in
`app/src/main/java/com/firstbrain/data/auth/AuthConstants.kt` and point
at the deployed Neon + Hugging Face Spaces endpoints (see § 7). To run
against your own instances, replace those constants.

### 6.2 Run the ML server locally (optional)

```bash
pip install -r requirements.txt
cd recommendation-engine
uvicorn api:app --reload --port 8000
```

Then temporarily flip `AuthConstants.RECOMMENDATION_URL` to
`http://10.0.2.2:8000/` (emulator loopback) and rebuild.

---

## 7. Deployed backends

| Service | URL | Notes |
|---|---|---|
| Neon Auth (Better Auth) | `https://ep-proud-resonance-amlj3hjf.neonauth.c-5.us-east-1.aws.neon.tech/neondb/auth` | Issues 15-min EdDSA JWTs; mobile sends the raw cookie back for `/token` refresh. |
| Neon Data API (PostgREST) | `https://ep-proud-resonance-amlj3hjf.apirest.c-5.us-east-1.aws.neon.tech/neondb/rest/v1` | Tables exposed with RLS scoped on the JWT `sub`. |
| Recommendation engine | `https://amaralazizy-first-brain-engine.hf.space` | FastAPI / XGBoost / SHAP, Docker on HF Spaces free tier. |

No secrets are committed; the Android app holds only the public Bearer
JWT in `EncryptedSharedPreferences`. The Stack secret key lives only on
the deployed backend.

---

## 8. Performance & quality

- **DiffUtil**: `TaskAdapter` extends `ListAdapter<TaskEntity, VH>` with a
  `DiffUtil.ItemCallback`; only changed rows rebind, so the scrolling
  list stays at 60 fps even after a sync round-trip.
- **LeakCanary** is enabled in debug builds only; we run it on every
  build and have no outstanding watched references at the time of
  submission.
- **Rotation**: every ViewModel uses `by viewModels()` and exposes
  `StateFlow`, so rotating the device on any screen preserves
  scroll position, form input, tab selection, and the in-flight task
  list with no flicker.
- **Two screen configurations tested**: small phone (Pixel 6 emulator,
  API 34, 6.1") and a wider tablet preview (Pixel Tablet, API 34, 11").
- **Memory profiling**: Android Studio Profiler shows a steady ~35 MB
  heap on the Today screen; no growth across 100+ create/complete
  cycles.

---

## 9. Code quality checklist (rubric § 2.5)

| Requirement | Status |
|---|---|
| **ViewBinding**, no `findViewById` | ✅ — every Fragment + Activity uses generated `*Binding` classes. Dynamically inflated chart rows use `ItemChartRowBinding`. |
| **Coroutines** for all DB / network work | ✅ — every Repository method is `suspend` and wraps work in `withContext(io)`. DAOs are `suspend` or `Flow`-returning. |
| **Lifecycle-aware** observers | ✅ — Fragments collect through `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(STARTED)`. |
| **Strings in `strings.xml`**, dimens in `dimens.xml`, colors in `colors.xml` | ✅ — no hardcoded `android:text=` literals remain in any layout. |
| **Runtime permissions** declared and requested | ✅ — `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`; the runtime `POST_NOTIFICATIONS` prompt is handled via `ActivityResultContracts.RequestPermission` in `MainActivity`. |

---

## 10. Failure modes

| Failure | How the app behaves |
|---|---|
| No connectivity at launch | Last Room snapshot loads; Today's Picks may show null `rec_score` → "Priority will be calculated once you're online" placeholder; outbox accepts new feedback. |
| `/recommend` times out | `rescoreAll()` catches, logs `Log.w("TaskRepository", …)`, leaves the previous `rec_score` in place. UI keeps rendering with cached scores. |
| HTTP 401 from any authed call | `AuthInterceptor` invalidates the JWT and retries once. If still 401, the failure surfaces; `MainActivity`'s auth-state observer routes back to Login. |
| Sync race (same task edited on two devices) | Last-write-wins keyed on `updated_at`. Pull skips rows where the local copy is at least as fresh, protecting unflushed edits. |
| Room migration | `fallbackToDestructiveMigration()` is enabled for the pre-release build; production builds would add proper migrations. The DB version is currently **4**. |

---

## 11. Repository layout

```
first-brain-android/
├── app/                          # Android module
│   └── src/main/java/com/firstbrain/
│       ├── data/
│       │   ├── auth/             # NeonAuthApi, AuthRepository, TokenStore, AuthInterceptor
│       │   ├── local/            # Room entities + DAOs + AppDatabase
│       │   ├── remote/           # NeonTasksApi, RecommendationApi, DTOs, Mapper
│       │   ├── repo/             # TaskRepository
│       │   └── sync/             # SyncRepository, SyncStateStore
│       ├── di/                   # Hilt modules + qualifiers
│       ├── ui/                   # Fragments + ViewModels grouped by screen
│       └── worker/               # WorkManager workers (sync, reminders, digest)
├── recommendation-engine/        # Python FastAPI + XGBoost service
├── docs/
│   ├── ARCHITECTURE.md           # Diagrams + screen-by-screen breakdown
│   └── adr/                      # Architecture Decision Records
├── Dockerfile                    # ML service container (HF Spaces compatible)
├── requirements.txt              # Python deps
└── README.md                     # this file
```

---

## 12. Originality & academic integrity

The architecture, Room schema, Fragment navigation graph, sync protocol,
and written reports are the team's own original work. Portions of the
implementation (notably the initial scaffolding of the MVVM skeleton and
the PostgREST mapper) were drafted with AI assistance; every such block
is annotated inline with `// AI-assisted` comments where the contribution
is non-trivial. Every team member has read and can explain every line of
the submission.
