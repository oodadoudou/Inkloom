package app.inkloom.settings

import app.inkloom.model.EditorTool

sealed interface ToolPaletteEntry {
    data class Tool(val tool: EditorTool) : ToolPaletteEntry
    data object Separator : ToolPaletteEntry
}

/** Durable CSP-style tool rail order. A separator creates a visual tool group. */
object ToolPaletteLayout {
    val defaultEntries: List<ToolPaletteEntry> = listOf(
        EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER,
        null,
        EditorTool.SELECT, EditorTool.EYEDROPPER, EditorTool.FILL, EditorTool.GRADIENT,
        EditorTool.SMUDGE, EditorTool.LIQUIFY, EditorTool.CORRECT_LINE,
        null,
        EditorTool.SHAPE, EditorTool.TEXT, EditorTool.NOTE, EditorTool.LINK,
        EditorTool.IMAGE, EditorTool.LASER,
    ).map { it?.let(ToolPaletteEntry::Tool) ?: ToolPaletteEntry.Separator }

    fun decode(raw: String?): List<ToolPaletteEntry> {
        val tokens = raw.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        if (tokens.isEmpty()) return defaultEntries
        val parsed = tokens.mapNotNull { token ->
            if (token == "|") ToolPaletteEntry.Separator
            else runCatching { ToolPaletteEntry.Tool(EditorTool.valueOf(token)) }.getOrNull()
        }
        if (parsed.filterIsInstance<ToolPaletteEntry.Tool>().map { it.tool }.toSet() != EditorTool.entries.toSet()) return defaultEntries
        val normalized = normalize(parsed)
        return normalized
    }

    fun encode(entries: List<ToolPaletteEntry>): String = normalize(entries).joinToString(",") {
        when (it) { is ToolPaletteEntry.Tool -> it.tool.name; ToolPaletteEntry.Separator -> "|" }
    }

    fun move(entries: List<ToolPaletteEntry>, from: Int, to: Int): List<ToolPaletteEntry> {
        if (from !in entries.indices || to !in 0..entries.size || from == to || from + 1 == to) return normalize(entries)
        val moved = entries.toMutableList()
        val item = moved.removeAt(from)
        moved.add((if (to > from) to - 1 else to).coerceIn(0, moved.size), item)
        return normalize(moved)
    }

    fun addSeparator(entries: List<ToolPaletteEntry>, index: Int = entries.size): List<ToolPaletteEntry> =
        normalize(entries.toMutableList().apply { add(index.coerceIn(0, size), ToolPaletteEntry.Separator) })

    fun removeSeparator(entries: List<ToolPaletteEntry>, index: Int): List<ToolPaletteEntry> =
        if (entries.getOrNull(index) != ToolPaletteEntry.Separator) normalize(entries)
        else normalize(entries.toMutableList().apply { removeAt(index) })

    fun normalize(entries: List<ToolPaletteEntry>): List<ToolPaletteEntry> {
        val seen = mutableSetOf<EditorTool>()
        val output = mutableListOf<ToolPaletteEntry>()
        entries.forEach { entry ->
            when (entry) {
                is ToolPaletteEntry.Tool -> if (seen.add(entry.tool)) output += entry
                ToolPaletteEntry.Separator -> if (output.lastOrNull() is ToolPaletteEntry.Tool) output += entry
            }
        }
        EditorTool.entries.filter(seen::add).forEach { output += ToolPaletteEntry.Tool(it) }
        while (output.lastOrNull() == ToolPaletteEntry.Separator) output.removeAt(output.lastIndex)
        return output
    }
}
