# Architecture

Companion to the [README](../README.md). The README covers the *what*; this
document covers the *how it fits together* in enough depth to answer the
rubric § 6 Q&A questions cold.

---

## 1. Layer responsibilities

### View — Fragments + XML layouts

- Inflate the layout via the generated `*Binding` class — never call
  `findViewById` directly.
- Collect a single `StateFlow` from the ViewModel and re-render.
- Translate user input into ViewModel function calls (`vm.save(...)`,
  `vm.complete(id)`).
- Hold zero business rules. Anything more interesting than "render this
  string" lives behind a ViewModel call.

### ViewModel — `@HiltViewModel`

- Instantiated by Hilt, scoped to the Fragment lifetime via
  `by viewModels()`.
- Owns transient UI state in `StateFlow` (and one-shot events in a
  `Channel`).
- Calls Repository methods inside `viewModelScope.launch { … }` so the
  call survives configuration changes but is cancelled when the
  Fragment is permanently destroyed.
- Never references Android `Context`, `View`, or `View*Binding`.

### Repository — `@Singleton`

- Single source of truth across local + remote sources.
- Wraps every call in `withContext(io) { … }` using the `@IoDispatcher`
  qualifier so callers don't have to think about threads.
- Three Repositories:
  - `TaskRepository` — task CRUD + ML rescoring + feedback outbox.
  - `AuthRepository` — sign-up / sign-in / sign-out, JWT refresh.
  - `SyncRepository` — push dirty rows + pull updates + drain
    feedback outbox.

### Local data — Room

- `AppDatabase` (`version = 4`) hosts the three entities and the three
  DAOs.
- Hilt's `DatabaseModule` constructs it with `fallbackToDestructiveMigration`
  (acceptable for pre-release; production migrations are tracked in
  ADR-002).

### Remote data — Retrofit + OkHttp

- Three Retrofit clients, all sharing a Hilt-managed `Json` and
  `OkHttpClient`:
  - `NeonAuthApi` — unauthenticated; carries `Origin` header.
  - `NeonTasksApi` — authenticated via `AuthInterceptor` (Bearer JWT).
  - `RecommendationApi` — authenticated via `AuthInterceptor`.

### WorkManager workers

- `SyncWorker` — `CONNECTED` constraint, one-shot + 15-min periodic.
  Calls `SyncRepository.syncAll()` then `TaskRepository.rescoreAll()`.
- `ReminderWorker` — fires per-task notifications at deadline-1d,
  effort+1h, and deadline.
- `DailyDigestWorker` — morning summary notification.

---

## 2. Fragment-by-fragment

### 2.1 LoginFragment / SignUpFragment
Renders email + password (+ name) inputs. Calls
`AuthRepository.signIn` / `.signUp`, which on success persists the
session, kicks `SyncWorker`, and flips `AuthState`. Fragment listens for
the `Event.Authenticated` channel and navigates to `todaysPicksFragment`
with `popUpTo` clearing the auth stack.

### 2.2 TodaysPicksFragment
Subscribes to `TaskRepository.observePicks()` (`LIMIT 3`). Swipe-to-refresh
calls `vm.refresh()` which re-runs `rescoreAll`. Tap on a card → detail.

### 2.3 TasksFragment
Full list, drives `taskDao.observeAll()`. FAB navigates to
`addEditTaskFragment`.

### 2.4 AddEditTaskFragment
Form for title / description / urgency / type / effort / deadline.
`Save` calls `TaskRepository.createTask(...)` which inserts to Room
(marked `dirty=true`), schedules deadline reminders, kicks `SyncWorker`,
and triggers `rescoreAll`.

### 2.5 TaskDetailFragment
Read-only view of one task + its interaction history (via
`InteractionDao.observeForTask`). Buttons call `complete` / `skip` /
`reopen` / `delete` on the repository.

### 2.6 HistoryFragment
Completed + skipped, tabbed (All / Completed / Skipped). Pushes counts
into the toolbar inline counters via `MainActivity.setToolbarStats`.

### 2.7 AnalyticsFragment
Bar charts of action counts in the last 7 days
(`InteractionDao.actionCountsSince`).

---

## 3. End-to-end mutation flow

```
User taps "Complete" on a task card
   │
   ▼
TaskAdapter -> onComplete(task)
   │
   ▼
TasksViewModel.complete(id) — viewModelScope.launch
   │
   ▼
TaskRepository.complete(id) — withContext(io)
   ├── transform TaskEntity → status=completed, completedAt=now, dirty=true
   ├── taskDao.update(updated)
   ├── interactionDao.insert(InteractionEntity(action=completed))
   ├── feedbackOutboxDao.insert(FeedbackOutboxEntity(action="complete"))
   ├── rescoreAll()  ← /recommend round-trip
   └── SyncWorker.enqueueNow(workManager)
          │
          ▼
       SyncWorker.doWork() — constraint NetworkType.CONNECTED
          ├── syncRepository.syncAll()
          │     ├── push(): bulk POST dirty rows to Neon Data API
          │     │   then clear dirty
          │     ├── pull(): GET tasks?updated_at=gt.<watermark>
          │     │   merge LWW; preserve explanation_json
          │     └── drainFeedback(): POST queued events to /feedback
          └── taskRepository.rescoreAll(): refresh scores after sync
```

---

## 4. ViewModel survival across rotation

`by viewModels()` returns the same ViewModel instance for the duration
the Fragment is associated with its Activity. The Fragment view *does*
recreate on rotation, so the binding (`_binding`) is set to null in
`onDestroyView()` to avoid leaking the destroyed view.
`viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) {…} }`
ensures the collection coroutine is cancelled when the view leaves
STARTED and restarted when it comes back, so the new view sees the
current `StateFlow.value` immediately.

---

## 5. Why no DB access in the View layer

If a Fragment held a DAO reference directly, every screen would need to
re-implement caching, threading, and offline reconciliation. By forcing
the dependency graph through a Repository:

- The threading rule (`withContext(io)`) is enforced in one place.
- Local + remote merge logic stays out of the UI.
- Swapping Room for, say, SQLDelight would touch only the `data/local`
  package; no Fragment would change.

---

## 6. Alternatives considered

| Option | Why rejected |
|---|---|
| MVC with Activity-as-controller | Activity dies on rotation; either lose state or fight `onSaveInstanceState`. ViewModel solves this for free. |
| Jetpack Compose | Explicitly disallowed by the rubric. |
| LiveData instead of StateFlow | StateFlow is the Kotlin-idiomatic primitive, integrates better with `repeatOnLifecycle`, and avoids LiveData's main-thread coupling. We don't use the lifecycle-aware redelivery quirks that justify LiveData. |
| Manual DI (constructor wiring in `Application`) | Works but quickly becomes a maintenance burden as the graph grows past ~5 singletons. Hilt is the rubric-blessed choice and the boilerplate is minimal. |
| Polling instead of WorkManager | Drains battery, ignores network constraints, doesn't survive process death. WorkManager handles all three. |
