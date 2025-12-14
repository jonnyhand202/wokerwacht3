package com.workwatch.reporting

import com.workwatch.security.CryptoUtils

class WorkerLeakSender(
    val cryptoUtils: CryptoUtils,
    private val firestoreService: FirestoreService? = null
) {
    /**
     * Report a leaked hash
     */
    suspend fun reportHashLeak(workerId: String, hashBase64: String): Boolean {
        return try {
            firestoreService?.saveLeak(workerId, hashBase64) ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Interface for Firestore integration
     */
    interface FirestoreService {
        suspend fun saveLeak(workerId: String, hash: String): Boolean
    }
}
