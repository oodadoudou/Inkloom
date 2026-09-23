package app.inkloom.document

/** Pure folder mutations shared by UI actions and drag/drop. */
object LibraryFolderOperations {
    data class Removal(val folders: List<InkFolder>, val documents: List<InkDocument>)

    fun create(folders: List<InkFolder>, name: String, parentId: String? = null): List<InkFolder> {
        val normalized = name.trim()
        if (normalized.isBlank()) return folders
        if (parentId != null && folders.none { it.id == parentId }) return folders
        if (folders.any { it.parentId == parentId && it.name.equals(normalized, ignoreCase = true) }) return folders
        return folders + InkFolder(name = normalized, parentId = parentId)
    }

    fun rename(folders: List<InkFolder>, folderId: String, name: String): List<InkFolder> {
        val normalized = name.trim()
        val target = folders.firstOrNull { it.id == folderId } ?: return folders
        if (normalized.isBlank()) return folders
        if (folders.any { it.id != folderId && it.parentId == target.parentId && it.name.equals(normalized, ignoreCase = true) }) return folders
        return folders.map { if (it.id == folderId) it.copy(name = normalized) else it }
    }

    fun moveDocument(document: InkDocument, folders: List<InkFolder>, folderId: String?): InkDocument {
        if (folderId != null && folders.none { it.id == folderId }) return document
        if (document.folderId == folderId) return document
        return document.copy(folderId = folderId, updatedAt = System.currentTimeMillis())
    }

    fun moveDocuments(documents: List<InkDocument>, folders: List<InkFolder>, documentIds: Set<String>, folderId: String?): List<InkDocument>? {
        if (documentIds.isEmpty() || (folderId != null && folders.none { it.id == folderId })) return null
        if (documents.count { it.id in documentIds && it.trashedAt == null } != documentIds.size) return null
        return documents.map { if (it.id in documentIds) moveDocument(it, folders, folderId) else it }
    }

    fun remove(folders: List<InkFolder>, documents: List<InkDocument>, folderId: String): Removal {
        if (folders.none { it.id == folderId }) return Removal(folders, documents)
        val removedIds = buildSet {
            add(folderId)
            folders.filter { it.parentId == folderId }.forEach { add(it.id) }
        }
        return Removal(
            folders = folders.filterNot { it.id in removedIds },
            documents = documents.map { document ->
                if (document.folderId in removedIds) document.copy(folderId = null, updatedAt = System.currentTimeMillis()) else document
            },
        )
    }
}
