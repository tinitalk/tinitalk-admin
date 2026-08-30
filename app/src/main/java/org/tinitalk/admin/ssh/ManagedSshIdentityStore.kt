package org.tinitalk.admin.ssh

import java.security.KeyPair

data class ManagedSshIdentity(
    val alias: String,
    val keyPair: KeyPair,
    val openSshPublicKey: String,
    val marker: String,
)

interface ManagedSshIdentityStore {
    fun create(serverId: String): ManagedSshIdentity
    fun load(alias: String): ManagedSshIdentity
    fun delete(alias: String)
}
