package app.inkloom.importer

import app.inkloom.model.ImportedBrush

object BrushGroupOperations {
    private val whitespace = Regex("\\s+")

    fun normalize(value: String): String = value.trim().replace(whitespace, " ").take(MAX_NAME_LENGTH)

    fun names(brushes: List<ImportedBrush>): List<String> = brushes
        .map { normalize(it.groupName) }
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun assign(brush: ImportedBrush, groupName: String): ImportedBrush = brush.copy(groupName = normalize(groupName))

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

    const val MAX_NAME_LENGTH = 40
}
