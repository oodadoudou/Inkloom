package app.inkloom.ui

import app.inkloom.document.InkDocument
import app.inkloom.document.PageBackground
import app.inkloom.document.PageSearch

internal enum class LibraryFilter { ALL, RECENT, FAVORITES, ARCHIVED, TRASH, PDF }

/** One source of truth for Library sections; Recents is a filter, never a duplicate card shelf. */
internal object LibraryDocumentQuery {
    fun visible(
        documents: List<InkDocument>,
        filter: LibraryFilter,
        folderId: String?,
        search: String,
    ): List<InkDocument> = documents
        .asSequence()
        .filter { document ->
            search.isBlank() ||
                document.title.contains(search, true) ||
                document.tags.any { it.contains(search, true) } ||
                document.pages.any { PageSearch.matches(it, search) }
        }
        .filter { folderId == null || it.folderId == folderId }
        .filter { document ->
            when (filter) {
                LibraryFilter.ALL, LibraryFilter.RECENT -> document.trashedAt == null && document.archivedAt == null
                LibraryFilter.FAVORITES -> document.favorite && document.trashedAt == null && document.archivedAt == null
                LibraryFilter.ARCHIVED -> document.archivedAt != null && document.trashedAt == null
                LibraryFilter.TRASH -> document.trashedAt != null
                LibraryFilter.PDF -> document.pages.any { it.background is PageBackground.Pdf } && document.trashedAt == null && document.archivedAt == null
            }
        }
        .let { matches -> if (filter == LibraryFilter.RECENT) matches.sortedByDescending(InkDocument::updatedAt) else matches }
        .toList()
}
