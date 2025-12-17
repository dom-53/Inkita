package net.dom53.inkita.core.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dom53.inkita.BuildConfig
import net.dom53.inkita.R
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.notification.AppNotificationManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Lightweight update checker used at app startup. */
object UpdateChecker {
    private const val UPDATES_URL = "https://dom-53.github.io/Inkita/updates.json"
    private const val NOTIFICATION_ID = 2001

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    suspend fun checkForUpdate(context: Context) {
        try {
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
            val info = fetchUpdateInfo(context)
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

            if (showingProgress) AppNotificationManager.cancel(NOTIFICATION_ID)
            if (info == null || pending == null) return
            AppNotificationManager.showInfo(
                id = NOTIFICATION_ID,
                channel = AppNotificationManager.CHANNEL_GENERAL,
                title = context.getString(R.string.update_notification_title, info.versionName),
                text = context.getString(R.string.update_notification_text),
                autoCancel = true,
                contentIntent = pending,
            )
        } catch (e: Exception) {
            LoggingManager.w("UpdateChecker", "Startup update check failed", e)
        }
    }

    private suspend fun fetchUpdateInfo(context: Context): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val url = URL(UPDATES_URL)
            val conn =
                (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            val remoteChannel = json.optString("channel").ifBlank { "stable" }
            val acceptChannel =
                when {
                    remoteChannel == BuildConfig.RELEASE_CHANNEL -> true
                    BuildConfig.RELEASE_CHANNEL == "preview" && remoteChannel == "stable" -> true
                    else -> false
                }
            if (!acceptChannel) return@withContext null

            val remoteVersionRaw = json.optString("version")
            val remoteCode = json.optLong("versionCode", 0)
            val remoteVersion = parseVersion(remoteVersionRaw) ?: return@withContext null

            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersion = parseVersion(pkg.versionName ?: "") ?: return@withContext null
            val currentCode = pkg.longVersionCode

            val apkUrl = json.optString("url").takeIf { it.isNotBlank() } ?: return@withContext null

            return@withContext if (isRemoteNewer(currentVersion, remoteVersion) || remoteCode > currentCode) {
                UpdateInfo(remoteVersionRaw, apkUrl)
            } else {
                null
            }
        }

    private fun parseVersion(value: String): Version? {
        val regex = Regex("""(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z0-9.-]+))?""")
        val m = regex.matchEntire(value) ?: return null
        val (maj, min, patch, pre) = m.destructured
        return Version(maj.toInt(), min.toInt(), patch.toInt(), pre.ifBlank { null })
    }

    private fun isRemoteNewer(
        current: Version,
        remote: Version,
    ): Boolean {
        if (remote.major != current.major) return remote.major > current.major
        if (remote.minor != current.minor) return remote.minor > current.minor
        if (remote.patch != current.patch) return remote.patch > current.patch
        val curPre = current.preRelease
        val remPre = remote.preRelease
        return when {
            curPre == null && remPre == null -> false
            curPre == null && remPre != null -> false
            curPre != null && remPre == null -> true
            else -> remPre!! > curPre!!
        }
    }

    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String?,
    )

    private data class UpdateInfo(
        val versionName: String,
        val url: String,
    )
}
