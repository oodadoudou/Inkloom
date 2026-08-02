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

    const val MAX_NAME_LENGTH = 40
}
