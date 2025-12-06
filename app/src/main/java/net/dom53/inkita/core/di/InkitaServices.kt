package net.dom53.inkita.core.di

import android.content.Context
import net.dom53.inkita.core.cache.CacheManagerImpl
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.ReaderRepositoryImpl
import net.dom53.inkita.domain.repository.ReaderRepository
import java.io.File

/**
 * Very small service locator to provide dependencies for background workers
 * (e.g., ProgressSyncWorker) where we can't easily pass objects directly.
 */
data class InkitaServices(
    val readerRepository: ReaderRepository,
) {
    companion object {
        @Volatile
        private var instance: InkitaServices? = null

        fun get(context: Context): InkitaServices =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): InkitaServices {
            val appPreferences = AppPreferences(context)
            val database = InkitaDatabase.getInstance(context)
            // CacheManager is not needed for reader repository, but ensure thumbs dir exists for other lookups
            File(context.filesDir, "thumbnails").mkdirs()
            val readerRepository =
                ReaderRepositoryImpl(
                    context,
                    appPreferences,
                    database.readerDao(),
                    database.downloadDao(),
                )
            return InkitaServices(readerRepository = readerRepository)
        }
    }
}
