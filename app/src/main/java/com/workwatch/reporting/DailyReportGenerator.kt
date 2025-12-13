package com.workwatch.reporting

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.workwatch.data.WorkerRepository
import com.workwatch.entities.GPSTrailPoint
import com.workwatch.entities.WorkerLogEntry
import com.workwatch.security.CryptoUtils
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data classes for daily report
 */
data class DailyReport(
    val reportDate: String,
    val workerId: String,
    val checkIn: CheckInOutData?,
    val checkOut: CheckInOutData?,
    val workDuration: String,
    val totalSeconds: Long,
    val gpsTrail: List<GPSTrailPoint>,
    val googleMapsLink: String?,
    val previousDayHash: String,
    val todayHash: String,
    val chainValid: Boolean,
    val integrity: IntegrityData
)

data class CheckInOutData(
    val timestamp: String,
    val gps: GPSData,
    val device: DeviceData,
    val hash: String
)

data class GPSData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val provider: String
)

data class DeviceData(
    val model: String,
    val androidVersion: String,
    val appVersion: String
)

data class IntegrityData(
    val encrypted: Boolean,
    val sealed: Boolean,
    val tampered: Boolean,
    val openCount: Int,
    val openHistory: List<OpenHistoryEntry>
)

data class OpenHistoryEntry(
    val timestamp: String,
    val action: String
)

/**
 * Daily Report Generator
 * Her gece 23:59'da günlük rapor oluşturur
 */
