package org.tinitalk.admin.ssh

import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.server.EndpointParser
import org.tinitalk.admin.server.PinnedPeerVerifier
import org.tinitalk.admin.server.ResolvedEndpoint
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.xfer.InMemorySourceFile

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

    data class ManagedKey(internal val alias: String) : SshCredential {
        override fun close() = Unit
    }
}

data class SshCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

interface SshConnection : Closeable {
    suspend fun exec(
        command: String,
        stdin: ByteArray? = null,
        timeoutMillis: Long? = null,
    ): SshCommandResult
    suspend fun upload(
        bytes: ByteArray,
        remotePath: String,
        mode: Int,
        timeoutMillis: Long? = null,
    )
}

interface SshAccessChecker {
    suspend fun scanHostKey(endpoint: ResolvedEndpoint): PinnedHostKey

    suspend fun connect(
        endpoint: ResolvedEndpoint,
        login: String,
        credential: SshCredential,
        pinnedHostKey: PinnedHostKey,
    ): SshConnection
}

class SshjAccessChecker(
    private val identityStore: ManagedSshIdentityStore,
    private val operationTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES,
) : SshAccessChecker {
    override suspend fun scanHostKey(endpoint: ResolvedEndpoint): PinnedHostKey {
        val capture = CapturingHostKeyVerifier()
        return useClient(hostKeyRejected = { false }) { client ->
            client.addHostKeyVerifier(capture)
            connectToPinnedAddress(client, endpoint)
            capture.observed ?: error("SSH server did not present a host key")
        }
    }

    override suspend fun connect(
        endpoint: ResolvedEndpoint,
        login: String,
        credential: SshCredential,
        pinnedHostKey: PinnedHostKey,
    ): SshConnection {
        val verifier = PinnedHostKeyVerifier(pinnedHostKey)
        val client = newClient()
        var connected = false
        try {
            bounded {
                client.addHostKeyVerifier(verifier)
                connectToPinnedAddress(client, endpoint)
                try {
                    when (credential) {
                        is SshCredential.Password -> client.authPassword(login, credential.chars)
                        is SshCredential.ImportedKey -> client.authPublickey(
                            login,
                            client.loadKeys(credential.identity.keyPair()),
                        )
                        is SshCredential.ManagedKey -> client.authPublickey(
                            login,
                            client.loadKeys(identityStore.load(credential.alias).keyPair),
                        )
                    }
                } catch (_: UserAuthException) {
                    throw SshFailure.AuthenticationFailed()
                }
            }
            connected = true
            return SshjConnection(client, operationTimeoutMillis, maxOutputBytes)
        } catch (error: Throwable) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            throw mapFailure(error, verifier.rejected)
        } finally {
            if (!connected) client.closeIgnoringFailure()
        }
    }

    private suspend fun <T> useClient(
        hostKeyRejected: () -> Boolean,
        block: (SSHClient) -> T,
    ): T {
        val client = newClient()
        return try {
            bounded { block(client) }
        } catch (error: Throwable) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            throw mapFailure(error, hostKeyRejected())
        } finally {
            client.closeIgnoringFailure()
        }
    }

    private fun newClient() = SSHClient(secureSshConfig()).apply {
        connectTimeout = operationTimeoutMillis.toInt()
        timeout = operationTimeoutMillis.toInt()
    }

    private suspend fun <T> bounded(block: () -> T): T = withTimeout(operationTimeoutMillis) {
        runInterruptible(Dispatchers.IO) { block() }
    }

    private fun mapFailure(error: Throwable, hostKeyRejected: Boolean): SshFailure = when {
        error is SshFailure -> error
        hostKeyRejected -> SshFailure.HostKeyChanged()
        error is TimeoutCancellationException ||
            error is InterruptedException ||
            error is SocketTimeoutException ||
            error is TimeoutException -> SshFailure.Timeout()
        else -> SshFailure.Transport(error)
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

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            observed = SshHostKeys.fromPublicKey(key)
            return true
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
        const val DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024
    }
}

private class SshjConnection(
    private val client: SSHClient,
    private val defaultTimeoutMillis: Long,
    private val maxOutputBytes: Int,
) : SshConnection {
    override suspend fun exec(
        command: String,
        stdin: ByteArray?,
        timeoutMillis: Long?,
    ): SshCommandResult = try {
        val effectiveTimeoutMillis = timeoutMillis ?: defaultTimeoutMillis
        withTimeout(effectiveTimeoutMillis) {
            val session = runInterruptible(Dispatchers.IO) { client.startSession() }
            val running = try {
                runInterruptible(Dispatchers.IO) { session.exec(command) }
            } catch (error: Throwable) {
                session.closeIgnoringFailure()
                throw error
            }
            try {
                stdin?.let { bytes ->
                    runInterruptible(Dispatchers.IO) {
                        running.outputStream.use { it.write(bytes) }
                    }
                }
                coroutineScope {
                    val stdout = async(Dispatchers.IO) {
                        runInterruptible { running.inputStream.readBounded(maxOutputBytes) }
                    }
                    val stderr = async(Dispatchers.IO) {
                        runInterruptible { running.errorStream.readBounded(maxOutputBytes) }
                    }
                    val exitCode = runInterruptible(Dispatchers.IO) {
                        running.join(effectiveTimeoutMillis, TimeUnit.MILLISECONDS)
                        running.exitStatus
                    } ?: throw SshFailure.Timeout()
                    SshCommandResult(exitCode, stdout.await(), stderr.await())
                }
            } finally {
                running.closeIgnoringFailure()
                session.closeIgnoringFailure()
            }
        }
    } catch (error: Throwable) {
        if (error is CancellationException && error !is TimeoutCancellationException) throw error
        throw error.asConnectionFailure()
    }

    override suspend fun upload(
        bytes: ByteArray,
        remotePath: String,
        mode: Int,
        timeoutMillis: Long?,
    ) {
        try {
            withTimeout(timeoutMillis ?: defaultTimeoutMillis) {
                runInterruptible(Dispatchers.IO) {
                    client.newSFTPClient().use { sftp ->
                        sftp.put(ByteArraySourceFile(bytes, remotePath.substringAfterLast('/')), remotePath)
                        sftp.chmod(remotePath, mode)
                    }
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            throw error.asConnectionFailure()
        }
    }

    override fun close() {
        client.close()
    }

    private fun Throwable.asConnectionFailure(): SshFailure = when (this) {
        is SshFailure -> this
        is TimeoutCancellationException,
        is InterruptedException,
        is SocketTimeoutException,
        is TimeoutException,
        -> SshFailure.Timeout()
        else -> SshFailure.Transport(this)
    }
}

private class ByteArraySourceFile(
    private val bytes: ByteArray,
    private val filename: String,
) : InMemorySourceFile() {
    override fun getName(): String = filename
    override fun getLength(): Long = bytes.size.toLong()
    override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
    override fun getPermissions(): Int = 384
}

private fun InputStream.readBounded(maxBytes: Int): String {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8_192))
    val chunk = ByteArray(8_192)
    var total = 0
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        total += read
        if (total > maxBytes) throw SshFailure.OutputTooLarge()
        output.write(chunk, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
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
        // Preserve the primary SSH result.
    }
}
