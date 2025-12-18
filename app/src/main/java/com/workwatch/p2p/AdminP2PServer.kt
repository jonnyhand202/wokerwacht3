package com.workwatch.p2p

import com.workwatch.data.HashLeakDao
import com.workwatch.security.CryptoUtils
import com.workwatch.entities.HashLeak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminP2PServer(
    private val leakDao: HashLeakDao,
    private val cryptoUtils: CryptoUtils
) {

    suspend fun storeHashLeak(leak: HashLeak): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                leakDao.insert(leak)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun validateAndStoreHashLeak(leak: HashLeak): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Validate the leak data
                if (leak.workerId.isNotEmpty() && leak.hashBase64.isNotEmpty()) {
                    leakDao.insert(leak)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    fun generateP2PMessage(leak: HashLeak): String {
        return """
            {
                "type": "hash_leak",
                "workerId": "${leak.workerId}",
                "hash": "${leak.hashBase64}",
                "timestamp": ${leak.timestamp},
                "nonce": "${leak.nonce}"
            }
        """.trimIndent()
    }
}
