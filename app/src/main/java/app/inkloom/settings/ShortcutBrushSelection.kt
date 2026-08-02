package app.inkloom.settings

import app.inkloom.model.ImportedBrush

enum class ShortcutBrushGroup { PEN, PENCIL, BRUSH }

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

    fun preferred(brushes: List<ImportedBrush>, group: ShortcutBrushGroup): ImportedBrush? = brushes
        .asSequence()
        .filterNot(ImportedBrush::hidden)
        .filter { matches(it, group) }
        .maxWithOrNull(compareBy<ImportedBrush> { it.lastUsedAt }.thenBy { it.name })

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
