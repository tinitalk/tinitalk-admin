package org.tinitalk.admin.ssh

import org.tinitalk.admin.model.PinnedHostKey
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType

object SshHostKeys {
    fun fromPublicKey(publicKey: PublicKey): PinnedHostKey {
        val keyType = KeyType.fromKey(publicKey)
        require(keyType != KeyType.UNKNOWN) { "Unsupported SSH host key type" }
        val buffer = Buffer.PlainBuffer()
        keyType.putPubKeyIntoBuffer(publicKey, buffer)
        val wire = buffer.compactData
        return PinnedHostKey(
            algorithm = keyType.toString(),
            sshWireKeyBase64 = Base64.getEncoder().encodeToString(wire),
            sha256Fingerprint = sha256Fingerprint(wire),
        )
    }

    private fun sha256Fingerprint(wire: ByteArray): String = "SHA256:" +
        Base64.getEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(wire),
        )
}
