package org.tinitalk.admin.ssh

import org.tinitalk.admin.model.PinnedHostKey
import java.security.KeyPairGenerator
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedHostKeyVerifierTest {
    @Test
    fun acceptsOnlyTheExactPinnedHostKey() {
        val expectedWire = byteArrayOf(0, 0, 0, 7, 115, 115, 104, 45, 114, 115, 97)
        val verifier = PinnedHostKeyVerifier(
            PinnedHostKey(
                algorithm = "ssh-rsa",
                sshWireKeyBase64 = Base64.getEncoder().encodeToString(expectedWire),
                sha256Fingerprint = "SHA256:fixture",
            ),
        )

        assertTrue(verifier.accept("ssh-rsa", expectedWire.copyOf()))
        assertFalse(verifier.accept("ssh-ed25519", expectedWire.copyOf()))
        assertFalse(verifier.accept("ssh-rsa", expectedWire.copyOf().also { it[it.lastIndex]++ }))
    }

    @Test
    fun exposesTheHostKeyRejectedDuringVerification() {
        val expectedKey = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().public
        val observedKey = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().public
        val verifier = PinnedHostKeyVerifier(SshHostKeys.fromPublicKey(expectedKey))

        assertFalse(verifier.verify("server.example", 22, observedKey))
        assertEquals(SshHostKeys.fromPublicKey(observedKey), verifier.rejectedHostKey)
    }
}
