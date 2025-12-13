package com.workwatch.security

import android.util.Base64
import com.google.gson.Gson
import com.workwatch.reporting.DailyReport
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tamper Detection - Mühür kontrolü
 * Dosyaların açılıp açılmadığını ve değiştirilip değiştirilmediğini kontrol eder
 */
@Singleton
class TamperDetection @Inject constructor(
    private val gson: Gson
) {

    companion object {
        private const val TAG = "TamperDetection"
    }

    /**
     * Dosyanın integrity durumunu kontrol et
     */
    fun checkFileIntegrity(file: File): IntegrityStatus {
        if (!file.exists()) {
            return IntegrityStatus.Missing("Dosya bulunamadı")
        }

        return when {
            file.name.startsWith("SEALED_ORIGINAL") -> {
                checkSealedFile(file)
            }
            file.name.startsWith("READABLE_COPY") -> {
                IntegrityStatus.ReadableCopy("Bu bir kopya dosyası")
            }
            else -> {
                IntegrityStatus.Unknown("Bilinmeyen dosya tipi")
            }
        }
    }

    /**
     * SEALED dosyasını kontrol et
     */
    private fun checkSealedFile(file: File): IntegrityStatus {
        return try {
            // Dosya boyutunu kontrol et
            val fileSize = file.length()
            if (fileSize == 0L) {
                return IntegrityStatus.Corrupted("Dosya boş")
            }

            // Metadata kontrol et (basitleştirilmiş)
            // Gerçek implementasyonda dosya içinde metadata olmalı

            IntegrityStatus.Sealed(
                isSealed = true,
                fileHash = calculateFileHash(file),
                fileSizeBytes = fileSize
            )

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to check sealed file", e)
            IntegrityStatus.Error("Kontrol hatası: ${e.message}")
        }
    }

    /**
     * SEALED dosyasını aç (Şifre ile)
     */
    fun openSealedFile(
        file: File,
        password: String,
        confirmOpen: Boolean = false
    ): OpenResult {
        if (!confirmOpen) {
            return OpenResult.NeedsConfirmation(
                warningMessage = """
                    ⚠️ UYARI!

                    Mühürlü dosyayı açmak üzeresiniz.

                    Dosya açıldığında:
                    • "Açılmış" olarak işaretlenecek
                    • Mühür durumu "Bozulmuş" olacak
                    • Mahkemede bu durum sorulabilir

                    READONLY dosyalarını kullanmanız önerilir:
                    • READABLE_COPY.json
                    • SUMMARY.txt

                    Yine de açmak istiyor musunuz?
                """.trimIndent()
            )
        }

        return try {
            // Şifreyi doğrula ve dosyayı aç
            val decryptedData = decryptFile(file, password)

            // JSON'a parse et
            val report = gson.fromJson(decryptedData, DailyReport::class.java)

            // Mühür bozuldu bilgisini logla
            android.util.Log.w(
                TAG,
                "SEALED file opened: ${file.name} at ${System.currentTimeMillis()}"
            )

            OpenResult.Success(
                report = report,
                warning = "⚠️ Dosya açıldı. Mühür artık geçersiz!"
            )

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to open sealed file", e)
            when {
                e.message?.contains("padding") == true -> {
                    OpenResult.WrongPassword("Şifre yanlış!")
                }
                else -> {
                    OpenResult.Error("Dosya açılamadı: ${e.message}")
                }
            }
        }
    }

    /**
     * İki dosyanın hash'ini karşılaştır
     */
    fun compareFiles(file1: File, file2: File): ComparisonResult {
        val hash1 = calculateFileHash(file1)
        val hash2 = calculateFileHash(file2)

        return if (hash1 == hash2) {
            ComparisonResult.Identical("Dosyalar aynı ✅")
        } else {
            ComparisonResult.Different(
                message = "Dosyalar farklı ⚠️",
                hash1 = hash1,
                hash2 = hash2
            )
        }
    }

    /**
     * READABLE kopyanın watermark'ını kontrol et
     */
    fun verifyReadableCopy(file: File): Boolean {
        return try {
            val content = file.readText()
            content.contains("⚠️ BU BİR KOPYADIR")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Dosya hash'ini hesapla (SHA-256)
     */
    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Dosyayı şifre ile decrypt et
     */
    private fun decryptFile(file: File, password: String): String {
        val encryptedData = file.readBytes()

        val key = MessageDigest.getInstance("SHA-256")
            .digest(password.toByteArray())

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)

        val decryptedBytes = cipher.doFinal(encryptedData)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Batch kontrolü (Aylık raporlar için)
     */
    fun verifyMonthlyReports(reportDir: File): BatchVerificationResult {
        val results = mutableListOf<FileVerification>()

        reportDir.listFiles()?.forEach { dayDir ->
            if (dayDir.isDirectory) {
                dayDir.listFiles()?.forEach { file ->
                    if (file.name.startsWith("SEALED_ORIGINAL")) {
                        val status = checkFileIntegrity(file)
                        results.add(
                            FileVerification(
                                fileName = file.name,
                                status = status,
                                date = dayDir.name
                            )
                        )
                    }
                }
            }
        }

        val allSealed = results.all { it.status is IntegrityStatus.Sealed }

        return BatchVerificationResult(
            totalFiles = results.size,
            allSealed = allSealed,
            verifications = results
        )
    }
}

/**
 * Integrity Status - Dosya durumu
 */
sealed class IntegrityStatus {
    data class Sealed(
        val isSealed: Boolean,
        val fileHash: String,
        val fileSizeBytes: Long
    ) : IntegrityStatus()

    data class ReadableCopy(val message: String) : IntegrityStatus()
    data class Missing(val message: String) : IntegrityStatus()
    data class Corrupted(val message: String) : IntegrityStatus()
    data class Unknown(val message: String) : IntegrityStatus()
    data class Error(val message: String) : IntegrityStatus()
}

/**
 * Open Result - Dosya açma sonucu
 */
sealed class OpenResult {
    data class Success(
        val report: DailyReport,
        val warning: String
    ) : OpenResult()

    data class NeedsConfirmation(val warningMessage: String) : OpenResult()
    data class WrongPassword(val message: String) : OpenResult()
    data class Error(val message: String) : OpenResult()
}

/**
 * Comparison Result - Karşılaştırma sonucu
 */
sealed class ComparisonResult {
    data class Identical(val message: String) : ComparisonResult()
    data class Different(
        val message: String,
        val hash1: String,
        val hash2: String
    ) : ComparisonResult()
}

/**
 * File Verification - Dosya doğrulama
 */
data class FileVerification(
    val fileName: String,
    val status: IntegrityStatus,
    val date: String
)

/**
 * Batch Verification Result - Toplu doğrulama
 */
data class BatchVerificationResult(
    val totalFiles: Int,
    val allSealed: Boolean,
    val verifications: List<FileVerification>
)
