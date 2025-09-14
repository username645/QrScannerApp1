package com.example.qrscannerapp

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

// ... (data class UpdateInfo и sealed interface UpdateState остаются без изменений) ...
data class UpdateInfo(
    @SerializedName("latestVersionCode") val latestVersionCode: Int,
    @SerializedName("latestVersionName") val latestVersionName: String,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("releaseNotes") val releaseNotes: String
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState
    object UpdateNotAvailable : UpdateState
    data class Downloading(val progress: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(private val context: Context) {

    private val gson = Gson()
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    private var downloadId: Long = -1L

    companion object {
        private const val UPDATE_URL = "https://example.com/update.json" // ЗАГЛУШКА
        private const val UPDATE_FILE_NAME = "QrScannerApp-update.apk"
    }

    suspend fun checkForUpdates() {
        if (_updateState.value is UpdateState.Checking) return
        _updateState.value = UpdateState.Checking

        withContext(Dispatchers.IO) {
            try {
                delay(1500)
                val currentVersionCode = getCurrentVersionCode()
                val fakeJson = """
                    {
                      "latestVersionCode": ${currentVersionCode + 1},
                      "latestVersionName": "2.0.0-beta",
                      "apkUrl": "https://example.com/fake.apk",
                      "releaseNotes": "- Это тестовое обновление.\n- Оно демонстрирует работу UI."
                    }
                """.trimIndent()
                val updateInfo = gson.fromJson(fakeJson, UpdateInfo::class.java)

                if (updateInfo.latestVersionCode > currentVersionCode) {
                    _updateState.value = UpdateState.UpdateAvailable(updateInfo)
                } else {
                    _updateState.value = UpdateState.UpdateNotAvailable
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Check failed", e)
                _updateState.value = UpdateState.Error("Ошибка проверки обновлений: ${e.message}")
            }
        }
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo, scope: CoroutineScope) {
        if (!hasInstallPermission()) {
            requestInstallPermission()
            return
        }

        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_FILE_NAME)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(updateInfo.apkUrl))
            .setTitle("Загрузка обновления QrScannerApp")
            .setDescription("Версия ${updateInfo.latestVersionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, UPDATE_FILE_NAME)

        downloadId = downloadManager.enqueue(request)
        _updateState.value = UpdateState.Downloading(0)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val downloadedFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_FILE_NAME)
                    if (downloadedFile.exists()) {
                        // --- ИСПРАВЛЕНИЕ: Используем FileProvider для создания безопасного Uri ---
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", downloadedFile)
                        installApk(uri)
                    } else {
                        _updateState.value = UpdateState.Error("Не удалось найти файл после загрузки.")
                    }
                    context.unregisterReceiver(this)
                    resetState()
                }
            }
        }

        // --- ИСПРАВЛЕНИЕ: Используем безопасный ContextCompat для регистрации ресивера ---
        ContextCompat.registerReceiver(
            context,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        scope.launch {
            monitorDownloadProgress()
        }
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    private suspend fun monitorDownloadProgress() {
        var isDownloading = true
        while (isDownloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (totalBytes > 0) {
                                val downloadedBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    _updateState.value = UpdateState.Downloading(progress)
                                }
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                            isDownloading = false
                            if(status == DownloadManager.STATUS_FAILED) {
                                withContext(Dispatchers.Main) {
                                    _updateState.value = UpdateState.Error("Ошибка загрузки файла.")
                                }
                            }
                        }
                    }
                } else {
                    isDownloading = false
                }
            }
            if (isDownloading) delay(500)
        }
    }

    private fun installApk(fileUri: Uri) {
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }

    private fun hasInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else { true }
    }

    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionCode
        } catch (e: Exception) { -1 }
    }
}