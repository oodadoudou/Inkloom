package app.inkloom.importer

import app.inkloom.model.ImportedBrush
import java.util.UUID

object BrushGroupOperations {
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String = value
        .split('/')
        .map { segment -> segment.trim().replace(whitespace, " ").take(MAX_NAME_LENGTH) }
        .filter(String::isNotBlank)
        .joinToString("/")
        .take(MAX_PATH_LENGTH)

    fun names(brushes: List<ImportedBrush>): List<String> = brushes
        .map { normalize(it.groupName) }
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)

    fun reconcileRegisteredGroups(registeredGroups: List<String>, brushes: List<ImportedBrush>): List<String> =
        (registeredGroups + names(brushes))
            .map(::normalize)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)

    fun treePaths(brushes: List<ImportedBrush>, registeredGroups: List<String> = emptyList()): List<String> {
        val discovered = buildList<String> {
            reconcileRegisteredGroups(registeredGroups, brushes).forEach { path ->
                val segments = path.split('/')
                segments.indices.forEach { index ->
                    val ancestor = segments.take(index + 1).joinToString("/")
                    if (none { it.equals(ancestor, ignoreCase = true) }) add(ancestor)
                }
            }
        }
        return buildList {
            fun appendSubtree(path: String) {
                add(path)
                discovered.filter { parent(it)?.equals(path, ignoreCase = true) == true }.forEach(::appendSubtree)
            }
            discovered.filter { parent(it) == null }.forEach(::appendSubtree)
        }
    }

    fun depth(groupName: String): Int = normalize(groupName).count { it == '/' }

    fun displayName(groupName: String): String = normalize(groupName).substringAfterLast('/')

    fun parent(groupName: String): String? = normalize(groupName).substringBeforeLast('/', "").takeIf(String::isNotBlank)

    fun isWithin(groupName: String, rootGroupName: String): Boolean {
        val group = normalize(groupName)
        val root = normalize(rootGroupName)
        return root.isNotBlank() && (group.equals(root, ignoreCase = true) || group.startsWith("$root/", ignoreCase = true))
    }

    fun assign(brush: ImportedBrush, groupName: String): ImportedBrush = brush.copy(groupName = normalize(groupName))

    fun nest(parentGroup: String, childGroup: String): String = listOf(parentGroup, childGroup)
        .map(::normalize)
        .filter(String::isNotBlank)
        .joinToString("/")
        .let(::normalize)

    fun assignMany(brushes: List<ImportedBrush>, brushIds: Set<String>, groupName: String): List<ImportedBrush> {
        if (brushIds.isEmpty()) return brushes
        val normalized = normalize(groupName)
        return brushes.map { brush -> if (brush.id in brushIds) brush.copy(groupName = normalized) else brush }
    }

    fun duplicateMany(
        brushes: List<ImportedBrush>,
        brushIds: Set<String>,
        groupName: String,
        copySuffix: String = "copy",
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): List<ImportedBrush> {
        if (brushIds.isEmpty()) return brushes
        val targetGroup = normalize(groupName)
        var nextOrder = brushes.filter { normalize(it.groupName).equals(targetGroup, ignoreCase = true) }.maxOfOrNull(ImportedBrush::sortOrder)?.plus(1) ?: 0
        val copies = brushes.filter { it.id in brushIds }.map { source ->
            source.copy(
                id = idFactory(),
                name = "${source.name} $copySuffix".trim(),
                groupName = targetGroup,
                favorite = false,
                hidden = false,
                lastUsedAt = 0L,
                sortOrder = nextOrder++,
            )
        }
        return brushes + copies
    }

    fun setFavorite(brushes: List<ImportedBrush>, brushIds: Set<String>, favorite: Boolean): List<ImportedBrush> =
        brushes.map { brush -> if (brush.id in brushIds) brush.copy(favorite = favorite) else brush }

    fun setHidden(brushes: List<ImportedBrush>, brushIds: Set<String>, hidden: Boolean): List<ImportedBrush> =
        brushes.map { brush -> if (brush.id in brushIds) brush.copy(hidden = hidden) else brush }

    fun removeMany(brushes: List<ImportedBrush>, brushIds: Set<String>): List<ImportedBrush> =
        brushes.filterNot { it.id in brushIds }

    fun duplicateGroup(
        brushes: List<ImportedBrush>,
        groupName: String,
        newGroupName: String,
        copySuffix: String = "copy",
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): List<ImportedBrush> {
        val source = normalize(groupName)
        val target = normalize(newGroupName)
        if (source.isBlank() || target.isBlank()) return brushes
        val sourceBrushes = brushes.filter { isWithin(it.groupName, source) }
        var nextOrder = brushes.filter { isWithin(it.groupName, target) }.maxOfOrNull(ImportedBrush::sortOrder)?.plus(1) ?: 0
        val copies = sourceBrushes.map { brush ->
            val suffix = normalize(brush.groupName).drop(source.length).trimStart('/')
            brush.copy(
                id = idFactory(),
                name = "${brush.name} $copySuffix".trim(),
                groupName = listOf(target, suffix).filter(String::isNotBlank).joinToString("/"),
                favorite = false,
                hidden = false,
                lastUsedAt = 0L,
                sortOrder = nextOrder++,
            )
        }
        return brushes + copies
    }

    fun deleteGroup(brushes: List<ImportedBrush>, groupName: String): List<ImportedBrush> {
        val source = normalize(groupName)
        return if (source.isBlank()) brushes else brushes.filterNot { isWithin(it.groupName, source) }
    }

    fun deleteRegisteredGroup(groups: List<String>, groupName: String): List<String> {
        val source = normalize(groupName)
        return if (source.isBlank()) groups else groups.map(::normalize).filterNot { isWithin(it, source) }
    }

    fun mergeImports(existing: List<ImportedBrush>, additions: List<ImportedBrush>): List<ImportedBrush> {
        val known = existing.mapTo(mutableSetOf(), ::importIdentity)
        return existing + additions.filter { known.add(importIdentity(it)) }
    }

    private fun importIdentity(brush: ImportedBrush): String {
        val sourceKey = brush.sha256 ?: "${brush.name}:${brush.sizeBytes}"
        return "$sourceKey:${brush.tipPreviewPath.orEmpty()}"
    }

    fun rename(brushes: List<ImportedBrush>, oldName: String, newName: String): List<ImportedBrush> {
        val old = normalize(oldName)
        val replacement = normalize(newName)
        if (old.isBlank()) return brushes
        return brushes.map { brush ->
            val current = normalize(brush.groupName)
            if (!isWithin(current, old)) brush else {
                val suffix = current.drop(old.length).trimStart('/')
                brush.copy(groupName = listOf(replacement, suffix).filter(String::isNotBlank).joinToString("/"))
            }
        }
    }

    fun renameRegisteredGroups(groups: List<String>, oldName: String, newName: String): List<String> {
        val old = normalize(oldName)
        val replacement = normalize(newName)
        if (old.isBlank()) return groups
        return groups.map { path ->
            val current = normalize(path)
            if (!isWithin(current, old)) current else {
                val suffix = current.drop(old.length).trimStart('/')
                listOf(replacement, suffix).filter(String::isNotBlank).joinToString("/")
            }
        }.filter(String::isNotBlank).distinctBy(String::lowercase)
    }

    fun moveRegisteredGroup(groups: List<String>, groupName: String, delta: Int): List<String> {
        if (delta == 0) return groups
        val normalizedGroups = groups.map(::normalize).filter(String::isNotBlank).distinctBy(String::lowercase)
        val selected = normalize(groupName)
        val selectedParent = parent(selected)
        val siblings = treePaths(emptyList(), normalizedGroups)
            .filter { parent(it)?.equals(selectedParent, ignoreCase = true) ?: (selectedParent == null && parent(it) == null) }
            .toMutableList()
        val from = siblings.indexOfFirst { it.equals(selected, ignoreCase = true) }
        if (from < 0) return normalizedGroups
        val to = (from + delta).coerceIn(0, siblings.lastIndex)
        if (from == to) return normalizedGroups
        siblings.add(to, siblings.removeAt(from))
        val matched = normalizedGroups.filter { path -> siblings.any { sibling -> isWithin(path, sibling) } }
        val reordered = siblings.flatMap { sibling -> matched.filter { path -> isWithin(path, sibling) } }.iterator()
        return normalizedGroups.map { path -> if (siblings.any { sibling -> isWithin(path, sibling) }) reordered.next() else path }
    }

    private val savedPeerOrder = compareBy<ImportedBrush> { it.sortOrder }
        .thenByDescending { it.favorite }
        .thenByDescending { it.lastUsedAt }
        .thenBy { it.name.lowercase() }

    /** Orders each peer group in its existing slots; unrelated group positions stay unchanged. */
    fun orderForDisplay(brushes: List<ImportedBrush>): List<ImportedBrush> {
        val peers = brushes.groupBy { normalize(it.groupName).lowercase() }
            .mapValues { (_, group) -> group.sortedWith(savedPeerOrder).iterator() }
        return brushes.map { peers.getValue(normalize(it.groupName).lowercase()).next() }
    }

    fun move(brushes: List<ImportedBrush>, brushId: String, delta: Int): List<ImportedBrush> {
        if (delta == 0) return brushes
        val moving = brushes.firstOrNull { it.id == brushId } ?: return brushes
        val group = normalize(moving.groupName)
        val peers = brushes.filter { normalize(it.groupName).equals(group, ignoreCase = true) }
            .sortedWith(savedPeerOrder)
            .toMutableList()
        val from = peers.indexOfFirst { it.id == brushId }
        val to = (from + delta).coerceIn(0, peers.lastIndex)
        if (from < 0 || from == to) return brushes
        peers.add(to, peers.removeAt(from))
        val order = peers.mapIndexed { index, brush -> brush.id to index }.toMap()
        return brushes.map { brush -> order[brush.id]?.let { brush.copy(sortOrder = it) } ?: brush }
    }

    fun moveGroup(brushes: List<ImportedBrush>, groupName: String, delta: Int): List<ImportedBrush> {
        if (delta == 0) return brushes
        val selected = normalize(groupName)
        val selectedParent = parent(selected)
        val groups = treePaths(brushes).filter { parent(it)?.equals(selectedParent, ignoreCase = true) ?: (selectedParent == null && parent(it) == null) }.toMutableList()
        val from = groups.indexOfFirst { it.equals(selected, ignoreCase = true) }
        if (from < 0) return brushes
        val to = (from + delta).coerceIn(0, groups.lastIndex)
        if (from == to) return brushes
        groups.add(to, groups.removeAt(from))
        val matched = brushes.filter { brush -> groups.any { group -> isWithin(brush.groupName, group) } }
        val reordered = groups.flatMap { group -> matched.filter { brush -> isWithin(brush.groupName, group) } }.iterator()
        return brushes.map { brush -> if (groups.any { group -> isWithin(brush.groupName, group) }) reordered.next() else brush }
    }

    const val MAX_NAME_LENGTH = 128
    const val MAX_PATH_LENGTH = 4096
}
