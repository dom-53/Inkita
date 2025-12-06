package net.dom53.inkita.core.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import net.dom53.inkita.R
import net.dom53.inkita.core.logging.LoggingManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified entrypoint for creating channels and posting notifications across the app.
 */
object AppNotificationManager {
    const val CHANNEL_DOWNLOADS = "inkita_downloads"
    const val CHANNEL_PREFETCH = "inkita_prefetch"
    const val CHANNEL_SYNC = "inkita_sync"
    const val CHANNEL_GENERAL = "inkita_general"

    data class Action(
        @DrawableRes val iconRes: Int,
        val title: CharSequence,
        val pendingIntent: PendingIntent,
    )

    private data class ChannelSpec(
        val id: String,
        val name: String,
        val importance: Int,
        val showBadge: Boolean = false,
    )

    private val channelSpecs =
        listOf(
            ChannelSpec(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW, showBadge = false),
            ChannelSpec(CHANNEL_PREFETCH, "Prefetch", NotificationManager.IMPORTANCE_LOW, showBadge = false),
            ChannelSpec(CHANNEL_SYNC, "Sync", NotificationManager.IMPORTANCE_MIN, showBadge = false),
            ChannelSpec(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT, showBadge = true),
        )

    private val initialised = AtomicBoolean(false)
    private lateinit var appContext: Context
    private val accentColor: Int
        get() = ContextCompat.getColor(appContext, R.color.teal_700)

    /** Initialise and ensure all channels exist. Safe to call multiple times. */
    fun init(context: Context) {
        appContext = context.applicationContext
        initialised.set(true)
        ensureChannels()
    }

    /** Register channels if missing. */
    fun ensureChannels() {
        if (!initialised.get()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        channelSpecs.forEach { spec ->
            val existing = manager.getNotificationChannel(spec.id)
            if (existing == null) {
                val channel =
                    NotificationChannel(spec.id, spec.name, spec.importance).apply {
                        setShowBadge(spec.showBadge)
                        enableVibration(false)
                        enableLights(false)
                    }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /** Post or update a progress notification. Returns false if permission is missing. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showProgress(
        id: Int,
        channel: String,
        title: String,
        text: String,
        progress: Int?,
        max: Int?,
        ongoing: Boolean = true,
        actions: List<Action> = emptyList(),
    ): Boolean {
        if (!readyWithPermission()) return false
        val builder =
            baseBuilder(channel, title, text)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
        when {
            progress != null && max != null -> builder.setProgress(max, progress.coerceAtLeast(0), false)
            else -> builder.setProgress(0, 0, true)
        }
        actions.forEach { action -> builder.addAction(action.iconRes, action.title, action.pendingIntent) }
        NotificationManagerCompat.from(appContext).notify(id, builder.build())
        return true
    }

    /** Post a simple information notification. Returns false if permission is missing. */
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showInfo(
        id: Int,
        channel: String,
        title: String,
        text: String,
        autoCancel: Boolean = true,
    ): Boolean {
        if (!readyWithPermission()) return false
        val builder =
            baseBuilder(channel, title, text)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(autoCancel)
        NotificationManagerCompat.from(appContext).notify(id, builder.build())
        return true
    }

    fun cancel(id: Int) {
        if (!initialised.get()) return
        NotificationManagerCompat.from(appContext).cancel(id)
    }

    fun cancelAll() {
        if (!initialised.get()) return
        NotificationManagerCompat.from(appContext).cancelAll()
    }

    /**
     * Build a notification suitable for ForegroundInfo/Service use.
     * Does not post the notification; caller is responsible for promotion.
     */
    fun buildForegroundNotification(
        channel: String,
        title: String,
        text: String,
        progress: Int? = null,
        max: Int? = null,
        actions: List<Action> = emptyList(),
    ): Notification {
        ensureChannels()
        val builder =
            baseBuilder(channel, title, text)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
        if (progress != null && max != null) {
            builder.setProgress(max, progress.coerceAtLeast(0), false)
        }
        actions.forEach { action -> builder.addAction(action.iconRes, action.title, action.pendingIntent) }
        return builder.build()
    }

    /** Exposed for callers that need to gate foreground promotion. */
    fun canPostNotifications(): Boolean = readyWithPermission()

    private fun baseBuilder(
        channel: String,
        title: String,
        text: String,
    ): NotificationCompat.Builder {
        ensureChannels()
        val safeTitle = if (title.isBlank()) appContext.getString(R.string.app_name) else title
        return NotificationCompat
            .Builder(appContext, channel)
            .setSmallIcon(R.mipmap.inkita_launcher)
            .setContentTitle(safeTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(accentColor)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun readyWithPermission(): Boolean {
        if (!initialised.get()) {
            LoggingManager.w("AppNotificationManager", "Called before init; ignoring notification request")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
}
