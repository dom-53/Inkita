package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.ReadingList

interface ReadingListRepository {
    suspend fun getReadingLists(
        includePromoted: Boolean = true,
        sortByLastModified: Boolean = false,
        pageNumber: Int = 1,
        pageSize: Int = 50,
    ): List<ReadingList>
}
