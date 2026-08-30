package org.tinitalk.admin.ssh

import android.content.ContentResolver
import android.net.Uri
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.schmizz.sshj.userauth.keyprovider.BaseFileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyFormat
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

class ImportedSshIdentityException :
    IllegalArgumentException("Unsupported or unreadable SSH private key")

class ImportedSshIdentity internal constructor(keyPair: KeyPair) : Closeable {
    private var current: KeyPair? = keyPair

    internal fun keyPair(): KeyPair = checkNotNull(current) { "Imported SSH identity is closed" }

    override fun close() {
        current = null
    }
}

class ImportedKeyReader(
    private val contentResolver: ContentResolver,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    suspend fun read(uri: Uri, passphrase: CharArray): ImportedSshIdentity =
        withContext(Dispatchers.IO) {
            runInterruptible {
                val bytes = ByteArray(maxBytes + 1)
                var length = 0
                try {
                    contentResolver.openInputStream(uri)?.use { source ->
                        while (length < bytes.size) {
                            val read = source.read(bytes, length, bytes.size - length)
                            if (read < 0) break
                            if (read > 0) length += read
                        }
                        if (length == bytes.size || source.read() >= 0) {
                            throw ImportedSshIdentityException()
                        }
                    } ?: throw ImportedSshIdentityException()
                    parse(bytes.copyOf(length), passphrase.takeIf { it.isNotEmpty() })
                } catch (error: CancellationException) {
                    throw error
                } catch (_: ImportedSshIdentityException) {
                    throw ImportedSshIdentityException()
                } catch (_: Exception) {
                    throw ImportedSshIdentityException()
                } finally {
                    bytes.fill(0)
                    passphrase.fill('\u0000')
                }
            }
        }

    private fun parse(keyBytes: ByteArray, passphrase: CharArray?): ImportedSshIdentity {
        try {
            val format = keyBytes.reader().use { KeyProviderUtil.detectKeyFileFormat(it, false) }
            val provider: BaseFileKeyProvider = when (format) {
                KeyFormat.OpenSSHv1 -> OpenSSHKeyV1KeyFile()
                KeyFormat.OpenSSH -> OpenSSHKeyFile()
                KeyFormat.PKCS8 -> PKCS8KeyFile()
                else -> throw ImportedSshIdentityException()
            }
            return keyBytes.reader().use { reader ->
                provider.init(reader, passphrase?.let(::SinglePasswordFinder))
                ImportedSshIdentity(KeyPair(provider.public, provider.private))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: ImportedSshIdentityException) {
            throw ImportedSshIdentityException()
        } catch (_: Exception) {
            throw ImportedSshIdentityException()
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun ByteArray.reader() = InputStreamReader(
        ByteArrayInputStream(this),
        StandardCharsets.US_ASCII,
    )

    private companion object {
        const val DEFAULT_MAX_BYTES = 1024 * 1024
    }
}

private class SinglePasswordFinder(
    private val passphrase: CharArray,
) : PasswordFinder {
    override fun reqPassword(resource: Resource<*>?): CharArray = passphrase
    override fun shouldRetry(resource: Resource<*>?): Boolean = false
}
