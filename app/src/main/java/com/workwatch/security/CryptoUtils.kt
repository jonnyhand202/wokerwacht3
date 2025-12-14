package com.workwatch.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoUtils {
    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 96 / 8 // 12 bytes for GCM
        private const val HASH_ALGORITHM = "SHA-256"
    }

    /**
     * Generate a 256-bit AES key
     */
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance(ALGORITHM)
        keyGen.init(256)
        return keyGen.generateKey()
    }

    /**
     * Encrypt data using AES-GCM
     */
    fun encryptData(data: ByteArray, key: SecretKey): EncryptedResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv // GCM IV
        val encryptedData = cipher.doFinal(data)

        return EncryptedResult(
            encryptedData = encryptedData,
            iv = iv,
            tag = encryptedData.sliceArray(encryptedData.size - 16 until encryptedData.size) // Last 16 bytes are tag
        )
    }

    /**
     * Decrypt data using AES-GCM
     */
    fun decryptData(encryptedData: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(encryptedData)
    }

    /**
     * Encrypt log data with a key
     */
    fun encryptLogData(data: ByteArray, key: SecretKey): EncryptedResult {
        return encryptData(data, key)
    }

    /**
     * Calculate SHA-256 hash of data
     */
    fun calculateHash(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(HASH_ALGORITHM)
        return md.digest(data)
    }

    /**
     * Calculate hash chain: hash(previousHash + currentData)
     */
    fun calculateCurrentHash(data: ByteArray, previousHash: ByteArray): ByteArray {
        val md = MessageDigest.getInstance(HASH_ALGORITHM)
        md.update(previousHash)
        md.update(data)
        return md.digest()
    }

    /**
     * Convert bytes to Base64 string
     */
    fun encodeToBase64(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    /**
     * Convert Base64 string to bytes
     */
    fun decodeFromBase64(encoded: String): ByteArray {
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    /**
     * Derive a key from a password
     */
    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val md = MessageDigest.getInstance(HASH_ALGORITHM)
        md.update(salt)
        val keyBytes = md.digest(password.toByteArray())
        return SecretKeySpec(keyBytes, 0, 32, ALGORITHM)
    }
}

/**
 * Result of encryption operation
 */
data class EncryptedResult(
    val encryptedData: ByteArray,
    val iv: ByteArray,
    val tag: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedResult
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!tag.contentEquals(other.tag)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = encryptedData.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        return result
    }
}
