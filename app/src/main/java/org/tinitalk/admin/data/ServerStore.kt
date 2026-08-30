package org.tinitalk.admin.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.model.ServerRecord
import org.tinitalk.admin.model.displayTitle

interface ServerStore {
    fun list(): List<ServerRecord>
    fun put(record: ServerRecord)
    fun rename(id: String, displayName: String)
    fun remove(id: String)
}

class SharedPreferencesServerStore(
    context: Context,
    private val gson: Gson = Gson(),
) : ServerStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    override fun list(): List<ServerRecord> = synchronized(lock) {
        readRecords().sortedWith(RECORD_ORDER)
    }

    override fun put(record: ServerRecord) = synchronized(lock) {
        val records = readRecords().toMutableList()
        check(records.filter { it.hasSameHostAs(record) }.all { it.hasSameHostKeyAs(record) }) {
            "Refusing to replace a saved SSH host key"
        }
        val existingIndex = records.indexOfFirst { it.hasSameAccountAs(record) }
        if (existingIndex >= 0) {
            records[existingIndex] = record.copy(id = records[existingIndex].id)
        } else {
            records += record
        }
        writeRecords(records)
    }

    override fun rename(id: String, displayName: String) = synchronized(lock) {
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        check(index >= 0) { "Server not found" }
        records[index] = records[index].copy(displayName = displayName)
        writeRecords(records)
    }

    override fun remove(id: String) = synchronized(lock) {
        val records = readRecords().toMutableList()
        check(records.removeAll { it.id == id }) { "Server not found" }
        writeRecords(records)
    }

    private fun writeRecords(records: List<ServerRecord>) {
        val json = gson.toJson(
            ServerEnvelope(
                version = CURRENT_VERSION,
                records = records.sortedWith(RECORD_ORDER),
            ),
        )
        check(preferences.edit().putString(RECORDS_KEY, json).commit()) {
            "Failed to persist server list"
        }
    }

    private fun readRecords(): List<ServerRecord> {
        val raw = preferences.getString(RECORDS_KEY, null) ?: return emptyList()
        return try {
            val envelope = gson.fromJson(raw, StoredEnvelope::class.java)
                ?: throw IllegalStateException("Saved server list is empty")
            check(envelope.version == CURRENT_VERSION) {
                "Unsupported server list version: ${envelope.version}"
            }
            envelope.records.orEmpty().map(StoredServerRecord::toModel)
        } catch (_: RuntimeException) {
            quarantineUnreadableRecords(raw)
            emptyList()
        }
    }

    private fun quarantineUnreadableRecords(raw: String) {
        check(
            preferences.edit()
                .putString(QUARANTINED_RECORDS_KEY, raw)
                .remove(RECORDS_KEY)
                .commit(),
        ) {
            "Failed to quarantine unreadable server list"
        }
    }

    private data class ServerEnvelope(
        @SerializedName("version") val version: Int,
        @SerializedName("records") val records: List<ServerRecord>,
    )

    private data class StoredEnvelope(
        @SerializedName("version") val version: Int?,
        @SerializedName("records") val records: List<StoredServerRecord>?,
    )

    private data class StoredServerRecord(
        @SerializedName("id") val id: String?,
        @SerializedName("display_name") val displayName: String?,
        @SerializedName("entered_address") val enteredAddress: String?,
        @SerializedName("frozen_ipv4") val frozenIpv4: String?,
        @SerializedName("ssh_port") val sshPort: Int?,
        @SerializedName("ssh_login") val sshLogin: String?,
        @SerializedName("host_key") val hostKey: StoredHostKey?,
        @SerializedName("verified_at_epoch_millis") val verifiedAtEpochMillis: Long?,
    ) {
        fun toModel(): ServerRecord {
            val model = ServerRecord(
                id = id.required("id"),
                displayName = displayName.present("display_name"),
                enteredAddress = enteredAddress.required("entered_address"),
                frozenIpv4 = frozenIpv4.required("frozen_ipv4"),
                sshPort = requireNotNull(sshPort) { "Missing saved field: ssh_port" },
                sshLogin = sshLogin.required("ssh_login"),
                hostKey = requireNotNull(hostKey) { "Missing saved field: host_key" }.toModel(),
                verifiedAtEpochMillis = requireNotNull(verifiedAtEpochMillis) {
                    "Missing saved field: verified_at_epoch_millis"
                },
            )
            check(model.sshPort in 1..65_535) { "Saved SSH port is invalid" }
            check(model.verifiedAtEpochMillis > 0) { "Saved verification time is invalid" }
            return model
        }
    }

    private data class StoredHostKey(
        @SerializedName("algorithm") val algorithm: String?,
        @SerializedName("ssh_wire_key_base64") val sshWireKeyBase64: String?,
        @SerializedName("sha256_fingerprint") val sha256Fingerprint: String?,
    ) {
        fun toModel() = PinnedHostKey(
            algorithm = algorithm.required("host_key.algorithm"),
            sshWireKeyBase64 = sshWireKeyBase64.required("host_key.ssh_wire_key_base64"),
            sha256Fingerprint = sha256Fingerprint.required("host_key.sha256_fingerprint")
                .also { check(it.startsWith("SHA256:")) { "Saved fingerprint is invalid" } },
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "tinitalk_admin"
        const val RECORDS_KEY = "server_records_v1"
        const val QUARANTINED_RECORDS_KEY = "server_records_unreadable"
        const val CURRENT_VERSION = 1
        val RECORD_ORDER = compareBy<ServerRecord>({ it.displayTitle.lowercase() }, { it.id })
    }
}

private fun String?.required(field: String): String =
    requireNotNull(this?.takeIf(String::isNotBlank)) { "Missing saved field: $field" }

private fun String?.present(field: String): String =
    requireNotNull(this) { "Missing saved field: $field" }

private fun ServerRecord.hasSameHostAs(other: ServerRecord): Boolean =
    enteredAddress == other.enteredAddress &&
        sshPort == other.sshPort

private fun ServerRecord.hasSameAccountAs(other: ServerRecord): Boolean =
    hasSameHostAs(other) &&
        sshLogin == other.sshLogin

private fun ServerRecord.hasSameHostKeyAs(other: ServerRecord): Boolean =
    hostKey.algorithm == other.hostKey.algorithm &&
        hostKey.sshWireKeyBase64 == other.hostKey.sshWireKeyBase64
