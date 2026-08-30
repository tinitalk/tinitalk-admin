package org.tinitalk.admin.ssh

import org.tinitalk.admin.model.PinnedHostKey
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import net.schmizz.sshj.transport.verification.HostKeyVerifier

class PinnedHostKeyVerifier(
    expected: PinnedHostKey,
) : HostKeyVerifier {
    private val expectedAlgorithm = expected.algorithm.toByteArray(Charsets.UTF_8)
    private val expectedWire = Base64.getDecoder().decode(expected.sshWireKeyBase64)

    var rejected: Boolean = false
        private set

    fun accept(algorithm: String, wire: ByteArray): Boolean {
        val algorithmMatches = MessageDigest.isEqual(
            expectedAlgorithm,
            algorithm.toByteArray(Charsets.UTF_8),
        )
        val wireMatches = MessageDigest.isEqual(expectedWire, wire)
        return algorithmMatches and wireMatches
    }

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val observed = SshHostKeys.fromPublicKey(key)
        val accepted = accept(
            observed.algorithm,
            Base64.getDecoder().decode(observed.sshWireKeyBase64),
        )
        rejected = !accepted
        return accepted
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}
