package app.inkloom.document

/** Pure folder mutations shared by UI actions and drag/drop. */
object LibraryFolderOperations {
    data class Removal(val folders: List<InkFolder>, val documents: List<InkDocument>)

    private fun descendants(folders: List<InkFolder>, roots: Set<String>): Set<String> {
        val children = folders.groupBy(InkFolder::parentId)
        val visited = roots.toMutableSet()
        val pending = ArrayDeque(roots)
        while (pending.isNotEmpty()) {
            children[pending.removeFirst()].orEmpty().forEach { child ->
                if (visited.add(child.id)) pending.addLast(child.id)
            }
        }
        return visited
    }

    /** Move selected roots while retaining their child folders and notebooks. Null means reject all. */
    fun moveFolders(folders: List<InkFolder>, selectedIds: Set<String>, destinationId: String?): List<InkFolder>? {
        if (selectedIds.isEmpty() || folders.count { it.id in selectedIds } != selectedIds.size) return null
        if (destinationId != null && folders.none { it.id == destinationId }) return null
        val byId = folders.associateBy(InkFolder::id)
        val movingRoots = selectedIds.filter { id ->
            var parent = byId[id]?.parentId
            val seen = mutableSetOf(id)
            var selectedAncestor = false
            while (parent != null && seen.add(parent)) {
                if (parent in selectedIds) { selectedAncestor = true; break }
                parent = byId[parent]?.parentId
            }
            !selectedAncestor
        }.toSet()
        if (destinationId != null && destinationId in descendants(folders, movingRoots)) return null
        val targetNames = folders.filter { it.parentId == destinationId && it.id !in movingRoots }
            .map { it.name.lowercase() }.toMutableSet()
        for (id in movingRoots) {
            if (!targetNames.add(requireNotNull(byId[id]).name.lowercase())) return null
        }
        return folders.map { if (it.id in movingRoots) it.copy(parentId = destinationId) else it }
    }

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
        return removeMany(folders, documents, setOf(folderId)) ?: Removal(folders, documents)
    }

    fun removeMany(folders: List<InkFolder>, documents: List<InkDocument>, folderIds: Set<String>): Removal? {
        if (folderIds.isEmpty() || folders.count { it.id in folderIds } != folderIds.size) return null
        val removedIds = descendants(folders, folderIds)
        return Removal(
            folders = folders.filterNot { it.id in removedIds },
            documents = documents.map { document ->
                if (document.folderId in removedIds) document.copy(folderId = null, updatedAt = System.currentTimeMillis()) else document
            },
        )
    }
}
