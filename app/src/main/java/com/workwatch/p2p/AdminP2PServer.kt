package com.workwatch.p2p

import android.util.Log
import com.workwatch.data.HashLeakDao
import com.workwatch.entities.HashLeak
import com.workwatch.security.CryptoUtils

class AdminP2PServer(
    private val leakDao: HashLeakDao,
    private val cryptoUtils: CryptoUtils
) {
    companion object {
        private const val TAG = "AdminP2PServer"
        private const val DEFAULT_PORT = 8080
    }

    private var isRunning = false
    private var port = DEFAULT_PORT

    /**
     * Start P2P server
     */
    suspend fun startServer(port: Int = DEFAULT_PORT): Boolean {
        return try {
            this.port = port
            isRunning = true
            Log.d(TAG, "P2P server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            false
        }
    }

    /**
     * Stop P2P server
     */
    fun stopServer() {
        isRunning = false
        Log.d(TAG, "P2P server stopped")
    }

    /**
     * Store leaked hash from worker
     */
    suspend fun storeLeakedHash(workerId: String, hashBase64: String, nonce: String): Boolean {
        return try {
            val leak = HashLeak(
                workerId = workerId,
                hashBase64 = hashBase64,
                timestamp = System.currentTimeMillis(),
                nonce = nonce
            )
            leakDao.insert(leak)
            Log.d(TAG, "Stored leaked hash for worker $workerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store leaked hash", e)
            false
        }
    }

    /**
     * Get leaked hashes for a worker
     */
    suspend fun getWorkerLeaks(workerId: String): List<HashLeak> {
        return emptyList() // TODO: Add query method to HashLeakDao
    }

    /**
     * Verify server is running
     */
    fun isServerRunning(): Boolean = isRunning

    /**
     * Get current port
     */
    fun getPort(): Int = port
}
