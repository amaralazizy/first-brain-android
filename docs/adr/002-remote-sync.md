# ADR-002: Adopt remote sync (Neon Auth + Neon Data API + remote XGBoost)

## Status

Accepted — 2026-05-20. Supersedes the "no Retrofit, no INTERNET
permission" guidance in [ADR-001](001-local-only-mvvm.md); everything
else in ADR-001 (MVVM, Room, Hilt, ViewBinding, Coroutines/IO) stands.

## Context

ADR-001 shipped the app as a fully local Kotlin demo to remove the demo
risk of a flaky backend. Two follow-on requirements changed the calculus:

1. **Multi-device** — the same user wants to add a task on their phone
   on the bus and see it on a tablet at home.
2. **Real ML signal** — the local heuristic, by construction, can't
   learn from a user's completion / skip behaviour. The team had a
   working XGBoost + SHAP pipeline already; wiring it up gives genuine
   personalisation as soon as a few hundred feedback events accumulate.

The rubric (§ 2.4) actively encourages an offline-first remote
extension, so the work fits the criteria as well.

## Decision

Layer **remote sync** on top of the local-first store:

- **Authentication** — Neon Auth (Better Auth profile). User signs in
  via native Compose-free Login / SignUp Fragments calling
  `POST /sign-up/email` and `POST /sign-in/email`. Server returns a
  long-lived session cookie; mobile exchanges it for a 15-minute
  EdDSA-signed JWT via `GET /token`. JWT cached in
  `EncryptedSharedPreferences`; refresh on demand inside a mutex.
- **Remote DB** — Neon Postgres exposed via the Neon Data API
  (PostgREST). `public.tasks` mirrors `TaskEntity`. Row-Level Security
  policies key on `auth.user_id()` so the JWT alone decides what the
  user can see / write.
- **Sync protocol** — push dirty rows then pull `updated_at > watermark`,
  last-write-wins by `updated_at`. Driven by a `CoroutineWorker` with
  `NetworkType.CONNECTED` so WorkManager handles connectivity retries
  for us.
- **Feedback outbox** — every `/feedback` event lands in a Room table
  first, then `SyncWorker` drains it. Dropped POSTs never lose data.
- **Recommendation engine** — FastAPI + XGBoost + SHAP, deployed as a
  Docker image on Hugging Face Spaces (free tier; previously Railway
  before that platform-wide incident). `/recommend` and `/feedback`
  both require the same JWT (`get_current_user` verifies via JWKS).
- **Heuristic removed** — once the remote model is the only ranker, the
  in-app `RankingHeuristic.kt` becomes dead code. Deleted along with
  the orphaned Insights screen that consumed it.

## Consequences

Positive:

- Real cross-device sync, real ML personalisation.
- Demo no longer ships secrets; only the Bearer JWT (per-user, short-lived)
  ever sits on the device, in `EncryptedSharedPreferences`.
- Offline UX is now an explicit design surface — placeholders, outbox,
  last-cached scores. Pulling the network plug doesn't break the app, it
  just degrades it.

Negative / accepted:

- Three external services (Neon Auth, Neon Data API, HF Spaces) the
  grader must trust to be up at demo time. Mitigated by the local Room
  cache: even if all three are down, the user can still create, edit,
  and complete tasks; they just won't see refreshed ML scores.
- Hugging Face's free tier sleeps the container after ~48h idle; first
  request after sleep takes ~30 sec to wake. Acceptable for a class
  project. The outbox absorbs the delay.
- Last-write-wins sync can lose a concurrent edit on a second device.
  Acceptable for a single-user-multi-device pattern; would not ship
  this conflict policy to a multi-tenant team app.

## Alternatives considered

| Option | Why rejected |
|---|---|
| Build our own Ktor backend on a VPS | More moving parts to host, secrets to rotate, and TLS to manage. Neon's hosted PostgREST + Better Auth eliminate all three. |
| Supabase | Functionally equivalent; we already had Neon Postgres for the original web app. |
| Hand-roll Stack Auth on Android via OIDC + AppAuth | Better Auth is what Neon ships; matching it directly is one less moving part. The mobile side is just two POSTs plus a cookie round-trip. |
| Skip auth, hard-code a `user_id` per device | Doesn't meet the "multi-device for the same user" requirement; can't gate RLS. |
| Move ML inference on-device with ONNX Runtime | XGBoost + SHAP isn't a clean ONNX path; the model is small but the SHAP explainer is not. Keeping inference server-side also lets us iterate on the model without shipping a new APK. |
