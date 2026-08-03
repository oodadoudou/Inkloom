package app.inkloom.settings

import app.inkloom.model.ImportedBrush

enum class ShortcutBrushGroup { PEN, PENCIL, BRUSH }

data class ShortcutBrushGroupTargets(
    val pen: String = "",
    val pencil: String = "",
    val brush: String = "",
) {
    fun forGroup(group: ShortcutBrushGroup): String = when (group) {
        ShortcutBrushGroup.PEN -> pen
        ShortcutBrushGroup.PENCIL -> pencil
        ShortcutBrushGroup.BRUSH -> brush
    }

    fun withGroup(group: ShortcutBrushGroup, path: String): ShortcutBrushGroupTargets = when (group) {
        ShortcutBrushGroup.PEN -> copy(pen = path)
        ShortcutBrushGroup.PENCIL -> copy(pencil = path)
        ShortcutBrushGroup.BRUSH -> copy(brush = path)
    }.normalized()

    fun normalized(): ShortcutBrushGroupTargets = copy(
        pen = normalizeGroupPath(pen),
        pencil = normalizeGroupPath(pencil),
        brush = normalizeGroupPath(brush),
    )

    private fun normalizeGroupPath(value: String): String = value.trim().trim('/').split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("/")
        .take(MAX_GROUP_PATH_LENGTH)

    private companion object {
        const val MAX_GROUP_PATH_LENGTH = 240
    }
}

object ShortcutBrushSelection {
    private val penTerms = listOf("pen", "钢笔")
    private val pencilTerms = listOf("pencil", "铅笔")
    private val brushTerms = listOf("brush", "brushes", "画笔", "笔刷")

    fun matches(brush: ImportedBrush?, group: ShortcutBrushGroup): Boolean {
        if (brush == null) return false
        val searchable = "${brush.groupName} ${brush.name}".lowercase()
        val terms = when (group) {
            ShortcutBrushGroup.PEN -> penTerms
            ShortcutBrushGroup.PENCIL -> pencilTerms
            ShortcutBrushGroup.BRUSH -> brushTerms
        }
        return terms.any(searchable::contains)
    }

    fun preferred(brushes: List<ImportedBrush>, group: ShortcutBrushGroup, targetPath: String = ""): ImportedBrush? {
        val normalizedTarget = ShortcutBrushGroupTargets().withGroup(group, targetPath).forGroup(group)
        if (normalizedTarget.isNotEmpty()) {
            brushes.asSequence()
                .filterNot(ImportedBrush::hidden)
                .filter { belongsToGroup(it, normalizedTarget) }
                .maxWithOrNull(compareBy<ImportedBrush> { it.lastUsedAt }.thenBy { it.name })
                ?.let { return it }
        }
        return brushes.asSequence()
            .filterNot(ImportedBrush::hidden)
            .filter { matches(it, group) }
            .maxWithOrNull(compareBy<ImportedBrush> { it.lastUsedAt }.thenBy { it.name })
    }

    private fun belongsToGroup(brush: ImportedBrush, targetPath: String): Boolean {
        val path = brush.groupName.trim().trim('/')
        return path.equals(targetPath, ignoreCase = true) || path.startsWith("$targetPath/", ignoreCase = true)
    }

    fun adjacent(brushes: List<ImportedBrush>, currentId: String?, delta: Int): ImportedBrush? {
        val visible = brushes.filterNot(ImportedBrush::hidden)
            .sortedWith(compareBy<ImportedBrush> { it.sortOrder }.thenByDescending { it.favorite }.thenByDescending { it.lastUsedAt }.thenBy { it.name.lowercase() })
        if (visible.isEmpty()) return null
        val current = visible.firstOrNull { it.id == currentId }
        val peers = current?.groupName?.trim()?.takeIf(String::isNotEmpty)?.let { currentGroup ->
            visible.filter { it.groupName.trim().equals(currentGroup, ignoreCase = true) }.takeIf(List<ImportedBrush>::isNotEmpty)
        } ?: visible
        val currentIndex = peers.indexOfFirst { it.id == currentId }
        if (currentIndex < 0) return if (delta < 0) peers.last() else peers.first()
        return peers[Math.floorMod(currentIndex + delta, peers.size)]
    }
}
