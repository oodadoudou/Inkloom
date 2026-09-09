package app.inkloom.importer

import app.inkloom.model.ImportedBrush
import java.util.UUID

/** Provider-independent traversal: identities prevent cycles, paths preserve the user's hierarchy. */
object BrushFolderImport {
    data class Node(val id: String, val name: String, val directory: Boolean)
    data class Source(val id: String, val name: String, val group: String) { val path get() = "$group/$name" }
    data class Plan(val root: String, val groups: List<String>, val files: List<Source>, val skipped: Int, val errors: List<String>)
    private val extensions=setOf("sut","abr","brush","brushset","inkbrush","sutg","brushlibrary","zip")
    fun scan(root: Node, children: (String) -> List<Node>, cancelled: () -> Boolean = { false }): Plan {
        require(root.directory) { "Select a folder" }
        fun segment(name: String): String = BrushGroupOperations.normalize(name.replace('/', '∕')).also {
            require(it.isNotEmpty()) { "Folder has no readable name" }
        }
        val rootName=segment(root.name)
        val groups=mutableListOf<String>(); val files=mutableListOf<Source>(); val errors=mutableListOf<String>()
        var skipped=0
        val visited=mutableSetOf<String>(); val pending=ArrayDeque<Pair<Node,String>>()
        pending.add(root to rootName)
        while(pending.isNotEmpty() && !cancelled()) {
            val (folder,path)=pending.removeFirst()
            if(!visited.add(folder.id)) { errors += "$path: repeated directory reference"; continue }
            groups += path
            val result=runCatching { children(folder.id) }
            if(result.isFailure) { errors += "$path: ${result.exceptionOrNull()?.message}"; continue }
            val listed=result.getOrThrow()
            val names=mutableSetOf<String>()
            listed.sortedWith(compareBy<Node> { !it.directory }.thenBy { it.name.lowercase() }.thenBy { it.id }).forEach { node ->
                if(node.directory) {
                    var name=segment(node.name); val base=name; var suffix=2
                    while(!names.add(name.lowercase())) name="$base (${suffix++})"
                    val child="$path/$name"
                    if(child.length>BrushGroupOperations.MAX_PATH_LENGTH || BrushGroupOperations.depth(child)>32) errors += "$child: folder nesting exceeds the supported path length"
                    else pending.add(node to child)
                } else if(node.name.substringAfterLast('.',"").lowercase() in extensions) files += Source(node.id,node.name,path)
                else skipped++
            }
        }
        return Plan(rootName,groups,files,skipped,errors)
    }
    fun assign(source: Source, brush: ImportedBrush): ImportedBrush {
        val group=BrushGroupOperations.nest(source.group,brush.groupName)
        require(group.length == listOf(source.group,BrushGroupOperations.normalize(brush.groupName)).filter(String::isNotBlank).joinToString("/").length) { "Nested brush group exceeds path length: ${source.path}" }
        val identity="${brush.sha256}:${brush.tipPreviewPath}:${brush.name}:$group"
        return BrushGroupOperations.assign(brush,group).copy(id=UUID.nameUUIDFromBytes(identity.toByteArray(Charsets.UTF_8)).toString())
    }
    fun merge(existing: List<ImportedBrush>, additions: List<ImportedBrush>): List<ImportedBrush> {
        fun key(brush: ImportedBrush)="${brush.sha256 ?: brush.id}:${brush.tipPreviewPath}:${brush.name}:${brush.groupName.lowercase()}"
        val seen=existing.mapTo(mutableSetOf(),::key)
        return existing+additions.filter { seen.add(key(it)) }
    }
}
