package net.dom53.inkita.core.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dom53.inkita.BuildConfig
import net.dom53.inkita.R
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.notification.AppNotificationManager
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Lightweight update checker used at app startup. */
object UpdateChecker {
    private const val UPDATES_URL = "https://dom-53.github.io/Inkita/updates.json"
    private const val NOTIFICATION_ID = 2001

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun checkForUpdate(context: Context) {
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val showingProgress =
                AppNotificationManager.showProgress(
                    id = NOTIFICATION_ID,
                    channel = AppNotificationManager.CHANNEL_GENERAL,
                    title = context.getString(R.string.update_checking_title),
                    text = context.getString(R.string.update_checking_text),
                    progress = null,
                    max = null,
                    ongoing = true,
                )
            val info =
                try {
                    findUpdate(pkg.versionName.orEmpty(), pkg.longVersionCode, BuildConfig.RELEASE_CHANNEL)
                } finally {
                    if (showingProgress) AppNotificationManager.cancel(NOTIFICATION_ID)
                }
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("UpdateChecker", "Update info=${info?.versionName ?: "none"}")
            }
            val pending =
                info?.let { update ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.url))
                    PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                }

            if (info == null || pending == null) {
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.d("UpdateChecker", "No update available")
                }
                return
            }
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d("UpdateChecker", "Update found version=${info.versionName}")
            }
            AppNotificationManager.showInfo(
                id = NOTIFICATION_ID,
                channel = AppNotificationManager.CHANNEL_GENERAL,
                title = context.getString(R.string.update_notification_title, info.versionName),
                text = context.getString(R.string.update_notification_text),
                autoCancel = true,
                contentIntent = pending,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoggingManager.w("UpdateChecker", "Startup update check failed", e)
        }
    }

    internal suspend fun findUpdate(
        currentName: String,
        currentCode: Long,
        channel: String,
        feedUrl: String = UPDATES_URL,
    ): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val url = URL(feedUrl)
            val conn =
                (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }

            val response =
                try {
                    if (conn.responseCode !in 200..299) throw IOException("Update feed HTTP ${conn.responseCode}")
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            val json = JSONObject(response)

            val remoteChannel = json.optString("channel").ifBlank { "stable" }
            val acceptChannel =
                when {
                    remoteChannel == channel -> true
                    channel == "preview" && remoteChannel == "stable" -> true
                    else -> false
                }
            if (!acceptChannel) return@withContext null

            val remoteVersionRaw = json.optString("version")
            val remoteCode = json.optLong("versionCode", 0)
            if (!isNewerRelease(currentName, currentCode, remoteVersionRaw, remoteCode)) return@withContext null
            val apkUrl =
                json.optString("url").takeIf { it.isNotBlank() }
                    ?: throw IOException("Update feed is missing the download URL")
            UpdateInfo(remoteVersionRaw, apkUrl)
        }

    internal data class UpdateInfo(
        val versionName: String,
        val url: String,
    )
}
