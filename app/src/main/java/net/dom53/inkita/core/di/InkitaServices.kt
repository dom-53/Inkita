package net.dom53.inkita.core.di

import android.content.Context
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.local.db.InkitaDatabase
import net.dom53.inkita.data.repository.ReaderRepositoryImpl
import net.dom53.inkita.domain.repository.ReaderRepository

/**
 * Very small service locator to provide dependencies for background workers
 * (e.g., ProgressSyncWorker) where we can't easily pass objects directly.
 */
data class InkitaServices(
    val readerRepository: ReaderRepository,
) {
    companion object {
        fun get(
            context: Context,
            appPreferences: AppPreferences,
        ): InkitaServices {
            val database = InkitaDatabase.getInstance(context, appPreferences.profile)
            val readerRepository =
                ReaderRepositoryImpl(
                    context,
                    appPreferences,
                    database.readerDao(),
                    database.downloadV2Dao(),
                )
            return InkitaServices(readerRepository = readerRepository)
        }
    }
}
