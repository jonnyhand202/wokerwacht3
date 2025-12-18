package com.workwatch.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyStoreManager {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "workwatch_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_CBC
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_PKCS7
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    fun getOrCreateKey(): SecretKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null)
        return if (entry is KeyStore.SecretKeyEntry) {
            entry.secretKey
        } else {
            generateAndStoreKey()
        }
    }

    private fun generateAndStoreKey(): SecretKey {
        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setRandomizedEncryptionRequired(true)
            .build()

        val keyGen = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)
        keyGen.init(keyGenSpec)
        return keyGen.generateKey()
    }

    fun deleteKey() {
        keyStore.deleteEntry(KEY_ALIAS)
    }

    fun keyExists(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }
}
