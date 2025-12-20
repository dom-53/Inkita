package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.Person

interface PersonRepository {
    suspend fun getBrowsePeople(
        pageNumber: Int = 1,
        pageSize: Int = 50,
    ): List<Person>
}
