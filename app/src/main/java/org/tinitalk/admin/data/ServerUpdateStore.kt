package org.tinitalk.admin.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class StoredServerUpdate(
    @SerializedName("startedAtEpochMillis") val startedAtEpochMillis: Long,
    @SerializedName("binaryUri") val binaryUri: String,
)

interface ServerUpdateStore {
    fun get(serverId: String): StoredServerUpdate?
    fun put(serverId: String, update: StoredServerUpdate)
    fun remove(serverId: String)
}

class SharedPreferencesServerUpdateStore(
    context: Context,
    private val gson: Gson = Gson(),
) : ServerUpdateStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun get(serverId: String): StoredServerUpdate? {
        val raw = preferences.getString(serverId, null) ?: return null
        return runCatching { gson.fromJson(raw, StoredServerUpdate::class.java) }
            .getOrNull()
            ?.takeIf { it.startedAtEpochMillis > 0 && it.binaryUri.isNotBlank() }
    }

    override fun put(serverId: String, update: StoredServerUpdate) {
        check(preferences.edit().putString(serverId, gson.toJson(update)).commit()) {
            "Failed to save server update state"
        }
    }

    override fun remove(serverId: String) {
        check(preferences.edit().remove(serverId).commit()) {
            "Failed to remove server update state"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "tinitalk_server_updates"
    }
}
