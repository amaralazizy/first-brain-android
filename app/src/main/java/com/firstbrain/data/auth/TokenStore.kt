// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted persistent store for auth material.
 *
 *  - [sessionToken]: long-lived (~7 days) opaque token returned by Better Auth.
 *    Used as the `__Secure-neon-auth.session_token` cookie when exchanging for a JWT.
 *  - [jwt] / [jwtExpiresAt]: short-lived (~15 min) JWT cached for outbound API calls.
 *  - [userId]: cached `sub` claim, useful for FK on outbound INSERTs.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var sessionToken: String?
        get() = prefs.getString(KEY_SESSION, null)
        set(value) { prefs.edit().putString(KEY_SESSION, value).apply() }

    var jwt: String?
        get() = prefs.getString(KEY_JWT, null)
        set(value) { prefs.edit().putString(KEY_JWT, value).apply() }

    var jwtExpiresAt: Long
        get() = prefs.getLong(KEY_JWT_EXP, 0L)
        set(value) { prefs.edit().putLong(KEY_JWT_EXP, value).apply() }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) { prefs.edit().putString(KEY_USER_ID, value).apply() }

    val isLoggedIn: Boolean
        get() = !sessionToken.isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val FILE = "first_brain_auth"
        const val KEY_SESSION = "session_token"
        const val KEY_JWT = "jwt"
        const val KEY_JWT_EXP = "jwt_exp"
        const val KEY_USER_ID = "user_id"
    }
}