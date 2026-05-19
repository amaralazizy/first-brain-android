package com.firstbrain.data.auth

import android.util.Base64
import androidx.work.WorkManager
import com.firstbrain.data.sync.SyncStateStore
import com.firstbrain.worker.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.URLEncoder
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
) {

    private val _state = MutableStateFlow<AuthState>(
        store.userId?.let { AuthState.Authenticated(it) } ?: AuthState.Unauthenticated
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    suspend fun signUp(email: String, password: String, name: String) {
        val res = api.signUp(body = SignUpRequest(email, password, name))
        persistSession(res)
    }

    suspend fun signIn(email: String, password: String) {
        val res = api.signIn(body = SignInRequest(email, password))
        persistSession(res)
    }

    suspend fun signOut() {
        val session = store.sessionToken
        if (!session.isNullOrEmpty()) {
            runCatching { api.signOut(cookie = sessionCookie(session)) }
        }
        store.clear()
        syncState.clear()
        SyncWorker.cancelAll(workManager)
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
        val session = store.sessionToken
            ?: throw IllegalStateException("Not signed in")
        val fresh = api.token(cookie = sessionCookie(session)).token
        store.jwt = fresh
        store.jwtExpiresAt = parseJwtExp(fresh)
        fresh
    }

    /** Drop only the cached JWT — next request will fetch a new one. */
    fun invalidateJwt() {
        store.jwt = null
        store.jwtExpiresAt = 0
    }

    private fun persistSession(res: AuthResponse) {
        store.sessionToken = res.token
        store.userId = res.user.id
        store.jwt = null
        store.jwtExpiresAt = 0
        _state.value = AuthState.Authenticated(res.user.id)
        SyncWorker.enqueueNow(workManager)
        SyncWorker.schedulePeriodic(workManager)
    }

    private fun sessionCookie(token: String): String {
        val encoded = URLEncoder.encode(token, Charsets.UTF_8.name())
        return "${AuthConstants.SESSION_COOKIE}=$encoded"
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
