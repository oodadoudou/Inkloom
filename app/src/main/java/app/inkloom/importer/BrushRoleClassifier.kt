package app.inkloom.importer

import app.inkloom.model.BrushToolRole
import app.inkloom.model.ImportedBrush
import app.inkloom.model.EditorTool

/** Reads declared tool semantics; display metadata is never a dispatch input. */
object BrushRoleClassifier {
    fun isEraser(brush: ImportedBrush?): Boolean = brush?.toolBinding?.role == BrushToolRole.ERASE
    fun isBlend(brush: ImportedBrush?): Boolean = brush?.toolBinding?.role == BrushToolRole.SMUDGE
    fun isLiquify(brush: ImportedBrush?): Boolean = brush?.toolBinding?.role == BrushToolRole.LIQUIFY

    fun editorTool(brush: ImportedBrush?): EditorTool = when (brush?.toolBinding?.role) {
        BrushToolRole.ERASE -> EditorTool.ERASER
        BrushToolRole.SMUDGE -> EditorTool.SMUDGE
        BrushToolRole.LIQUIFY -> EditorTool.LIQUIFY
        // Unsupported records have no executable payload and are rejected at selection/contact.
        else -> EditorTool.PEN
    }
}
