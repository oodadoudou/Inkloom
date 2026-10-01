package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Test fun batchMoveValidatesAllNotesBeforeChangingAny() {
        val folder = InkFolder(id = "target", name = "Target")
        val notes = listOf(InkDocument(id = "a", title = "A"), InkDocument(id = "b", title = "B"), InkDocument(id = "c", title = "C"))
        assertNull(LibraryFolderOperations.moveDocuments(notes, listOf(folder), setOf("a", "missing"), folder.id))
        assertNull(LibraryFolderOperations.moveDocuments(notes, listOf(folder), setOf("a", "b"), "missing"))
        val moved = LibraryFolderOperations.moveDocuments(notes, listOf(folder), setOf("b", "a"), folder.id)!!
        assertEquals(listOf("target", "target", null), moved.map(InkDocument::folderId))
        assertEquals(notes[2], moved[2])
    }

    @Test fun batchFolderMoveKeepsDescendantsAndRejectsCyclesOrNameConflicts() {
        val a = InkFolder(id = "a", name = "Art")
        val child = InkFolder(id = "child", name = "Drafts", parentId = "a")
        val grandchild = InkFolder(id = "grandchild", name = "Ink", parentId = "child")
        val b = InkFolder(id = "b", name = "Notes")
        val target = InkFolder(id = "target", name = "Target")
        val folders = listOf(a, child, grandchild, b, target)

        val moved = LibraryFolderOperations.moveFolders(folders, setOf("a", "child", "b"), "target")!!
        assertEquals("target", moved.first { it.id == "a" }.parentId)
        assertEquals("a", moved.first { it.id == "child" }.parentId)
        assertEquals("child", moved.first { it.id == "grandchild" }.parentId)
        assertEquals("target", moved.first { it.id == "b" }.parentId)
        assertNull(LibraryFolderOperations.moveFolders(folders, setOf("a"), "grandchild"))
        assertNull(LibraryFolderOperations.moveFolders(folders, setOf("a", "missing"), "target"))
        assertNull(LibraryFolderOperations.moveFolders(folders + InkFolder(id = "collision", name = "Art", parentId = "target"), setOf("a"), "target"))
    }

}
