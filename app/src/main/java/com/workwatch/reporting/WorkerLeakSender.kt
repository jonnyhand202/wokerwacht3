package com.workwatch.reporting

import android.util.Log
import com.workwatch.security.CryptoUtils
import com.workwatch.entities.HashLeak
import com.workwatch.firebase.FirestoreServiceImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class WorkerLeakSender(
    private val cryptoUtils: CryptoUtils,
    private val firestoreService: FirestoreServiceImpl? = null
) {

    companion object {
        private const val TAG = "WorkerLeakSender"
    }

    interface FirestoreService {
        suspend fun saveLeak(workerId: String, hash: String): Boolean
    }

    suspend fun sendHashLeak(leak: HashLeak): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Send hash leak to Firestore if available
                if (firestoreService != null) {
                    val success = firestoreService.saveHashLeak(leak)
                    if (success) {
                        Log.d(TAG, "Hash leak sent to Firestore for worker: ${leak.workerId}")
                    } else {
                        Log.w(TAG, "Failed to send hash leak to Firestore for worker: ${leak.workerId}")
                    }
                    success
                } else {
                    Log.w(TAG, "Firestore service not available for sending hash leak")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending hash leak: ${e.message}", e)
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
