package net.dom53.inkita.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.domain.model.Library
import net.dom53.inkita.domain.repository.LibraryRepository
import java.io.IOException

class LibraryRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : LibraryRepository {
    override suspend fun getLibraries(): List<Library> {
        val config = appPreferences.configFlow.first()

        if (!config.isConfigured) {
            return emptyList()
        }

        if (!NetworkUtils.isOnline(context)) {
            throw IOException("Offline")
        }

        val api =
            KavitaApiFactory.createAuthenticated(
                baseUrl = config.serverUrl,
                apiKey = config.apiKey,
            )

        val response = api.getLibrariesFilter()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code()} ${response.message()}")
        }

        val dtos = response.body() ?: emptyList()
        return dtos.map { it.toDomain() }
    }
}
