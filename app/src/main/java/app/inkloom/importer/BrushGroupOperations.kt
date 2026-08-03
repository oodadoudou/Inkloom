package app.inkloom.importer

import app.inkloom.model.ImportedBrush
import java.util.UUID

object BrushGroupOperations {
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String = value.trim().replace(whitespace, " ").take(MAX_NAME_LENGTH)

    fun names(brushes: List<ImportedBrush>): List<String> = brushes
        .map { normalize(it.groupName) }
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)

    fun assign(brush: ImportedBrush, groupName: String): ImportedBrush = brush.copy(groupName = normalize(groupName))

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
        return brushes.map { brush -> if (normalize(brush.groupName).equals(old, ignoreCase = true)) brush.copy(groupName = replacement) else brush }
    }

    fun move(brushes: List<ImportedBrush>, brushId: String, delta: Int): List<ImportedBrush> {
        if (delta == 0) return brushes
        val moving = brushes.firstOrNull { it.id == brushId } ?: return brushes
        val group = normalize(moving.groupName)
        val peers = brushes.filter { normalize(it.groupName).equals(group, ignoreCase = true) }
            .sortedWith(compareBy<ImportedBrush> { it.sortOrder }.thenByDescending { it.favorite }.thenByDescending { it.lastUsedAt }.thenBy { it.name.lowercase() })
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
        val groups = names(brushes).toMutableList()
        val from = groups.indexOfFirst { it.equals(normalize(groupName), ignoreCase = true) }
        if (from < 0) return brushes
        val to = (from + delta).coerceIn(0, groups.lastIndex)
        if (from == to) return brushes
        groups.add(to, groups.removeAt(from))
        val order = groups.mapIndexed { index, group -> group.lowercase() to index }.toMap()
        return brushes.withIndex().sortedWith(
            compareBy<IndexedValue<ImportedBrush>> { indexed ->
                normalize(indexed.value.groupName).takeIf(String::isNotBlank)?.lowercase()?.let(order::get) ?: Int.MAX_VALUE
            }.thenBy { it.index },
        ).map(IndexedValue<ImportedBrush>::value)
    }

    const val MAX_NAME_LENGTH = 40
}
