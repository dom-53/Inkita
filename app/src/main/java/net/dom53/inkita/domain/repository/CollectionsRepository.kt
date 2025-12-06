// domain/repository/CollectionsRepository.kt
package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.Collection
import net.dom53.inkita.domain.model.Series

interface CollectionsRepository {
    suspend fun getCollections(): List<Collection>

    suspend fun getSeriesForCollection(
        collectionId: Int,
        page: Int = 1,
        pageSize: Int = 50,
    ): List<Series>
}
