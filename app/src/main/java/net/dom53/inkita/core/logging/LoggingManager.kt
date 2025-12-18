package net.dom53.inkita.core.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Logging wrapper that writes to logcat and to rotating log files for debugging.
 *
 * Notes:
 * - Avoid logging secrets; message is scrubbed for common sensitive tokens.
 * - File logging can be enabled/disabled independently from logcat logging.
 */
object LoggingManager {
    private const val MAX_FILE_SIZE_BYTES: Long = 1_000_000 // ~1MB
    private const val MAX_FILES = 5
    private const val FILE_PREFIX = "inkita-log"
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var debugEnabled: Boolean = false
    private var errorsEnabled: Boolean = true
    private var fileLoggingEnabled: Boolean = true
    private var logDir: File? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        logDir = dir
        pruneOldFiles()
    }

    fun setEnabled(value: Boolean) {
        debugEnabled = value
    }

    fun setErrorsEnabled(value: Boolean) {
        errorsEnabled = value
    }

    fun setFileLoggingEnabled(value: Boolean) {
        fileLoggingEnabled = value
    }

    fun d(
        tag: String,
        msg: String,
    ) {
        if (debugEnabled) Log.d(tag, msg)
        writeToFile("D", tag, msg)
    }

    fun i(
        tag: String,
        msg: String,
    ) {
        if (debugEnabled) Log.i(tag, msg)
        writeToFile("I", tag, msg)
    }

    fun w(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (debugEnabled || errorsEnabled) {
            if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
        }
        writeToFile("W", tag, msg, tr)
    }

    fun e(
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (errorsEnabled) {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        }
        writeToFile("E", tag, msg, tr)
    }

    fun isDebugEnabled(): Boolean = debugEnabled

    /**
     * Bundle existing log files into a zip ready for sharing.
     */
    fun exportLogs(context: Context): File? {
        val dir = logDir ?: return null
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return null
        if (files.isEmpty()) return null

        val zipName = "inkita-logs-${System.currentTimeMillis()}.zip"
        val outFile = File(context.cacheDir, zipName)
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            files.forEach { f ->
                zos.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return outFile
    }

    /**
     * Clear all local log files.
     */
    fun clearLogs() {
        logDir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun writeToFile(
        level: String,
        tag: String,
        msg: String,
        tr: Throwable? = null,
    ) {
        if (!fileLoggingEnabled) return
        val dir = logDir ?: return
        ioScope.launch {
            try {
                val file = currentFile(dir)
                val ts = timestampFormat.format(Date())
                val payload = buildString {
                    append(ts).append(" ").append(level).append("/").append(tag).append(": ")
                    append(scrub(msg))
                    if (tr != null) {
                        append("\n").append(Log.getStackTraceString(tr))
                    }
                    append("\n")
                }
                FileOutputStream(file, true).bufferedWriter().use { it.write(payload) }
                rotateIfNeeded(file, dir)
            } catch (e: Exception) {
                // Fallback to logcat only to avoid crash loops.
                if (errorsEnabled) Log.e("LoggingManager", "Failed to write log file", e)
            }
        }
    }

    private fun currentFile(dir: File): File {
        val existing =
            dir.listFiles { file -> file.name.startsWith(FILE_PREFIX) && file.extension == "txt" }
                ?.maxByOrNull { it.lastModified() }
        return existing ?: File(dir, "$FILE_PREFIX-1.txt")
    }

    private fun rotateIfNeeded(file: File, dir: File) {
        if (file.length() <= MAX_FILE_SIZE_BYTES) return
        // Shift older files
        val files =
            dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.extension == "txt" }
                ?.sortedByDescending { it.nameWithoutExtension }
                ?: emptyList()
        files.forEach { f ->
            val idx = f.nameWithoutExtension.substringAfter("$FILE_PREFIX-").toIntOrNull() ?: return@forEach
            val nextIdx = idx + 1
            if (nextIdx > MAX_FILES) {
                runCatching { f.delete() }
            } else {
                val target = File(dir, "$FILE_PREFIX-$nextIdx.txt")
                runCatching { f.renameTo(target) }
            }
        }
        // Start fresh file 1
        File(dir, "$FILE_PREFIX-1.txt").writeText("")
    }

    private fun pruneOldFiles() {
        val dir = logDir ?: return
        val files =
            dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.extension == "txt" }
                ?.sortedBy { it.lastModified() }
                ?: return
        if (files.size <= MAX_FILES) return
        val toDelete = files.take(files.size - MAX_FILES)
        toDelete.forEach { runCatching { it.delete() } }
    }

    private fun scrub(input: String): String {
        var out = input
        val patterns =
            listOf(
                "api[_-]?key=([^\\s]+)" to "api_key=***",
                "Authorization: Bearer ([^\\s]+)" to "Authorization: Bearer ***",
                "token=([^\\s]+)" to "token=***",
                "serverUrl=([^\\s]+)" to "serverUrl=***",
                "x-api-key: ([^\\s]+)" to "x-api-key: ***",
            )
        patterns.forEach { (pattern, replacement) ->
            out = out.replace(Regex(pattern, RegexOption.IGNORE_CASE), replacement)
        }
        return out
    }
}
