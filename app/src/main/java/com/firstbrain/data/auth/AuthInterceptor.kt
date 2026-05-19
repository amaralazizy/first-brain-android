package com.firstbrain.data.auth

import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches `Authorization: Bearer <jwt>` to outbound requests.
 *
 * On a 401 the JWT is invalidated and the request is retried once with a
 * freshly-minted JWT. If the user is signed out (no session token) the
 * request proceeds unauthenticated and the caller deals with the 401.
 *
 * [authRepository] is wrapped in [Lazy] so DI can resolve a cycle:
 * AuthInterceptor → OkHttpClient → Retrofit → NeonAuthApi → AuthRepository → AuthInterceptor.
 */
class AuthInterceptor @Inject constructor(
    private val authRepository: Lazy<AuthRepository>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val jwt = runCatching { runBlocking { authRepository.get().validJwt() } }.getOrNull()

        val authed = if (jwt != null) {
            request.newBuilder().header("Authorization", "Bearer $jwt").build()
        } else {
            request
        }

        val response = chain.proceed(authed)
        if (response.code != 401 || jwt == null) return response

        // JWT may have expired between cache hit and request — invalidate and retry once.
        response.close()
        authRepository.get().invalidateJwt()
        val refreshed = runCatching { runBlocking { authRepository.get().validJwt() } }.getOrNull()
            ?: return chain.proceed(authed)

        val retry = request.newBuilder().header("Authorization", "Bearer $refreshed").build()
        return chain.proceed(retry)
    }
}
