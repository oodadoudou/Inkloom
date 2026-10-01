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
        if (selectedIds.isEmpty() || folders.count { it.id in selectedIds && it.trashedAt==null } != selectedIds.size) return null
        if (destinationId != null && folders.none { it.id == destinationId && it.trashedAt==null }) return null
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
        val targetNames = folders.filter { it.trashedAt==null && it.parentId == destinationId && it.id !in movingRoots }
            .map { it.name.lowercase() }.toMutableSet()
        for (id in movingRoots) {
            if (!targetNames.add(requireNotNull(byId[id]).name.lowercase())) return null
        }
        return folders.map { if (it.id in movingRoots) it.copy(parentId = destinationId) else it }
    }

    fun create(folders: List<InkFolder>, name: String, parentId: String? = null): List<InkFolder> {
        val normalized = name.trim()
        if (normalized.isBlank()) return folders
        if (parentId != null && folders.none { it.id == parentId && it.trashedAt==null }) return folders
        if (folders.any { it.trashedAt==null && it.parentId == parentId && it.name.equals(normalized, ignoreCase = true) }) return folders
        return folders + InkFolder(name = normalized, parentId = parentId)
    }

    fun rename(folders: List<InkFolder>, folderId: String, name: String): List<InkFolder> {
        val normalized = name.trim()
        val target = folders.firstOrNull { it.id == folderId && it.trashedAt==null } ?: return folders
        if (normalized.isBlank()) return folders
        if (folders.any { it.trashedAt==null && it.id != folderId && it.parentId == target.parentId && it.name.equals(normalized, ignoreCase = true) }) return folders
        return folders.map { if (it.id == folderId) it.copy(name = normalized) else it }
    }

    fun moveDocument(document: InkDocument, folders: List<InkFolder>, folderId: String?): InkDocument {
        if (folderId != null && folders.none { it.id == folderId && it.trashedAt==null }) return document
        if (document.folderId == folderId) return document
        return document.copy(folderId = folderId, updatedAt = System.currentTimeMillis())
    }

    fun moveDocuments(documents: List<InkDocument>, folders: List<InkFolder>, documentIds: Set<String>, folderId: String?): List<InkDocument>? {
        if (documentIds.isEmpty() || (folderId != null && folders.none { it.id == folderId && it.trashedAt==null })) return null
        if (documents.count { it.id in documentIds && it.trashedAt == null } != documentIds.size) return null
        return documents.map { if (it.id in documentIds) moveDocument(it, folders, folderId) else it }
    }

    /** Folder trash keeps ancestry and the exact set of notes trashed by this operation. Notes
     * already in trash remain independently trashed when their containing folder is restored. */
    fun trashMany(folders:List<InkFolder>,documents:List<InkDocument>,ids:Set<String>,now:Long=System.currentTimeMillis()):Removal? {
        if(ids.isEmpty() || folders.count{it.id in ids && it.trashedAt==null}!=ids.size)return null
        val byId=folders.associateBy{it.id}
        val roots=ids.filter{ id ->
            var parent=byId[id]?.parentId;val seen=hashSetOf(id);var selected=false
            while(parent!=null && seen.add(parent)){if(parent in ids){selected=true;break};parent=byId[parent]?.parentId};!selected
        }
        val batches=mutableMapOf<String,String>()
        roots.forEach{root->val batch=java.util.UUID.randomUUID().toString();descendants(folders,setOf(root)).filter{byId[it]?.trashedAt==null}.forEach{batches[it]=batch}}
        return Removal(folders.map{f->batches[f.id]?.let{f.copy(trashedAt=now,trashBatchId=it)}?:f},documents.map{d->
            val batch=batches[d.folderId]
            if(batch!=null && d.trashedAt==null)d.copy(trashedAt=now,folderTrashBatchId=batch,updatedAt=now)else d
        })
    }

    fun restore(folders:List<InkFolder>,documents:List<InkDocument>,folderId:String,now:Long=System.currentTimeMillis()):Removal? {
        val folder=folders.firstOrNull{it.id==folderId && it.trashedAt!=null}?:return null
        val batch=folder.trashBatchId ?: return null
        val restoring=folders.filter{it.trashBatchId==batch && it.trashedAt!=null}.mapTo(hashSetOf()){it.id}
        val byId=folders.associateBy{it.id};val active=folders.filter{it.trashedAt==null}.mapTo(hashSetOf()){it.id}
        val next=folders.filter{it.id !in restoring}.toMutableList()
        fun usableParent(id:String?):String? {var parent=id;val seen=hashSetOf<String>();while(parent!=null && parent !in active && parent !in restoring && seen.add(parent))parent=byId[parent]?.parentId;return parent?.takeIf{it in active || it in restoring}}
        folders.filter{it.id in restoring}.forEach{f->
            val parent=usableParent(f.parentId);var name=f.name;var suffix=2
            while(next.any{it.trashedAt==null && it.parentId==parent && it.name.equals(name,true)}){name="${f.name} ($suffix)";suffix++}
            next+=f.copy(name=name,parentId=parent,trashedAt=null,trashBatchId=null)
        }
        val mapped=next.associateBy{it.id}
        return Removal(folders.map{mapped.getValue(it.id)},documents.map{d->
            if(d.folderTrashBatchId==batch && d.trashedAt!=null && d.folderId in restoring)d.copy(trashedAt=null,folderTrashBatchId=null,updatedAt=now)else d
        })
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
