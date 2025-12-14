package com.workwatch.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeyStoreManager {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "worker_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Generate a new key in Android Keystore
     */
    fun generateKey(keyAlias: String = KEY_ALIAS): SecretKey {
        if (keyExists(keyAlias)) {
            return getKey(keyAlias)
        }

        val keyGen = KeyGenerator.getInstance(ALGORITHM, KEYSTORE_PROVIDER)

        val keyGenParams = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(256)
        }

        keyGen.init(keyGenParams.build())
        return keyGen.generateKey()
    }

    /**
     * Check if key exists
     */
    fun keyExists(keyAlias: String = KEY_ALIAS): Boolean {
        return keyStore.containsAlias(keyAlias)
    }

    /**
     * Get key from keystore
     */
    fun getKey(keyAlias: String = KEY_ALIAS): SecretKey {
        return keyStore.getKey(keyAlias, null) as? SecretKey
            ?: throw IllegalStateException("Key not found: $keyAlias")
    }

    /**
     * Delete key from keystore
     */
    fun deleteKey(keyAlias: String = KEY_ALIAS) {
        keyStore.deleteEntry(keyAlias)
    }

    /**
     * Get or create key
     */
    fun getOrCreateKey(keyAlias: String = KEY_ALIAS): SecretKey {
        return if (keyExists(keyAlias)) {
            getKey(keyAlias)
        } else {
            generateKey(keyAlias)
        }
    }
}
