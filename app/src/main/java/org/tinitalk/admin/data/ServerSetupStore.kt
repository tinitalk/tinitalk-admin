package org.tinitalk.admin.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.tinitalk.admin.server.InitialSetupStep

enum class StoredServerSetupStatus {
    SSH_HOST_KEY_CHANGED,
}

data class StoredServerSetup(
    @SerializedName("configured") val configured: Boolean,
    @SerializedName("startedAtEpochMillis") val startedAtEpochMillis: Long? = null,
    @SerializedName("currentStep") val currentStep: InitialSetupStep? = null,
    @SerializedName("operationStarted") val operationStarted: Boolean = false,
    @SerializedName("completedSteps") val completedSteps: Set<InitialSetupStep>? = null,
    @SerializedName("binaryUri") val binaryUri: String? = null,
    @SerializedName("status") val status: StoredServerSetupStatus? = null,
    @SerializedName("observedFingerprint") val observedFingerprint: String? = null,
) {
    val inProgress: Boolean
        get() = !configured && startedAtEpochMillis != null && currentStep != null

    val completedStepSet: Set<InitialSetupStep>
        get() = completedSteps.orEmpty()

    val hostKeyChanged: Boolean
        get() = status == StoredServerSetupStatus.SSH_HOST_KEY_CHANGED
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
            ?.takeIf { it.configured || it.inProgress || it.hostKeyChanged }
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
