package com.workwatch.reporting

import com.workwatch.security.CryptoUtils
import com.workwatch.entities.HashLeak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class WorkerLeakSender(private val cryptoUtils: CryptoUtils) {

    suspend fun sendHashLeak(leak: HashLeak): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Encrypt the hash before sending
                val encryptedHash = cryptoUtils.calculateHash(leak.hashBase64.toByteArray())
                // Here you would send to a server or write to local storage
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun createHashLeak(
        workerId: String,
        hashValue: ByteArray,
        nonce: String
    ): HashLeak {
        return HashLeak(
            workerId = workerId,
            hashBase64 = Base64.getEncoder().encodeToString(hashValue),
            timestamp = System.currentTimeMillis(),
            nonce = nonce
        )
    }
}
