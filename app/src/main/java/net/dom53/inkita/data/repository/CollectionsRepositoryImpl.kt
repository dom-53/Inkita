// data/repository/CollectionsRepositoryImpl.kt
package net.dom53.inkita.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Series
import net.dom53.inkita.domain.repository.CollectionsRepository
import java.io.IOException

class CollectionsRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : CollectionsRepository {
    override suspend fun getCollections(): List<Collection> {
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) return emptyList()

        if (!NetworkUtils.isOnline(context)) throw IOException("Offline")

        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
        val response = api.getCollections()

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()
            throw Exception("Error loading collections: HTTP ${response.code()} ${response.message()} body=$body")
        }

        val dtos = response.body() ?: emptyList()
        return dtos.map { it.toDomain() }
    }

    override suspend fun getSeriesForCollection(
        collectionId: Int,
        page: Int,
        pageSize: Int,
    ): List<Series> {
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) return emptyList()

        if (!NetworkUtils.isOnline(context)) throw IOException("Offline")

        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
        val response =
            api.getSeriesByCollection(
                collectionId = collectionId,
                pageNumber = page,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()
            throw Exception("Error loading collection series: HTTP ${response.code()} ${response.message()} body=$body")
        }

        val body = response.body() ?: emptyList()
        return body
            .map { it.toDomain() }
            .filter { series ->
                series.format == net.dom53.inkita.domain.model.Format.Epub || series.format == net.dom53.inkita.domain.model.Format.Pdf
            }
    }
}
