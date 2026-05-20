// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit client for the Neon Data API (PostgREST) backing `public.tasks`.
 *
 *  - Row-Level Security on the server scopes every query to the calling user, so
 *    no client-side `user_id` filter is needed.
 *  - Bearer JWT is attached by `AuthInterceptor` on the `@ApiClient` OkHttpClient.
 *  - `Prefer: return=representation` makes POST/PATCH echo the resulting row(s)
 *    so we can immediately persist them locally with server-side defaults applied.
 *  - `Prefer: resolution=merge-duplicates` turns POST into an upsert keyed on `id`.
 */
interface NeonTasksApi {

    /** Full pull (used on first sync after sign-in). */
    @GET("tasks")
    suspend fun list(
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.desc",
    ): List<TaskRemoteDto>

    /** Incremental pull — everything updated strictly after [sinceIso]. */
    @GET("tasks")
    suspend fun listSince(
        @Query("updated_at") sinceFilter: String,           // "gt.2026-05-20T12:34:56Z"
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.asc",
    ): List<TaskRemoteDto>

    /** Upsert one or many rows in a single round-trip. */
    @Headers(
        "Content-Type: application/json",
        "Prefer: resolution=merge-duplicates,return=representation",
    )
    @POST("tasks")
    suspend fun upsert(@Body rows: List<TaskRemoteDto>): List<TaskRemoteDto>
}