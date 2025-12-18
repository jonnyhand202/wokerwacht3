package com.workwatch.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class CryptoUtils {

    fun calculateHash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    fun calculateCurrentHash(previousHash: ByteArray, logData: ByteArray): ByteArray {
        val combined = previousHash + logData
        return calculateHash(combined)
    }

    fun encryptLogData(data: ByteArray, secretKey: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(data)
        return EncryptedData(
            initializationVector = iv,
            cipherText = cipherText
        )
    }

    fun decryptLogData(encryptedData: EncryptedData, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(encryptedData.initializationVector))
        return cipher.doFinal(encryptedData.cipherText)
    }

    fun generateRandomKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    fun generateRandomBytes(length: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        return bytes
    }

    fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val combined = (password + Base64.getEncoder().encodeToString(salt)).toByteArray()
        return calculateHash(combined)
    }
}

data class EncryptedData(
    val initializationVector: ByteArray,
    val cipherText: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (!cipherText.contentEquals(other.cipherText)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + cipherText.contentHashCode()
        return result
    }
}
