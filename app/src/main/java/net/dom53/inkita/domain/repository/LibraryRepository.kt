package net.dom53.inkita.domain.repository

import net.dom53.inkita.domain.model.Library

interface LibraryRepository {
    suspend fun getLibraries(): List<Library>

    suspend fun hasLibraryAccess(libraryId: Int): Boolean
}
