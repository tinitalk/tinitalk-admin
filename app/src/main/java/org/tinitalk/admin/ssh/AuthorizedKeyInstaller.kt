package org.tinitalk.admin.ssh

import android.content.Context
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class RemoteKeyInstallation(
    val directory: String,
    val marker: String,
)

class AuthorizedKeyInstaller(context: Context) {
    private val assets = context.applicationContext.assets
    private val installScript = assets.open(INSTALL_SCRIPT).use { it.readBytes() }
    private val removeScript = assets.open(REMOVE_SCRIPT).use { it.readBytes() }

    suspend fun install(
        connection: SshConnection,
        identity: ManagedSshIdentity,
    ): RemoteKeyInstallation {
        val staging = connection.exec(CREATE_STAGING_COMMAND)
        check(staging.exitCode == 0 && staging.stderr.isBlank()) {
            "Failed to create managed-key staging directory"
        }
        val installation = RemoteKeyInstallation(staging.stdout.trim(), identity.marker)
        validate(installation)

        return try {
            val directory = installation.directory
            val key = "restrict ${identity.openSshPublicKey} ${identity.marker}\n".toByteArray()
            connection.upload(installScript, "$directory/$INSTALL_SCRIPT", SCRIPT_MODE)
            connection.upload(removeScript, "$directory/$REMOVE_SCRIPT", SCRIPT_MODE)
            connection.upload(key, "$directory/$KEY_FILE", KEY_MODE)
            val result = connection.exec(
                "LC_ALL=C sh '$directory/$INSTALL_SCRIPT' '$directory/$KEY_FILE' '${identity.marker}'",
            )
            check(result.exitCode == 0) { "Failed to install managed SSH key" }
            installation
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                runCatching { remove(connection, installation) }
                runCatching { cleanup(connection, installation) }
            }
            throw error
        }
    }

    suspend fun remove(connection: SshConnection, installation: RemoteKeyInstallation) {
        validate(installation)
        val result = connection.exec(
            "LC_ALL=C sh '${installation.directory}/$REMOVE_SCRIPT' '${installation.marker}'",
        )
        check(result.exitCode == 0) { "Failed to remove managed SSH key" }
    }

    suspend fun cleanup(connection: SshConnection, installation: RemoteKeyInstallation) {
        validate(installation)
        connection.exec("LC_ALL=C rm -rf -- '${installation.directory}'")
    }

    private fun validate(installation: RemoteKeyInstallation) {
        require(REMOTE_DIRECTORY.matches(installation.directory)) { "Unsafe staging directory" }
        require(MARKER.matches(installation.marker)) { "Unsafe managed-key marker" }
    }

    private companion object {
        const val INSTALL_SCRIPT = "install_managed_key.sh"
        const val REMOVE_SCRIPT = "remove_managed_key.sh"
        const val KEY_FILE = "managed_key"
        const val CREATE_STAGING_COMMAND = "LC_ALL=C umask 077; mktemp -d /tmp/tinitalk-admin.XXXXXXXXXX"
        const val SCRIPT_MODE = 448
        const val KEY_MODE = 384
        val REMOTE_DIRECTORY = Regex("^/tmp/tinitalk-admin\\.[A-Za-z0-9]+$")
        val MARKER = Regex("^tinitalk-admin:[A-Za-z0-9_-]+$")
    }
}
