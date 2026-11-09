package net.dom53.inkita.ui.settings.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dom53.inkita.BuildConfig
import net.dom53.inkita.R
import net.dom53.inkita.core.logging.LoggingManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var showLicenses by remember { mutableStateOf(false) }
    val libraries by produceLibraries(R.raw.aboutlibraries)
    val versionName =
        remember {
            runCatching {
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                pkg.versionName ?: pkg.longVersionCode.toString()
            }.getOrDefault("--")
        }
    val versionCode =
        remember {
            runCatching {
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                pkg.longVersionCode
            }.getOrDefault(0L)
        }
    val buildTime =
        remember {
            runCatching {
                val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                formatter.format(Date(pkg.lastUpdateTime))
            }.getOrDefault("--")
        }
    val kavitaTarget = "nightly-0.8.8.15"
    val scope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var pendingDownloadId by rememberSaveable { mutableStateOf<Long?>(null) }
    var awaitingInstallPermission by rememberSaveable { mutableStateOf(false) }
    var pendingUpdateVersion by remember { mutableStateOf<String?>(null) }
    var pendingUpdateUrl by remember { mutableStateOf<String?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val installPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!context.packageManager.canRequestPackageInstalls()) pendingDownloadId = null
            awaitingInstallPermission = false
        }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun parseVersion(value: String): Version? {
        val regex = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([A-Za-z0-9.-]+))?""")
        val m = regex.matchEntire(value) ?: return null
        val (maj, min, patch, pre) = m.destructured
        return Version(maj.toInt(), min.toInt(), patch.toInt(), pre.ifBlank { null })
    }

    fun isRemoteNewer(
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
            curPre == null && remPre != null -> false // remote is prerelease, current stable -> not newer
            curPre != null && remPre == null -> true // remote stable, current prerelease -> newer
            else -> remPre!! > curPre!!
        }
    }

    fun isRemoteCodeNewer(
        currentCode: Long,
        remoteCode: Long?,
    ): Boolean {
        if (remoteCode == null || remoteCode <= 0) return false
        return remoteCode > currentCode
    }
    DisposableEffect(context, lifecycle, pendingDownloadId, awaitingInstallPermission) {
        val downloadId = pendingDownloadId ?: return@DisposableEffect onDispose { }
        if (awaitingInstallPermission) return@DisposableEffect onDispose { }

        fun openDownloadedUpdate() {
            if (pendingDownloadId != downloadId || awaitingInstallPermission || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

            val status =
                downloadManager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) else null
                }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        awaitingInstallPermission = true
                        runCatching {
                            installPermissionLauncher.launch(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null)),
                            )
                        }.onSuccess {
                            showToast(context.getString(R.string.settings_about_update_allow_install))
                        }.onFailure {
                            pendingDownloadId = null
                            awaitingInstallPermission = false
                            LoggingManager.w("AppUpdate", "Could not open installation permission settings", it)
                            showToast(context.getString(R.string.settings_about_update_install_failed))
                        }
                        return
                    }
                    pendingDownloadId = null
                    val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
                    val opened =
                        apkUri != null &&
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(apkUri, APK_MIME_TYPE)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                )
                            }.onFailure { LoggingManager.w("AppUpdate", "Could not open the APK installer", it) }
                                .isSuccess
                    if (!opened) showToast(context.getString(R.string.settings_about_update_install_failed))
                }
                DownloadManager.STATUS_FAILED, null -> {
                    pendingDownloadId = null
                    showToast(context.getString(R.string.settings_about_update_download_failed))
                }
            }
        }

        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE &&
                        intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == downloadId
                    ) {
                        openDownloadedUpdate()
                    }
                }
            }
        // DownloadManager sends this from another process. Only act on the status of our own download ID.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) openDownloadedUpdate()
            }
        lifecycle.addObserver(observer)
        openDownloadedUpdate()
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    val onCheckUpdates: () -> Unit = onCheckUpdates@{
        if (isCheckingUpdate) return@onCheckUpdates
        isCheckingUpdate = true
        scope.launch {
            val message =
                withContext(Dispatchers.IO) {
                    try {
                        val url = URL("https://dom-53.github.io/Inkita/updates.json")
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
                                BuildConfig.RELEASE_CHANNEL == "preview" && remoteChannel == "stable" -> true // preview build can take stable
                                else -> false
                            }
                        if (!acceptChannel) {
                            return@withContext context.getString(R.string.settings_about_up_to_date)
                        }
                        val remoteVersionRaw = json.optString("version")
                        val remoteCode = json.optLong("versionCode", 0)
                        val remoteVersion = parseVersion(remoteVersionRaw) ?: return@withContext context.getString(R.string.settings_about_up_to_date)
                        val currentVersion = parseVersion(BuildConfig.VERSION_NAME) ?: return@withContext context.getString(R.string.settings_about_up_to_date)
                        return@withContext if (isRemoteCodeNewer(versionCode, remoteCode) || isRemoteNewer(currentVersion, remoteVersion)) {
                            val apkUrl = json.optString("url")
                            if (apkUrl.isNullOrBlank()) {
                                context.getString(R.string.settings_about_update_failed)
                            } else {
                                pendingUpdateVersion = remoteVersionRaw
                                pendingUpdateUrl = apkUrl
                                showUpdateDialog = true
                                context.getString(R.string.settings_about_update_available, remoteVersionRaw)
                            }
                        } else {
                            context.getString(R.string.settings_about_up_to_date)
                        }
                    } catch (_: Exception) {
                        context.getString(R.string.settings_about_update_failed)
                    }
                }
            isCheckingUpdate = false
            showToast(message)
        }
    }
    val startDownload: () -> Unit = download@{
        if (pendingDownloadId != null) return@download
        val url = pendingUpdateUrl
        val version = pendingUpdateVersion
        if (url.isNullOrBlank() || version.isNullOrBlank()) {
            showToast(context.getString(R.string.settings_about_update_failed))
            return@download
        }
        runCatching {
            val request =
                DownloadManager
                    .Request(Uri.parse(url))
                    .setTitle("Inkita $version")
                    .setMimeType(APK_MIME_TYPE)
                    .setDescription(context.getString(R.string.settings_about_update_downloading))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val path = "InkitaUpdates/inkita-$version.apk"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, path)
            } else {
                request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, path)
            }
            downloadManager.enqueue(request)
        }.onSuccess { id ->
            pendingDownloadId = id
            showToast(context.getString(R.string.settings_about_update_downloading))
        }.onFailure {
            LoggingManager.w("AppUpdate", "Could not start the APK download", it)
            showToast(context.getString(R.string.settings_about_update_download_failed))
        }
    }
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    val scrollState = rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back))
            }
            Icon(
                painter = painterResource(id = R.drawable.inkita_launcher_foreground),
                contentDescription = "Inkita logo",
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified,
            )
            Text(stringResource(R.string.settings_about_title), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(R.string.settings_about_version) + ": $versionName")
        Text(stringResource(R.string.settings_about_build_time) + ": $buildTime")
        Text(stringResource(R.string.settings_about_kavita_version) + ": $kavitaTarget")

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_check_updates)) },
            leadingContent = { Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { onCheckUpdates() }
                    .padding(vertical = 2.dp),
        )

        Text(stringResource(R.string.settings_about_label_whats_new), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_changelog_title)) },
            supportingContent = { Text(stringResource(R.string.settings_about_changelog_desc)) },
            leadingContent = { Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://github.com/dom-53/Inkita/blob/develop/CHANGELOG.md") }
                    .padding(vertical = 2.dp),
        )

        Text(stringResource(R.string.settings_about_label_community), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_website)) },
            leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://www.kavitareader.com") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("Kavita Discord") },
            leadingContent = {
                Icon(
                    painterResource(id = R.drawable.ic_discord),
                    contentDescription = "Discord",
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://discord.gg/b52wT37kt7") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("GitHub") },
            leadingContent = {
                Icon(
                    painterResource(id = R.drawable.ic_github),
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://github.com/dom-53/Inkita") }
                    .padding(vertical = 2.dp),
        )

        Text(stringResource(R.string.settings_about_label_contribute), style = MaterialTheme.typography.titleMedium)
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_kofi)) },
            leadingContent = {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.settings_about_kofi),
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://ko-fi.com/dom53") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text("Help translate") },
            leadingContent = {
                Icon(
                    painterResource(id = R.drawable.ic_translate),
                    contentDescription = stringResource(R.string.settings_about_help_transalate),
                    modifier = Modifier.size(24.dp),
                )
            },
            modifier =
                Modifier
                    .clickable { openUrl("https://poeditor.com/join/project/XLBygoGhOX") }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_osl)) },
            supportingContent = { Text(stringResource(R.string.settings_about_osl_3rd)) },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { showLicenses = true }
                    .padding(vertical = 2.dp),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about_license)) },
            supportingContent = { Text("Apache-2.0") },
            leadingContent = { Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(24.dp)) },
            modifier =
                Modifier
                    .clickable { openUrl("https://www.apache.org/licenses/LICENSE-2.0") }
                    .padding(vertical = 2.dp),
        )
    }

    if (showLicenses) {
        BackHandler { showLicenses = false }
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LibrariesContainer(
                libraries = libraries,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.settings_about_update_available, pendingUpdateVersion ?: "")) },
            text = { Text(stringResource(R.string.settings_about_update_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    startDownload()
                }) { Text(stringResource(R.string.settings_about_update_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.general_cancel)) }
            },
        )
    }
}

private data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
)
