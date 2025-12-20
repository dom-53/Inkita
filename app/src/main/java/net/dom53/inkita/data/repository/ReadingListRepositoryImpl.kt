package net.dom53.inkita.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.domain.model.ReadingList
import net.dom53.inkita.domain.repository.ReadingListRepository
import java.io.IOException

class ReadingListRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : ReadingListRepository {
    override suspend fun getReadingLists(
        includePromoted: Boolean,
        sortByLastModified: Boolean,
        pageNumber: Int,
        pageSize: Int,
    ): List<ReadingList> {
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) return emptyList()

        if (!NetworkUtils.isOnline(context)) throw IOException("Offline")

        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
        val response =
            api.getReadingLists(
                includePromoted = includePromoted,
                sortByLastModified = sortByLastModified,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()
            throw Exception("Error loading reading lists: HTTP ${response.code()} ${response.message()} body=$body")
        }

        val dtos = response.body() ?: emptyList()
        return dtos.map { it.toDomain() }
    }
}
