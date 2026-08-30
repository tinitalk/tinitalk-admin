package org.tinitalk.admin.data

import android.content.Context
import com.google.gson.Gson
import org.tinitalk.admin.server.InitialSetupStep

data class StoredServerSetup(
    val configured: Boolean,
    val startedAtEpochMillis: Long? = null,
    val currentStep: InitialSetupStep? = null,
    val operationStarted: Boolean = false,
    val completedSteps: Set<InitialSetupStep>? = null,
    val binaryUri: String? = null,
    val firebaseAndroidConfigUri: String? = null,
    val firebaseServiceAccountUri: String? = null,
) {
    val inProgress: Boolean
        get() = !configured && startedAtEpochMillis != null && currentStep != null

    val completedStepSet: Set<InitialSetupStep>
        get() = completedSteps.orEmpty()
}

interface ServerSetupStore {
    fun get(serverId: String): StoredServerSetup?
    fun put(serverId: String, setup: StoredServerSetup)
    fun remove(serverId: String)
}

class SharedPreferencesServerSetupStore(
    context: Context,
    private val gson: Gson = Gson(),
) : ServerSetupStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun get(serverId: String): StoredServerSetup? {
        val raw = preferences.getString(serverId, null) ?: return null
        return runCatching { gson.fromJson(raw, StoredServerSetup::class.java) }
            .getOrNull()
            ?.takeIf { it.configured || it.inProgress }
    }

    override fun put(serverId: String, setup: StoredServerSetup) {
        check(preferences.edit().putString(serverId, gson.toJson(setup)).commit()) {
            "Failed to save server setup state"
        }
    }

    override fun remove(serverId: String) {
        check(preferences.edit().remove(serverId).commit()) {
            "Failed to remove server setup state"
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "tinitalk_server_setup"
    }
}
