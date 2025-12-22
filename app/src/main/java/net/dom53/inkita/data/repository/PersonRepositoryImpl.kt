package net.dom53.inkita.data.repository

import android.content.Context
import kotlinx.coroutines.flow.first
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.network.NetworkUtils
import net.dom53.inkita.core.storage.AppPreferences
import net.dom53.inkita.data.api.dto.BrowsePersonFilterDto
import net.dom53.inkita.data.api.dto.PersonFilterStatementDto
import net.dom53.inkita.data.api.dto.PersonSortOptionsDto
import net.dom53.inkita.data.mapper.toDomain
import net.dom53.inkita.domain.model.Person
import net.dom53.inkita.domain.repository.PersonRepository
import java.io.IOException

class PersonRepositoryImpl(
    private val context: Context,
    private val appPreferences: AppPreferences,
) : PersonRepository {
    override suspend fun getBrowsePeople(
        pageNumber: Int,
        pageSize: Int,
    ): List<Person> {
        val config = appPreferences.configFlow.first()
        if (!config.isConfigured) return emptyList()

        if (!NetworkUtils.isOnline(context)) throw IOException("Offline")

        val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
        val filter =
            BrowsePersonFilterDto(
                id = null,
                name = null,
                statements =
                    listOf(
                        PersonFilterStatementDto(
                            comparison = 5,
                            field = 1,
                            value = "8,3",
                        ),
                    ),
                combination = 1,
                sortOptions =
                    PersonSortOptionsDto(
                        isAscending = true,
                        sortField = 1,
                    ),
                limitTo = 0,
            )

        val response =
            api.getBrowsePeople(
                filter = filter,
                pageNumber = pageNumber,
                pageSize = pageSize,
            )

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string()
            throw Exception("Error loading people: HTTP ${response.code()} ${response.message()} body=$body")
        }

        val dtos = response.body() ?: emptyList()
        return dtos.map { it.toDomain() }
    }
}
