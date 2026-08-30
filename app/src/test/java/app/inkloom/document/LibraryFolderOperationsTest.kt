package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryFolderOperationsTest {
    @Test fun createAndRenameRequireValidUniqueSiblingNames() {
        val root = InkFolder(id = "root", name = "Reference")
        val child = InkFolder(id = "child", name = "Sketches", parentId = root.id)
        val initial = listOf(root, child)

        assertEquals(initial, LibraryFolderOperations.create(initial, "Sketches", root.id))
        assertEquals(initial, LibraryFolderOperations.create(initial, "New", "missing"))
        assertEquals("Studies", LibraryFolderOperations.rename(initial, child.id, " Studies ").last().name)
        assertEquals(initial, LibraryFolderOperations.rename(initial, child.id, ""))
    }

    @Test fun documentMoveAcceptsOnlyARealFolderAndChangesExactlyOneDocument() {
        val folder = InkFolder(id = "target", name = "Target")
        val document = InkDocument(id = "note", title = "Note", updatedAt = 1L)

        assertEquals(document, LibraryFolderOperations.moveDocument(document, listOf(folder), "missing"))
        val moved = LibraryFolderOperations.moveDocument(document, listOf(folder), folder.id)
        assertEquals(folder.id, moved.folderId)
        assertNotEquals(document.updatedAt, moved.updatedAt)
    }

    @Test fun deletingAParentRemovesChildrenAndUnassignsTheirDocuments() {
        val root = InkFolder(id = "root", name = "Root")
        val child = InkFolder(id = "child", name = "Child", parentId = root.id)
        val outside = InkFolder(id = "outside", name = "Outside")
        val nestedDocument = InkDocument(id = "nested", title = "Nested", folderId = child.id)
        val outsideDocument = InkDocument(id = "outside-note", title = "Outside", folderId = outside.id)

        val result = LibraryFolderOperations.remove(listOf(root, child, outside), listOf(nestedDocument, outsideDocument), root.id)

        assertEquals(listOf(outside), result.folders)
        assertEquals(null, result.documents.first { it.id == nestedDocument.id }.folderId)
        assertEquals(outside.id, result.documents.first { it.id == outsideDocument.id }.folderId)
    }
}