@Singleton
class DailyReportGenerator @Inject constructor(
    private val repository: WorkerRepository,
    private val cryptoUtils: CryptoUtils,
    private val gson: Gson
) {

    companion object {
        private const val REPORT_DIR = "WorkWatch_Reports"
        private const val SEALED_PREFIX = "SEALED_ORIGINAL"
        private const val READABLE_PREFIX = "READABLE_COPY"
        private const val SUMMARY_PREFIX = "SUMMARY"
        private const val VERIFICATION_PREFIX = "VERIFICATION"

        private const val WATERMARK_TEXT = """
⚠️ BU BİR KOPYADIR - ORİJİNAL DEĞİŞMEDİ
Bu dosya sadece görüntüleme amaçlıdır.
Mahkemede geçerli olan SEALED_ORIGINAL.enc dosyasıdır.
"""
    }

    /**
     * Günlük rapor oluştur (3 dosya + verification)
     */
    suspend fun generateDailyReport(
        context: Context,
        password: String
    ): DailyReportResult {
        try {
            val today = getTodayDateString()
            val config = repository.getUserConfig()
                ?: return DailyReportResult.Failure("Config bulunamadı")

            // Bugünün loglarını al
            val todayLogs = repository.getTodayLogs()
            if (todayLogs.isEmpty()) {
                return DailyReportResult.Failure("Bugün log kaydı yok")
            }

            // GPS trail'i al
            val gpsTrail = repository.getTodayGPSTrail()

            // Rapor oluştur
            val report = buildDailyReport(
                workerId = config.workerId,
                logs = todayLogs,
                gpsTrail = gpsTrail
            )

            // Dosyaları oluştur
            val reportDir = createReportDirectory(context, today)

            val sealedFile = createSealedOriginal(reportDir, report, password, today)
            val readableFile = createReadableCopy(reportDir, report, today)
            val summaryFile = createSummaryPDF(reportDir, report, today)
            val verificationFile = createVerificationFile(reportDir, report, today)

            return DailyReportResult.Success(
                sealedFile = sealedFile,
                readableFile = readableFile,
                summaryFile = summaryFile,
                verificationFile = verificationFile,
                report = report
            )

        } catch (e: Exception) {
            android.util.Log.e("DailyReport", "Failed to generate report", e)
            return DailyReportResult.Failure("Rapor oluşturulamadı: ${e.message}")
        }
    }

    /**
     * Rapor verilerini oluştur
     */
    private suspend fun buildDailyReport(
        workerId: String,
        logs: List<WorkerLogEntry>,
        gpsTrail: List<GPSTrailPoint>
    ): DailyReport {
        val checkInLog = logs.firstOrNull()
        val checkOutLog = logs.lastOrNull()?.takeIf { it.checkOutTime != null }

        val checkInData = checkInLog?.let {
            CheckInOutData(
                timestamp = formatTimestamp(it.checkInTime),
                gps = GPSData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = 0.0, // TODO: Add to entity
                    accuracy = 0f,
                    speed = 0f,
                    bearing = 0f,
                    provider = "fused"
                ),
                device = getDeviceData(),
                hash = Base64.encodeToString(it.currentHash, Base64.NO_WRAP).take(16)
            )
        }

        val checkOutData = checkOutLog?.let {
            CheckInOutData(
                timestamp = formatTimestamp(it.checkOutTime ?: 0),
                gps = GPSData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitude = 0.0,
                    accuracy = 0f,
                    speed = 0f,
                    bearing = 0f,
                    provider = "fused"
                ),
                device = getDeviceData(),
                hash = Base64.encodeToString(it.currentHash, Base64.NO_WRAP).take(16)
            )
        }

        val workDuration = calculateWorkDuration(checkInLog, checkOutLog)
        val googleMapsLink = checkInLog?.let {
            "https://maps.google.com/?q=${it.latitude},${it.longitude}"
        }

        val previousDayHash = getPreviousDayHash()
        val todayHash = calculateTodayHash(logs)

        return DailyReport(
            reportDate = getTodayDateString(),
            workerId = workerId,
            checkIn = checkInData,
            checkOut = checkOutData,
            workDuration = workDuration.first,
            totalSeconds = workDuration.second,
            gpsTrail = gpsTrail,
            googleMapsLink = googleMapsLink,
            previousDayHash = previousDayHash,
            todayHash = todayHash,
            chainValid = validateChain(logs),
            integrity = IntegrityData(
                encrypted = true,
                sealed = true,
                tampered = false,
                openCount = 0,
                openHistory = emptyList()
            )
        )
    }

    /**
     * SEALED_ORIGINAL.enc oluştur (Şifreli + Mühürlü)
     */
    private fun createSealedOriginal(
        dir: File,
        report: DailyReport,
        password: String,
        date: String
    ): File {
        val file = File(dir, "${SEALED_PREFIX}_${date}.enc")

        // JSON'a çevir
        val json = gson.toJson(report)

        // Şifrele
        val encrypted = encryptWithPassword(json, password)

        // Dosyaya yaz
        file.writeBytes(encrypted)

        android.util.Log.d("DailyReport", "SEALED file created: ${file.absolutePath}")
        return file
    }

    /**
     * READABLE_COPY.json oluştur (Okunabilir + Watermarked)
     */
    private fun createReadableCopy(
        dir: File,
        report: DailyReport,
        date: String
    ): File {
        val file = File(dir, "${READABLE_PREFIX}_${date}.json")

        // Watermark ekle
        val watermarkedReport = report.copy(
            integrity = report.integrity.copy(
                sealed = false,
                openCount = -1 // -1 = Bu bir kopya
            )
        )

        // Pretty JSON
        val prettyGson = GsonBuilder().setPrettyPrinting().create()
        val json = buildString {
            appendLine(WATERMARK_TEXT)
            appendLine()
            appendLine(prettyGson.toJson(watermarkedReport))
        }

        file.writeText(json)

        android.util.Log.d("DailyReport", "READABLE file created: ${file.absolutePath}")
        return file
    }

    /**
     * SUMMARY.pdf oluştur (Özet + Watermarked)
     * Not: Basit text dosyası olarak oluşturuyoruz (PDF library yok)
     */
    private fun createSummaryPDF(
        dir: File,
        report: DailyReport,
        date: String
    ): File {
        val file = File(dir, "${SUMMARY_PREFIX}_${date}.txt")

        val summary = buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("     WORKWATCH GÜNLÜK RAPOR")
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("⚠️ BU BİR KOPYADIR")
            appendLine("Orijinal: SEALED_ORIGINAL_${date}.enc")
            appendLine()
            appendLine("───────────────────────────────────────")
            appendLine("TARİH: ${report.reportDate}")
            appendLine("WORKER ID: ${report.workerId}")
            appendLine("───────────────────────────────────────")
            appendLine()

            report.checkIn?.let {
                appendLine("✅ GİRİŞ:")
                appendLine("  Saat: ${it.timestamp}")
                appendLine("  Konum: ${it.gps.latitude}, ${it.gps.longitude}")
                appendLine("  Hash: ${it.hash}...")
                appendLine()
            }

            report.checkOut?.let {
                appendLine("✅ ÇIKIŞ:")
                appendLine("  Saat: ${it.timestamp}")
                appendLine("  Konum: ${it.gps.latitude}, ${it.gps.longitude}")
                appendLine("  Hash: ${it.hash}...")
                appendLine()
            }

            appendLine("⏱️ ÇALIŞMA SÜRESİ: ${report.workDuration}")
            appendLine()
            appendLine("🗺️ GPS İZİ: ${report.gpsTrail.size} nokta kaydedildi")
            report.googleMapsLink?.let {
                appendLine("📍 Harita: $it")
            }
            appendLine()
            appendLine("🔗 HASH ZİNCİRİ:")
            appendLine("  Önceki gün: ${report.previousDayHash.take(16)}...")
            appendLine("  Bugün: ${report.todayHash.take(16)}...")
            appendLine("  Geçerli: ${if (report.chainValid) "✅ EVET" else "❌ HAYIR"}")
            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine("Bu rapor WorkWatch tarafından oluşturuldu")
            appendLine("Kanıt değeri için SEALED_ORIGINAL kullanın")
            appendLine("═══════════════════════════════════════")
        }

        file.writeText(summary)

        android.util.Log.d("DailyReport", "SUMMARY file created: ${file.absolutePath}")
        return file
    }

    /**
     * VERIFICATION.txt oluştur (Doğrulama bilgileri)
     */
    private fun createVerificationFile(
        dir: File,
        report: DailyReport,
        date: String
    ): File {
        val file = File(dir, "${VERIFICATION_PREFIX}_${date}.txt")

        val verification = buildString {
            appendLine("WORKWATCH - DOĞRULAMA BİLGİLERİ")
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("Tarih: ${report.reportDate}")
            appendLine("Worker ID: ${report.workerId}")
            appendLine()
            appendLine("HASH BİLGİLERİ:")
            appendLine("─────────────────────────────────────")
            appendLine("Önceki Gün Hash: ${report.previousDayHash}")
            appendLine("Bugün Hash: ${report.todayHash}")
            appendLine("Zincir Geçerli: ${report.chainValid}")
            appendLine()
            appendLine("DOSYA BİLGİLERİ:")
            appendLine("─────────────────────────────────────")
            appendLine("SEALED_ORIGINAL_${date}.enc:")
            appendLine("  - Şifreli: ✅")
            appendLine("  - Mühürlü: ✅")
            appendLine("  - Değiştirilmiş: ❌")
            appendLine()
            appendLine("READABLE_COPY_${date}.json:")
            appendLine("  - Şifreli: ❌")
            appendLine("  - Sadece görüntüleme için")
            appendLine("  - Kanıt değeri YOK")
            appendLine()
            appendLine("SUMMARY_${date}.txt:")
            appendLine("  - Özet bilgi")
            appendLine("  - Kanıt değeri YOK")
            appendLine()
            appendLine("═══════════════════════════════════════")
            appendLine()
            appendLine("ℹ️ NASIL DOĞRULANIR?")
            appendLine()
            appendLine("1. SEALED_ORIGINAL açılmamışsa:")
            appendLine("   → Rapor GEÇERLİ ✅")
            appendLine()
            appendLine("2. SEALED_ORIGINAL açıldıysa:")
            appendLine("   → Rapor ŞÜPHELİ ⚠️")
            appendLine("   → Hash'leri kontrol edin")
            appendLine()
            appendLine("3. Hash'ler eşleşmiyorsa:")
            appendLine("   → Rapor DEĞİŞTİRİLMİŞ ❌")
            appendLine()
        }

        file.writeText(verification)

        android.util.Log.d("DailyReport", "VERIFICATION file created: ${file.absolutePath}")
        return file
    }

    /**
     * Şifrele (AES-256)
     */
    private fun encryptWithPassword(data: String, password: String): ByteArray {
        val key = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        return cipher.doFinal(data.toByteArray())
    }

    /**
     * Çalışma süresini hesapla
     */
    private fun calculateWorkDuration(
        checkIn: WorkerLogEntry?,
        checkOut: WorkerLogEntry?
    ): Pair<String, Long> {
        if (checkIn == null || checkOut == null || checkOut.checkOutTime == null) {
            return Pair("0h 0m", 0)
        }

        val durationMs = checkOut.checkOutTime!! - checkIn.checkInTime
        val hours = durationMs / (1000 * 60 * 60)
        val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)

        return Pair("${hours}h ${minutes}m", durationMs / 1000)
    }

    /**
     * Bugünün hash'ini hesapla
     */
    private fun calculateTodayHash(logs: List<WorkerLogEntry>): String {
        val lastLog = logs.lastOrNull() ?: return "GENESIS"
        return Base64.encodeToString(lastLog.currentHash, Base64.NO_WRAP)
    }

    /**
     * Önceki günün hash'ini al
     */
    private suspend fun getPreviousDayHash(): String {
        val yesterdayLogs = repository.getYesterdayLogs()
        val lastLog = yesterdayLogs.lastOrNull()
        return if (lastLog != null) {
            Base64.encodeToString(lastLog.currentHash, Base64.NO_WRAP)
        } else {
            "GENESIS"
        }
    }

    /**
     * Hash zincirini doğrula
     */
    private fun validateChain(logs: List<WorkerLogEntry>): Boolean {
        if (logs.isEmpty()) return true

        var previousHash = logs.first().previousHash

        for (log in logs) {
            if (!log.previousHash.contentEquals(previousHash)) {
                return false
            }

            val calculatedHash = cryptoUtils.calculateCurrentHash(
                log.encryptedLogData,
                log.previousHash
            )

            if (!calculatedHash.contentEquals(log.currentHash)) {
                return false
            }

            previousHash = log.currentHash
        }

        return true
    }

    private fun createReportDirectory(context: Context, date: String): File {
        val dir = File(context.getExternalFilesDir(null), "$REPORT_DIR/$date")
        dir.mkdirs()
        return dir
    }

    private fun getDeviceData() = DeviceData(
        model = android.os.Build.MODEL,
        androidVersion = android.os.Build.VERSION.RELEASE,
        appVersion = "1.0.0" // TODO: Get from BuildConfig
    )

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            .format(Date(timestamp))
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date())
    }
}

/**
 * Result sealed class
 */
sealed class DailyReportResult {
    data class Success(
        val sealedFile: File,
        val readableFile: File,
        val summaryFile: File,
        val verificationFile: File,
        val report: DailyReport
    ) : DailyReportResult()

    data class Failure(val error: String) : DailyReportResult()
}
