package app.inkloom.ui

import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush

/** Captures the actual selected descriptor; return never substitutes a library lookup or color. */
internal data class DrawingToolReturnTarget(
    val tool: EditorTool,
    val brush: ImportedBrush?,
    val size: Float,
    val opacity: Float,
)
