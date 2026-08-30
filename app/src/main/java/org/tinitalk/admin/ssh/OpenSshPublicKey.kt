package org.tinitalk.admin.ssh

import java.security.interfaces.RSAPublicKey
import java.util.Base64
import net.schmizz.sshj.common.Buffer

object OpenSshPublicKey {
    fun encode(publicKey: RSAPublicKey): String {
        val buffer = Buffer.PlainBuffer()
            .putString(SSH_RSA)
            .putMPInt(publicKey.publicExponent)
            .putMPInt(publicKey.modulus)
        return "$SSH_RSA ${Base64.getEncoder().encodeToString(buffer.compactData)}"
    }

    private const val SSH_RSA = "ssh-rsa"
}
