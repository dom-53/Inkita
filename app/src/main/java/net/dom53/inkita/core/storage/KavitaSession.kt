package net.dom53.inkita.core.storage

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.encodeUtf8

data class KavitaSession(
    val config: AppConfig,
    val profile: KavitaProfile,
)

@ConsistentCopyVisibility
data class KavitaProfile internal constructor(
    val id: String,
) {
    val databaseName: String
        get() = if (this == Legacy) "inkita.db" else "inkita-$id.db"

    val downloadFolder: String
        get() = if (this == Legacy) "download" else "$DOWNLOAD_FOLDER_PREFIX$id"

    val thumbnailFolder: String
        get() = if (this == Legacy) "thumbnails" else "thumbnails-$id"

    companion object {
        val Legacy = KavitaProfile("legacy")
        val Guest = KavitaProfile("guest")
        const val WORK_INPUT = "kavita_profile"
        const val DOWNLOAD_FOLDER_PREFIX = "inkita-download-"
    }
}

internal fun AppConfig.profileId(useAccountId: Boolean = true): String {
    val address = (serverUrl.toHttpUrlOrNull()?.toString() ?: serverUrl.trim()).removeSuffix("/")
    val account = if (useAccountId && userId > 0) "user:$userId" else "key:$apiKey"
    return "$address\n$account".encodeUtf8().sha256().hex()
}
