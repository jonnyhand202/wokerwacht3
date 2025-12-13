package com.workwatch.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manual Share Manager
 * Android native share ile manuel paylaşım
 * API yok, izin yok, para yok!
 */
@Singleton
class ManualShareManager @Inject constructor() {

    companion object {
        private const val TAG = "ManualShare"
        private const val AUTHORITY_SUFFIX = ".fileprovider"
    }

    /**
     * Tek dosya paylaş (Günlük rapor)
     */
    fun shareSingleFile(
        context: Context,
        file: File,
        title: String = "Raporu Paylaş",
        mimeType: String = "application/*"
    ) {
        try {
            val uri = getFileUri(context, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
                putExtra(Intent.EXTRA_TEXT, "WorkWatch Raporu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            android.util.Log.d(TAG, "Share dialog opened for: ${file.name}")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to share file", e)
            showError(context, "Paylaşım başarısız: ${e.message}")
        }
    }

    /**
     * Çoklu dosya paylaş (Aylık rapor)
     */
    fun shareMultipleFiles(
        context: Context,
        files: List<File>,
        title: String = "Raporları Paylaş"
    ) {
        try {
            if (files.isEmpty()) {
                showError(context, "Paylaşılacak dosya yok")
                return
            }

            val uris = files.map { getFileUri(context, it) }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_SUBJECT, "WorkWatch Raporları")
                putExtra(Intent.EXTRA_TEXT, "${files.size} dosya")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            android.util.Log.d(TAG, "Share dialog opened for ${files.size} files")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to share files", e)
            showError(context, "Paylaşım başarısız: ${e.message}")
        }
    }

    /**
     * ZIP dosyasını paylaş (Aylık export)
     */
    fun shareZipFile(
        context: Context,
        zipFile: File,
        title: String = "Aylık Rapor"
    ) {
        shareSingleFile(
            context = context,
            file = zipFile,
            title = title,
            mimeType = "application/zip"
        )
    }

    /**
     * Sadece WhatsApp'a paylaş
     */
    fun shareToWhatsApp(context: Context, file: File) {
        try {
            val uri = getFileUri(context, file)

            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "WorkWatch Raporu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(whatsappIntent)
            android.util.Log.d(TAG, "WhatsApp opened")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "WhatsApp not found or failed", e)
            // WhatsApp yoksa normal share aç
            shareSingleFile(context, file, "WhatsApp Bulunamadı")
        }
    }

    /**
     * Sadece Telegram'a paylaş
     */
    fun shareToTelegram(context: Context, file: File) {
        try {
            val uri = getFileUri(context, file)

            val telegramIntent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                setPackage("org.telegram.messenger")
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "WorkWatch Raporu")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(telegramIntent)
            android.util.Log.d(TAG, "Telegram opened")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Telegram not found or failed", e)
            // Telegram yoksa normal share aç
            shareSingleFile(context, file, "Telegram Bulunamadı")
        }
    }

    /**
     * Gmail ile gönder
     */
    fun shareViaEmail(
        context: Context,
        file: File,
        recipientEmail: String? = null,
        subject: String = "WorkWatch Raporu"
    ) {
        try {
            val uri = getFileUri(context, file)

            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, "Ekteki dosyada mesai raporumu bulabilirsiniz.")

                recipientEmail?.let {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(it))
                }

                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(emailIntent, "Email ile gönder")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            android.util.Log.d(TAG, "Email composer opened")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to open email", e)
            showError(context, "Email uygulaması bulunamadı")
        }
    }

    /**
     * Google Drive'a yükle (Google Drive app'i ile)
     */
    fun uploadToDrive(context: Context, file: File) {
        try {
            val uri = getFileUri(context, file)

            // Drive app var mı kontrol et
            val driveIntent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file)
                setPackage("com.google.android.apps.docs")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(driveIntent)
            android.util.Log.d(TAG, "Drive opened")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Drive not found or failed", e)
            // Drive yoksa normal share aç
            shareSingleFile(context, file, "Drive Bulunamadı")
        }
    }

    /**
     * Notification ile paylaş butonu
     */
    fun createShareNotification(
        context: Context,
        file: File,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager

        val channelId = "share_reports"

        // Android O+ için notification channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Rapor Paylaşımı",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Share intent
        val uri = getFileUri(context, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = getMimeType(file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            0,
            Intent.createChooser(shareIntent, "Raporu Paylaş"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * FileProvider URI al
     */
    private fun getFileUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}$AUTHORITY_SUFFIX"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * MIME type belirle
     */
    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "json" -> "application/json"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "enc" -> "application/octet-stream"
            else -> "*/*"
        }
    }

    /**
     * Hata mesajı göster
     */
    private fun showError(context: Context, message: String) {
        android.widget.Toast.makeText(
            context,
            message,
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}
