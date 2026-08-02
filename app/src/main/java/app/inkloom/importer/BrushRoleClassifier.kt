package app.inkloom.importer

import app.inkloom.model.ImportedBrush

object BrushRoleClassifier {
    fun isEraser(brush: ImportedBrush?): Boolean {
        if (brush == null) return false
        val searchable = "${brush.name} ${brush.groupName}".lowercase()
        return brush.isEraser || listOf("eraser", "橡皮", "消しゴム").any(searchable::contains)
    }
}
