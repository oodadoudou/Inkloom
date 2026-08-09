package app.inkloom.importer

import app.inkloom.model.ImportedBrush
import app.inkloom.model.EditorTool

object BrushRoleClassifier {
    fun isEraser(brush: ImportedBrush?): Boolean {
        if (brush == null) return false
        val searchable = "${brush.name} ${brush.groupName}".lowercase()
        return brush.isEraser || listOf("eraser", "橡皮", "消しゴム").any(searchable::contains)
    }

    fun isBlend(brush: ImportedBrush?): Boolean = hasRole(brush, "blend", "smudge", "混色", "涂抹")

    fun isLiquify(brush: ImportedBrush?): Boolean = hasRole(brush, "liquify", "液化")

    fun editorTool(brush: ImportedBrush?): EditorTool = when {
        isEraser(brush) -> EditorTool.ERASER
        isBlend(brush) -> EditorTool.SMUDGE
        isLiquify(brush) -> EditorTool.LIQUIFY
        else -> EditorTool.PEN
    }

    private fun hasRole(brush: ImportedBrush?, vararg terms: String): Boolean {
        if (brush == null) return false
        val searchable = buildString {
            append(brush.name).append(' ').append(brush.groupName).append(' ')
            append(brush.tags.joinToString(" "))
        }.lowercase()
        return terms.any(searchable::contains)
    }
}
