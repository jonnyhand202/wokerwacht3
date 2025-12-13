package com.workwatch.sharing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telegram Bot Service
 * Otomatik rapor gönderimi için Telegram Bot API
 * TAMAMEN ÜCRETSİZ! ✅
 */
@Singleton
class TelegramBotService @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "TelegramBot"
        private const val BASE_URL = "https://api.telegram.org"

        // SharedPreferences keys
        private const val PREFS_NAME = "telegram_bot_prefs"
        private const val KEY_TOKEN = "bot_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_ENABLED = "auto_send_enabled"
    }

    /**
     * Bot ayarlarını kaydet
     */
    fun saveSettings(
        context: Context,
        token: String,
        chatId: String,
        enabled: Boolean
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_CHAT_ID, chatId)
            .putBoolean(KEY_ENABLED, enabled)
            .apply()

        android.util.Log.d(TAG, "Settings saved - Enabled: $enabled")
    }

    /**
     * Bot ayarlarını oku
     */
    fun getSettings(context: Context): TelegramSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TelegramSettings(
            token = prefs.getString(KEY_TOKEN, "") ?: "",
            chatId = prefs.getString(KEY_CHAT_ID, "") ?: "",
            enabled = prefs.getBoolean(KEY_ENABLED, false)
        )
    }

    /**
     * Bot aktif mi kontrol et
     */
    fun isEnabled(context: Context): Boolean {
        val settings = getSettings(context)
        return settings.enabled &&
               settings.token.isNotBlank() &&
               settings.chatId.isNotBlank()
    }

    /**
     * Dosya gönder
     */
    suspend fun sendDocument(
        context: Context,
        file: File,
        caption: String? = null
    ): TelegramResult = withContext(Dispatchers.IO) {
        try {
            val settings = getSettings(context)

            if (!settings.isValid()) {
                return@withContext TelegramResult.NotConfigured(
                    "Telegram bot ayarları yapılmamış"
                )
            }

            val url = "$BASE_URL/bot${settings.token}/sendDocument"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", settings.chatId)
                .addFormDataPart(
                    "document",
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaType())
                )
                .apply {
                    caption?.let {
                        addFormDataPart("caption", it)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                android.util.Log.d(TAG, "Document sent: ${file.name}")
                TelegramResult.Success("Dosya gönderildi ✅")
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                android.util.Log.e(TAG, "Failed to send: $errorBody")
                TelegramResult.Error("Gönderim başarısız: ${response.code}")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Send document failed", e)
            TelegramResult.Error("Hata: ${e.message}")
        }
    }

    /**
     * Metin mesajı gönder
     */
    suspend fun sendMessage(
        context: Context,
        message: String
    ): TelegramResult = withContext(Dispatchers.IO) {
        try {
            val settings = getSettings(context)

            if (!settings.isValid()) {
                return@withContext TelegramResult.NotConfigured(
                    "Telegram bot ayarları yapılmamış"
                )
            }

            val url = "$BASE_URL/bot${settings.token}/sendMessage?" +
                      "chat_id=${settings.chatId}&text=$message"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                android.util.Log.d(TAG, "Message sent")
                TelegramResult.Success("Mesaj gönderildi ✅")
            } else {
                TelegramResult.Error("Gönderim başarısız: ${response.code}")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Send message failed", e)
            TelegramResult.Error("Hata: ${e.message}")
        }
    }

    /**
     * Bot bağlantısını test et
     */
    suspend fun testConnection(
        token: String
    ): TelegramResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/bot$token/getMe"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                android.util.Log.d(TAG, "Bot info: $body")
                TelegramResult.Success("Bot bağlantısı başarılı ✅")
            } else {
                TelegramResult.Error("Geçersiz token")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Test connection failed", e)
            TelegramResult.Error("Bağlantı hatası: ${e.message}")
        }
    }

    /**
     * Chat ID al (kullanıcı bota mesaj attıysa)
     */
    suspend fun getChatId(
        token: String
    ): TelegramResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/bot$token/getUpdates"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""

                // Basit parsing (gerçek uygulamada JSON parser kullan)
                val chatIdRegex = "\"chat\":\\{\"id\":(\\d+)".toRegex()
                val match = chatIdRegex.find(body)

                if (match != null) {
                    val chatId = match.groupValues[1]
                    android.util.Log.d(TAG, "Chat ID found: $chatId")
                    TelegramResult.ChatIdFound(chatId)
                } else {
                    TelegramResult.Error(
                        "Chat ID bulunamadı. Önce bota mesaj atın!"
                    )
                }
            } else {
                TelegramResult.Error("Geçersiz token")
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Get chat ID failed", e)
            TelegramResult.Error("Hata: ${e.message}")
        }
    }

    /**
     * Günlük rapor gönder (Otomatik)
     */
    suspend fun sendDailyReport(
        context: Context,
        reportFile: File,
        date: String
    ): TelegramResult {
        return sendDocument(
            context = context,
            file = reportFile,
            caption = "📊 Günlük Rapor - $date\n\n✅ WorkWatch tarafından otomatik gönderildi"
        )
    }

    /**
     * Aylık rapor gönder (Otomatik)
     */
    suspend fun sendMonthlyReport(
        context: Context,
        zipFile: File,
        month: String,
        totalDays: Int,
        totalHours: Double
    ): TelegramResult {
        val caption = buildString {
            appendLine("📅 Aylık Rapor - $month")
            appendLine()
            appendLine("Toplam Gün: $totalDays")
            appendLine("Toplam Saat: ${String.format("%.2f", totalHours)}")
            appendLine()
            appendLine("✅ WorkWatch tarafından otomatik gönderildi")
        }

        return sendDocument(
            context = context,
            file = zipFile,
            caption = caption
        )
    }
}

/**
 * Telegram Settings
 */
data class TelegramSettings(
    val token: String,
    val chatId: String,
    val enabled: Boolean
) {
    fun isValid(): Boolean {
        return token.isNotBlank() && chatId.isNotBlank()
    }
}

/**
 * Telegram Result
 */
sealed class TelegramResult {
    data class Success(val message: String) : TelegramResult()
    data class Error(val message: String) : TelegramResult()
    data class NotConfigured(val message: String) : TelegramResult()
    data class ChatIdFound(val chatId: String) : TelegramResult()
}
