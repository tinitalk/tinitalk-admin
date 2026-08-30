package org.tinitalk.admin.model

import com.google.gson.annotations.SerializedName

data class PinnedHostKey(
    @SerializedName("algorithm") val algorithm: String,
    @SerializedName("ssh_wire_key_base64") val sshWireKeyBase64: String,
    @SerializedName("sha256_fingerprint") val sha256Fingerprint: String,
)

data class ServerRecord(
    @SerializedName("id") val id: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("entered_address") val enteredAddress: String,
    @SerializedName("frozen_ipv4") val frozenIpv4: String,
    @SerializedName("ssh_port") val sshPort: Int,
    @SerializedName("ssh_login") val sshLogin: String,
    @SerializedName("host_key") val hostKey: PinnedHostKey,
    @SerializedName("keystore_alias") val keystoreAlias: String,
    @SerializedName("verified_at_epoch_millis") val verifiedAtEpochMillis: Long,
)

val ServerRecord.displayTitle: String
    get() = displayName.ifBlank { enteredAddress }
