package org.tinitalk.admin.ssh

import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.server.EndpointParser
import org.tinitalk.admin.server.PinnedPeerVerifier
import org.tinitalk.admin.server.ResolvedEndpoint
import java.io.Closeable
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException

sealed interface SshCredential : Closeable {
    class Password(internal val chars: CharArray) : SshCredential {
        override fun close() {
            chars.fill('\u0000')
        }
    }

    class ImportedKey(internal val identity: ImportedSshIdentity) : SshCredential {
        override fun close() {
            identity.close()
        }
    }
}

interface SshAccessChecker {
    suspend fun scanHostKey(endpoint: ResolvedEndpoint): PinnedHostKey

    suspend fun verifyAccess(
        endpoint: ResolvedEndpoint,
        login: String,
        credential: SshCredential,
        pinnedHostKey: PinnedHostKey,
    )
}

class SshjAccessChecker(
    private val operationTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : SshAccessChecker {
    override suspend fun scanHostKey(endpoint: ResolvedEndpoint): PinnedHostKey {
        val capture = CapturingHostKeyVerifier()
        return runSshOperation(hostKeyRejected = { false }) { client ->
            client.addHostKeyVerifier(capture)
            connectToPinnedAddress(client, endpoint)
            capture.observed ?: throw IllegalStateException("SSH server did not present a host key")
        }
    }

    override suspend fun verifyAccess(
        endpoint: ResolvedEndpoint,
        login: String,
        credential: SshCredential,
        pinnedHostKey: PinnedHostKey,
    ) {
        val verifier = PinnedHostKeyVerifier(pinnedHostKey)
        try {
            runSshOperation(hostKeyRejected = { verifier.rejected }) { client ->
                client.addHostKeyVerifier(verifier)
                connectToPinnedAddress(client, endpoint)
                try {
                    when (credential) {
                        is SshCredential.Password -> client.authPassword(login, credential.chars)
                        is SshCredential.ImportedKey -> client.authPublickey(
                            login,
                            client.loadKeys(credential.identity.keyPair()),
                        )
                    }
                } catch (_: UserAuthException) {
                    throw SshFailure.AuthenticationFailed()
                }
            }
        } finally {
            credential.close()
        }
    }

    private suspend fun <T> runSshOperation(
        hostKeyRejected: () -> Boolean,
        block: (SSHClient) -> T,
    ): T {
        val client = SSHClient(secureSshConfig()).apply {
            connectTimeout = operationTimeoutMillis.toInt()
            timeout = operationTimeoutMillis.toInt()
        }
        return try {
            withTimeout(operationTimeoutMillis) {
                runInterruptible(Dispatchers.IO) { block(client) }
            }
        } catch (error: Throwable) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            throw when {
                error is SshFailure -> error
                hostKeyRejected() -> SshFailure.HostKeyChanged()
                error is TimeoutCancellationException ||
                    error is InterruptedException ||
                    error is SocketTimeoutException ||
                    error is TimeoutException -> SshFailure.Timeout()
                else -> SshFailure.Transport(error)
            }
        } finally {
            client.closeIgnoringFailure()
        }
    }

    private fun connectToPinnedAddress(client: SSHClient, endpoint: ResolvedEndpoint) {
        val bytes = EndpointParser.parseIpv4Bytes(endpoint.frozenIpv4)
            ?: throw IllegalArgumentException("Invalid frozen IPv4 address")
        val address = InetAddress.getByAddress(endpoint.enteredAddress, bytes)
        client.connect(address, endpoint.sshPort)
        PinnedPeerVerifier.requireMatch(endpoint.frozenIpv4, client.remoteAddress)
    }

    private class CapturingHostKeyVerifier : HostKeyVerifier {
        var observed: PinnedHostKey? = null
            private set

        override fun verify(hostname: String, port: Int, key: java.security.PublicKey): Boolean {
            observed = SshHostKeys.fromPublicKey(key)
            return true
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}

private fun secureSshConfig(): DefaultConfig = DefaultSecurityProviderConfig().apply {
    keyAlgorithms = keyAlgorithms.filter { it.name in MODERN_HOST_KEY_ALGORITHMS }
    keyExchangeFactories = keyExchangeFactories.filter { it.name in MODERN_KEY_EXCHANGES }
    cipherFactories = cipherFactories.filter { it.name in MODERN_CIPHERS }
    macFactories = macFactories.filter { it.name in MODERN_MACS }
    check(keyAlgorithms.isNotEmpty()) { "No modern SSH host-key algorithms available" }
    check(keyExchangeFactories.isNotEmpty()) { "No modern SSH key exchanges available" }
    check(cipherFactories.isNotEmpty()) { "No modern SSH ciphers available" }
    check(macFactories.isNotEmpty()) { "No modern SSH MACs available" }
}

private val MODERN_HOST_KEY_ALGORITHMS = setOf(
    "rsa-sha2-512",
    "rsa-sha2-256",
)

private val MODERN_KEY_EXCHANGES = setOf(
    "ecdh-sha2-nistp521",
    "ecdh-sha2-nistp384",
    "ecdh-sha2-nistp256",
    "diffie-hellman-group14-sha256",
    "diffie-hellman-group15-sha512",
    "diffie-hellman-group16-sha512",
    "diffie-hellman-group17-sha512",
    "diffie-hellman-group18-sha512",
    "ext-info-c",
)

private val MODERN_CIPHERS = setOf(
    "chacha20-poly1305@openssh.com",
    "aes128-gcm@openssh.com",
    "aes256-gcm@openssh.com",
    "aes128-ctr",
    "aes192-ctr",
    "aes256-ctr",
)

private val MODERN_MACS = setOf(
    "hmac-sha2-256-etm@openssh.com",
    "hmac-sha2-512-etm@openssh.com",
    "hmac-sha2-256",
    "hmac-sha2-512",
)

private fun Closeable.closeIgnoringFailure() {
    try {
        close()
    } catch (_: Exception) {
        // Keep the original SSH result.
    }
}
