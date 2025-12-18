package com.workwatch.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.workwatch.entities.HashLeak
import com.workwatch.entities.WorkerLogEntry
import com.workwatch.reporting.WorkerLeakSender
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreServiceImpl(
    private val firestore: FirebaseFirestore
) : WorkerLeakSender.FirestoreService {

    companion object {
        private const val TAG = "FirestoreServiceImpl"
        private const val HASH_LEAKS_COLLECTION = "hash_leaks"
        private const val WORKER_LOGS_COLLECTION = "worker_logs"
        private const val WORKERS_COLLECTION = "workers"
    }

    override suspend fun saveLeak(workerId: String, hash: String): Boolean {
        return try {
            val leakData = mapOf(
                "workerId" to workerId,
                "hash" to hash,
                "timestamp" to System.currentTimeMillis(),
                "nonce" to UUID.randomUUID().toString()
            )

            firestore.collection(HASH_LEAKS_COLLECTION)
                .document(UUID.randomUUID().toString())
                .set(leakData)
                .await()

            Log.d(TAG, "Hash leak saved successfully for worker: $workerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving hash leak: ${e.message}", e)
            false
        }
    }

    suspend fun saveWorkerLog(logEntry: WorkerLogEntry, workerId: String): Boolean {
        return try {
            val logData = mapOf(
                "workerId" to workerId,
                "checkInTime" to logEntry.checkInTime,
                "checkOutTime" to logEntry.checkOutTime,
                "latitude" to logEntry.latitude,
                "longitude" to logEntry.longitude,
                "isSynced" to true,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection(WORKER_LOGS_COLLECTION)
                .document(UUID.randomUUID().toString())
                .set(logData)
                .await()

            Log.d(TAG, "Worker log saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving worker log: ${e.message}", e)
            false
        }
    }

    suspend fun saveHashLeak(leak: HashLeak): Boolean {
        return try {
            val leakData = mapOf(
                "workerId" to leak.workerId,
                "hash" to leak.hashBase64,
                "timestamp" to leak.timestamp,
                "nonce" to leak.nonce
            )

            firestore.collection(HASH_LEAKS_COLLECTION)
                .document(UUID.randomUUID().toString())
                .set(leakData)
                .await()

            Log.d(TAG, "Hash leak saved successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving hash leak: ${e.message}", e)
            false
        }
    }

    suspend fun getWorkerLogs(workerId: String): List<Map<String, Any>> {
        return try {
            firestore.collection(WORKER_LOGS_COLLECTION)
                .whereEqualTo("workerId", workerId)
                .get()
                .await()
                .documents
                .map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching worker logs: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getHashLeaks(workerId: String): List<Map<String, Any>> {
        return try {
            firestore.collection(HASH_LEAKS_COLLECTION)
                .whereEqualTo("workerId", workerId)
                .get()
                .await()
                .documents
                .map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching hash leaks: ${e.message}", e)
            emptyList()
        }
    }
}
