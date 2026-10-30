package app.inkloom.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.inkloom.document.PageElement
import app.inkloom.settings.AppSettings
import app.inkloom.settings.SelectionShape

/**
 * Editor features that palettes and toolbars reach through a composition local provided by the app
 * shell, so the editor's own parameter list stays within what the Android runtime verifier accepts.
 */
internal class EditorBridge(
    private val settings: () -> AppSettings,
    private val updateSettings: (AppSettings) -> Unit,
    /** Page width and height in page units, for sizing a text object. */
    val pageSize: () -> Pair<Float, Float>?,
    val insertText: (PageElement.Text) -> Unit,
) {
    /** The editor's canvas, registered by the canvas host when it is created. */
    var canvas: InkCanvasView? = null

    val selectionShape: SelectionShape get() = settings().selectionShape

    fun setSelectionShape(shape: SelectionShape) {
        canvas?.beginSelectionInput(shape)
        updateSettings(settings().copy(selectionShape = shape))
    }

    /** Magic wand: only the touched, connected area (on), or every pixel of that colour (off). */
    val selectionContiguous: Boolean get() = settings().selectionContiguous

    fun setSelectionContiguous(contiguous: Boolean) = updateSettings(settings().copy(selectionContiguous = contiguous))

    /** Magic wand Close gap level: 0 off, 1-3 small to large. */
    val selectionCloseGapLevel: Int get() = settings().selectionCloseGapLevel

    fun cycleSelectionCloseGap() = updateSettings(settings().copy(selectionCloseGapLevel = (settings().selectionCloseGapLevel + 1) % 4))
}

internal val LocalEditorBridge = staticCompositionLocalOf<EditorBridge?> { null }
