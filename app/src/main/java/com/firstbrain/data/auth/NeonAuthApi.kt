package com.firstbrain.data.auth

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val name: String,
)

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String,
    val name: String? = null,
    val emailVerified: Boolean = false,
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: AuthUser,
)

@Serializable
data class JwtResponse(
    val token: String,
)

interface NeonAuthApi {

    @POST("sign-up/email")
    suspend fun signUp(
        @Header("Origin") origin: String = AuthConstants.ORIGIN,
        @Body body: SignUpRequest,
    ): AuthResponse

    @POST("sign-in/email")
    suspend fun signIn(
        @Header("Origin") origin: String = AuthConstants.ORIGIN,
        @Body body: SignInRequest,
    ): AuthResponse

    @POST("sign-out")
    suspend fun signOut(
        @Header("Cookie") cookie: String,
        @Header("Origin") origin: String = AuthConstants.ORIGIN,
    )

    /** Exchange a session cookie for a short-lived JWT (Ed25519, 15 min). */
    @GET("token")
    suspend fun token(
        @Header("Cookie") cookie: String,
    ): JwtResponse
}
