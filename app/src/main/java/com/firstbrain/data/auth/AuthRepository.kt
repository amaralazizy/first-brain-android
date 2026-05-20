// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.data.auth

import android.util.Base64
import androidx.work.WorkManager
import com.firstbrain.data.local.AppDatabase
import com.firstbrain.data.sync.SyncStateStore
import com.firstbrain.di.IoDispatcher
import com.firstbrain.worker.SyncWorker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data class Authenticated(val userId: String) : AuthState
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: NeonAuthApi,
    private val store: TokenStore,
    private val syncState: SyncStateStore,
    private val workManager: WorkManager,
    private val database: AppDatabase,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val _state = MutableStateFlow<AuthState>(
        store.userId?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun signUp(email: String, password: String, name: String) {
        persistSession(api.signUp(body = SignUpRequest(email, password, name)))
    }

    suspend fun signIn(email: String, password: String) {
        persistSession(api.signIn(body = SignInRequest(email, password)))
    }

    suspend fun signOut() {
        val cookieValue = store.sessionToken
        if (!cookieValue.isNullOrEmpty()) {
            runCatching { api.signOut(cookie = "${AuthConstants.SESSION_COOKIE}=$cookieValue") }
        }
        store.clear()
        syncState.clear()
        SyncWorker.cancelAll(workManager)
        // Wipe the local cache so the next sign-in (possibly a different account
        // on this device) doesn't see the previous user's rows.
        withContext(io) { database.clearAllTables() }
        _state.value = AuthState.Unauthenticated
    }

    /**
     * Returns a valid JWT, refreshing it if missing or near expiry.
     * Throws if the user is signed out or the session token is no longer accepted.
     */
    suspend fun validJwt(): String = refreshMutex.withLock {
        val now = System.currentTimeMillis() / 1000
        val cached = store.jwt
        val exp = store.jwtExpiresAt
        if (!cached.isNullOrEmpty() && exp > now + AuthConstants.JWT_REFRESH_LEEWAY_SECONDS) {
            return@withLock cached
        }
        val cookieValue = store.sessionToken
            ?: throw IllegalStateException("Not signed in")
        val fresh = api.token(cookie = "${AuthConstants.SESSION_COOKIE}=$cookieValue").token
        store.jwt = fresh
        store.jwtExpiresAt = parseJwtExp(fresh)
        fresh
    }

    /** Drop only the cached JWT — next request will fetch a new one. */
    fun invalidateJwt() {
        store.jwt = null
        store.jwtExpiresAt = 0
    }

    private fun persistSession(response: Response<AuthResponse>) {
        if (!response.isSuccessful) {
            throw IllegalStateException("Auth failed: HTTP ${response.code()}")
        }
        val body = response.body() ?: throw IllegalStateException("Empty auth response")
        val cookieValue = extractSessionCookie(response)
            ?: throw IllegalStateException("No session cookie in response")
        store.sessionToken = cookieValue
        store.userId = body.user.id
        store.jwt = null
        store.jwtExpiresAt = 0
        _state.value = AuthState.Authenticated(body.user.id)
        SyncWorker.enqueueNow(workManager)
        SyncWorker.schedulePeriodic(workManager)
    }

    /**
     * Pull the URL-encoded value of `__Secure-neon-auth.session_token` out of
     * the `Set-Cookie` headers. The JSON `token` field is only the first half
     * of the cookie — the signature suffix lives only in the header.
     */
    private fun extractSessionCookie(response: Response<AuthResponse>): String? {
        val target = "${AuthConstants.SESSION_COOKIE}="
        for (header in response.headers().values("Set-Cookie")) {
            if (header.startsWith(target)) {
                val end = header.indexOf(';').let { if (it == -1) header.length else it }
                return header.substring(target.length, end)
            }
        }
        return null
    }

    private fun parseJwtExp(jwt: String): Long {
        val parts = jwt.split('.')
        if (parts.size < 2) return 0
        return runCatching {
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            Json.parseToJsonElement(payload.toString(Charsets.UTF_8))
                .jsonObject["exp"]?.jsonPrimitive?.long ?: 0L
        }.getOrDefault(0L)
    }
}