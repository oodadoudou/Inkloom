package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class FolderTrashTest {
    @Test fun restoresHierarchyOnlyWithNotesTrashedInTheSameBatch(){
        val folders=listOf(InkFolder(id="root",name="Art"),InkFolder(id="child",name="Ink",parentId="root"))
        val notes=listOf(InkDocument(id="active",title="Active",folderId="child"),InkDocument(id="old-trash",title="Old",folderId="child",trashedAt=1),InkDocument(id="outside",title="Outside"))
        val trash=LibraryFolderOperations.trashMany(folders,notes,setOf("root"),now=99)!!
        assertTrue(trash.folders.all{it.trashedAt==99L});assertEquals(1L,trash.documents[1].trashedAt)
        assertEquals(notes[2],trash.documents[2]);assertNull(trash.documents[1].folderTrashBatchId)
        assertEquals("child",trash.documents[0].folderId)
        val restored=LibraryFolderOperations.restore(trash.folders,trash.documents,"root",now=100)!!
        assertEquals(folders,restored.folders);assertNull(restored.documents[0].trashedAt)
        assertEquals(notes[1],restored.documents[1]);assertEquals(notes[2],restored.documents[2])
    }
    @Test fun independentTrashBatchAndNameCollisionDoNotCorruptOtherFolders(){
        val folders=listOf(InkFolder(id="a",name="Art"),InkFolder(id="b",name="Notes"))
        val trash=LibraryFolderOperations.trashMany(folders,emptyList(),setOf("a","b"))!!
        assertNotEquals(trash.folders[0].trashBatchId,trash.folders[1].trashBatchId)
        val duplicate=InkFolder(id="new",name="Art")
        val restored=LibraryFolderOperations.restore(trash.folders+duplicate,emptyList(),"a")!!
        assertEquals("Art (2)",restored.folders.first{it.id=="a"}.name)
        assertNotNull(restored.folders.first{it.id=="b"}.trashedAt)
        assertEquals(duplicate,restored.folders.last())
        assertNull(LibraryFolderOperations.moveFolders(trash.folders,setOf("a"),null))
        assertEquals(trash.folders,LibraryFolderOperations.create(trash.folders,"Child","a"))
    }
    @Test fun restoringChildAfterItsParentWasIndependentlyTrashedUsesAvailableRoot(){
        val folders=listOf(InkFolder(id="a",name="Art"),InkFolder(id="b",name="Child",parentId="a"))
        val child=LibraryFolderOperations.trashMany(folders,emptyList(),setOf("b"))!!
        val parent=LibraryFolderOperations.trashMany(child.folders,emptyList(),setOf("a"))!!
        val restored=LibraryFolderOperations.restore(parent.folders,emptyList(),"b")!!
        assertNull(restored.folders.first{it.id=="b"}.parentId)
        assertNotNull(restored.folders.first{it.id=="a"}.trashedAt)
    }
}
