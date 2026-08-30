package org.tinitalk.admin.ssh

import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.server.ResolvedEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class MissingAdministrativeAccessException :
    IllegalStateException("Root or passwordless sudo is required")

class ManagedAccessBootstrapper(
    private val ssh: SshAccessChecker,
    private val identityStore: ManagedSshIdentityStore,
    private val installer: AuthorizedKeyInstaller,
) {
    suspend fun <T> bootstrap(
        serverId: String,
        endpoint: ResolvedEndpoint,
        login: String,
        initialCredential: SshCredential,
        hostKey: PinnedHostKey,
        onVerified: suspend (keystoreAlias: String) -> T,
    ): T {
        val identity = identityStore.create(serverId)
        var installation: RemoteKeyInstallation? = null
        try {
            return ssh.connect(endpoint, login, initialCredential, hostKey).useSshConnection { initialConnection ->
                requireAdministrativeAccess(initialConnection, login)
                installation = installer.install(initialConnection, identity)

                ssh.connect(
                    endpoint = endpoint,
                    login = login,
                    credential = SshCredential.ManagedKey(identity.alias),
                    pinnedHostKey = hostKey,
                ).useSshConnection { managedConnection ->
                    requireAdministrativeAccess(managedConnection, login)
                }

                val result = onVerified(identity.alias)
                withContext(NonCancellable) {
                    runCatching { installer.cleanup(initialConnection, checkNotNull(installation)) }
                }
                result
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                installation?.let { remote ->
                    runCatching {
                        ssh.connect(endpoint, login, initialCredential, hostKey).useSshConnection { connection ->
                            installer.remove(connection, remote)
                            installer.cleanup(connection, remote)
                        }
                    }
                }
                runCatching { identityStore.delete(identity.alias) }
            }
            throw error
        }
    }

    private suspend fun requireAdministrativeAccess(connection: SshConnection, login: String) {
        val result = if (login == "root") {
            connection.exec("LC_ALL=C id -u")
        } else {
            connection.exec("LC_ALL=C sudo -n true")
        }
        val accepted = result.exitCode == 0 && (login != "root" || result.stdout.trim() == "0")
        if (!accepted) throw MissingAdministrativeAccessException()
    }
}

private suspend fun <T> SshConnection.useSshConnection(
    block: suspend (SshConnection) -> T,
): T = try {
    block(this)
} finally {
    withContext(NonCancellable + Dispatchers.IO) {
        runCatching { close() }
    }
}
