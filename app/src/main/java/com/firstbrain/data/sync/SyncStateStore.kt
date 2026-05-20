// AI-assisted: drafted with Claude (Anthropic), reviewed and adapted by the team.
// See README §12 for the team's originality statement.

package com.firstbrain.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the high-water-mark of `updated_at` seen on the server,
 * keyed per signed-in user so account switches don't pull stale state.
 */
@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastSyncedAt(userId: String): String? =
        prefs.getString(key(userId), null)

    fun setLastSyncedAt(userId: String, iso: String) {
        prefs.edit().putString(key(userId), iso).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(userId: String) = "last_synced_at:$userId"

    private companion object {
        const val FILE = "first_brain_sync"
    }
}