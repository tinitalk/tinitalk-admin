package org.tinitalk.admin.ssh

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.interfaces.RSAPublicKey

class AndroidKeystoreSshIdentityStore(context: Context) : ManagedSshIdentityStore {
    private val applicationContext = context.applicationContext
    private val deviceMarker by lazy { createDeviceMarker() }

    override fun create(serverId: String): ManagedSshIdentity {
        require(SERVER_ID_PATTERN.matches(serverId)) { "Invalid server id" }
        val alias = "$ALIAS_PREFIX$serverId"
        if (!keyStore().containsAlias(alias)) {
            if (Build.VERSION.SDK_INT >= 28 && hasStrongBox()) {
                generateWithStrongBoxFallback(alias)
            } else {
                generate(alias, strongBox = false)
            }
        }
        return load(alias)
    }

    override fun load(alias: String): ManagedSshIdentity {
        serverIdFromAlias(alias)
        val keyStore = keyStore()
        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: error("Managed SSH private key is unavailable")
        val publicKey = keyStore.getCertificate(alias)?.publicKey as? RSAPublicKey
            ?: error("Managed SSH public key is unavailable")
        return ManagedSshIdentity(
            alias = alias,
            keyPair = KeyPair(publicKey, privateKey),
            openSshPublicKey = OpenSshPublicKey.encode(publicKey),
            marker = deviceMarker,
        )
    }

    override fun delete(alias: String) {
        serverIdFromAlias(alias)
        val keyStore = keyStore()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    @RequiresApi(28)
    private fun generateWithStrongBoxFallback(alias: String) {
        try {
            generate(alias, strongBox = true)
        } catch (_: ProviderException) {
            keyStore().deleteEntry(alias)
            generate(alias, strongBox = false)
        }
    }

    @RequiresApi(28)
    private fun hasStrongBox(): Boolean = applicationContext.packageManager.hasSystemFeature(
        PackageManager.FEATURE_STRONGBOX_KEYSTORE,
    )

    private fun generate(alias: String, strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setKeySize(KEY_SIZE_BITS)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .apply {
                if (Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(strongBox)
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE).apply {
            initialize(spec)
        }.generateKeyPair()
    }

    private fun serverIdFromAlias(alias: String): String {
        require(alias.startsWith(ALIAS_PREFIX)) { "Not a managed SSH key alias" }
        return alias.removePrefix(ALIAS_PREFIX).also {
            require(SERVER_ID_PATTERN.matches(it)) { "Invalid managed SSH key alias" }
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun createDeviceMarker(): String {
        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        require(!androidId.isNullOrBlank()) { "Android device id is unavailable" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$MARKER_HASH_CONTEXT$androidId".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        return "$MARKER_PREFIX$hash"
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS_PREFIX = "tinitalk_admin_ssh_"
        const val MARKER_PREFIX = "tinitalk-admin:device-"
        const val MARKER_HASH_CONTEXT = "tinitalk-admin:ssh-device:"
        const val KEY_SIZE_BITS = 3072
        val SERVER_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
    }
}
