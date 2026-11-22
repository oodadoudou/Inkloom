package app.inkloom.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.inkloom.document.VectorGeometry
import app.inkloom.document.VectorStroke
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/** Which of the active vector layer's lines an operation changes. */
internal enum class VectorLineScope { ALL, CROSSING_SELECTION, INSIDE_SELECTION }

/**
 * CSP-style vector line operations on the active vector layer: choose all lines, the lines
 * crossing the selection or those entirely inside it, then delete, recolour, re-width or copy
 * them as SVG. Every change is one undoable step.
 */
@Composable
internal fun VectorLinesDialog(view: InkCanvasView, language: UiLanguage, drawingColor: Int, pageWidth: Float, pageHeight: Float,
    eraseWholeLine: Boolean, onEraseWholeLine: (Boolean) -> Unit, onDismiss: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    var scope by remember { mutableStateOf(VectorLineScope.ALL) }
    var revision by remember { mutableIntStateOf(0) }
    var widthScale by remember { mutableFloatStateOf(1f) }
    val content = remember(revision) { view.vectorContent() }
    val chosen = remember(scope, revision) {
        when (scope) {
            VectorLineScope.ALL -> content?.strokes?.mapTo(linkedSetOf()) { it.id }.orEmpty()
            VectorLineScope.CROSSING_SELECTION -> view.selectedVectorIds(containedOnly = false).orEmpty()
            VectorLineScope.INSIDE_SELECTION -> view.selectedVectorIds(containedOnly = true).orEmpty()
        }
    }
    fun change(transform: (VectorStroke) -> VectorStroke?) {
        val changed = view.editVectorLayer { lines, _ -> lines.copy(strokes = lines.strokes.mapNotNull { if (it.id in chosen) transform(it) else it }) }
        if (changed) revision++
        else Toast.makeText(context, tr("Nothing changed; lift the pen and choose some lines", "没有变化；请抬笔并选择线条"), Toast.LENGTH_SHORT).show()
    }
    StudioDialog(onDismissRequest = onDismiss, placement = StudioDialogPlacement.SIDE, title = { Text(tr("Vector lines", "矢量线条")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (content == null) StudioNote(tr("Select a vector layer first.", "请先选择矢量图层。"))
            StudioSection(tr("Lines", "线条"))
            StudioSegmented(listOf(tr("All", "全部"), tr("Crossing selection", "与选区相交"), tr("Inside selection", "完全在选区内")),
                scope.ordinal, { scope = VectorLineScope.entries[it] })
            StudioNote(tr("${chosen.size} of ${content?.strokes?.size ?: 0} lines", "已选 ${chosen.size} / ${content?.strokes?.size ?: 0} 条线"))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioButton(tr("Delete", "删除"), { change { null } }, enabled = chosen.isNotEmpty())
                StudioButton(tr("Use drawing color", "使用描绘色"), { change { it.copy(colorArgb = drawingColor) } }, enabled = chosen.isNotEmpty())
                StudioButton(tr("Copy as SVG", "复制为 SVG"), {
                    val svg = VectorGeometry.svg(content?.strokes.orEmpty().filter { it.id in chosen }, pageWidth, pageHeight)
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SVG", svg))
                    Toast.makeText(context, tr("Copied ${chosen.size} lines as SVG", "已将 ${chosen.size} 条线复制为 SVG"), Toast.LENGTH_SHORT).show()
                }, enabled = chosen.isNotEmpty())
            }
            StudioSection(tr("Line width", "线宽"))
            StudioSliderRow(tr("Scale", "缩放"), widthScale, .1f..5f, { widthScale = it }, unit = "×")
            StudioButton(tr("Apply width", "应用线宽"), { change { it.copy(diameter = (it.diameter * widthScale).coerceIn(.01f, 2000f)) }; widthScale = 1f },
                enabled = chosen.isNotEmpty() && widthScale != 1f)
            StudioSection(tr("Vector eraser", "矢量橡皮擦"))
            StudioSegmented(listOf(tr("Touched area", "触碰部分"), tr("Whole line", "整条线")), if (eraseWholeLine) 1 else 0, { onEraseWholeLine(it == 1) })
        }
    }, confirmButton = { TextButton(onDismiss) { Text(tr("Done", "完成")) } })
}
