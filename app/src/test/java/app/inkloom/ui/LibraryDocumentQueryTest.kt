package app.inkloom.ui

import app.inkloom.document.InkDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDocumentQueryTest {
    @Test fun recentsAreAnOrderedFilterWithoutDuplicatingDocuments() {
        val old = InkDocument(id = "old", title = "Old", updatedAt = 10L)
        val new = InkDocument(id = "new", title = "New", updatedAt = 30L)
        val archived = InkDocument(id = "archived", title = "Archived", updatedAt = 40L, archivedAt = 40L)

        val result = LibraryDocumentQuery.visible(listOf(old, archived, new), LibraryFilter.RECENT, null, "")

        assertEquals(listOf("new", "old"), result.map(InkDocument::id))
        assertEquals(result.size, result.distinctBy(InkDocument::id).size)
    }

    @Test fun favoritesAndFolderFiltersDoNotInjectARecentShelf() {
        val favorite = InkDocument(id = "favorite", title = "Favorite", folderId = "folder", favorite = true)
        val other = InkDocument(id = "other", title = "Other", favorite = true)

        val result = LibraryDocumentQuery.visible(listOf(favorite, other), LibraryFilter.FAVORITES, "folder", "")

        assertEquals(listOf("favorite"), result.map(InkDocument::id))
        assertTrue(result.all(InkDocument::favorite))
    }
}
