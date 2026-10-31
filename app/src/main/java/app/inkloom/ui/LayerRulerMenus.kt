package app.inkloom.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
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
import app.inkloom.document.InkLayer
import app.inkloom.document.LayerRulers
import app.inkloom.document.PerspectiveRuler
import app.inkloom.document.RulerGeometry
import app.inkloom.document.RulerRange
import app.inkloom.document.RulerShape
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/** CSP's Layer > Ruler/Frame commands, for one layer. */
internal enum class LayerRulerAction {
    TOGGLE_SHOW, RANGE_EDITING_TARGET, RANGE_SAME_FOLDER, RANGE_ALL_LAYERS, TOGGLE_LINK_LAYER, TOGGLE_LINK_GUIDES,
    SELECTION, DRAW_ALONG, FROM_VECTOR, CREATE_PERSPECTIVE, PERSPECTIVE_SETTINGS, DELETE,
}

/** The ruler commands available for [layer], as menu items. */
@Composable
internal fun LayerRulerMenuItems(layer: InkLayer, language: UiLanguage, onAction: (LayerRulerAction) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val rulers = layer.rulers?.takeUnless { it.isEmpty }
    @Composable fun item(label: String, action: LayerRulerAction, checked: Boolean? = null, enabled: Boolean = true) =
        StudioMenuItem(text = { Text(label) }, enabled = enabled, onClick = { onAction(action) },
            trailingIcon = if (checked == true) ({ Icon(Icons.Outlined.Check, null) }) else null)
    if (rulers != null) {
        item(tr("Show ruler", "显示尺规"), LayerRulerAction.TOGGLE_SHOW, rulers.visible)
        item(tr("Show only when editing target", "仅在编辑对象时显示"), LayerRulerAction.RANGE_EDITING_TARGET, rulers.range == RulerRange.EDITING_TARGET)
        item(tr("Show in same folder", "在同一文件夹中显示"), LayerRulerAction.RANGE_SAME_FOLDER, rulers.range == RulerRange.SAME_FOLDER)
        item(tr("Show in all layers", "在所有图层中显示"), LayerRulerAction.RANGE_ALL_LAYERS, rulers.range == RulerRange.ALL_LAYERS)
        item(tr("Link ruler to layer", "尺规链接到图层"), LayerRulerAction.TOGGLE_LINK_LAYER, rulers.linkedToLayer)
        item(tr("Link guides to ruler", "参考线链接到尺规"), LayerRulerAction.TOGGLE_LINK_GUIDES, rulers.linkGuides)
        item(tr("Selection from ruler", "从尺规创建选区"), LayerRulerAction.SELECTION,
            enabled = rulers.shapes.any { it is RulerShape.Path && it.closed })
        item(tr("Draw along ruler…", "沿尺规描绘…"), LayerRulerAction.DRAW_ALONG)
        if (rulers.perspective != null) item(tr("Perspective ruler settings…", "透视尺规设置…"), LayerRulerAction.PERSPECTIVE_SETTINGS)
        item(tr("Delete ruler", "删除尺规"), LayerRulerAction.DELETE)
    }
    if (layer.vector?.strokes?.isNotEmpty() == true) item(tr("Ruler from vector", "从矢量创建尺规"), LayerRulerAction.FROM_VECTOR)
    if (rulers?.perspective == null) item(tr("Create perspective ruler…", "创建透视尺规…"), LayerRulerAction.CREATE_PERSPECTIVE)
}

/**
 * Runs a ruler command on [layerId]. Returns the command when it needs a dialog, or null once it
 * has been carried out.
 */
internal fun performLayerRulerAction(view: InkCanvasView, layer: InkLayer, action: LayerRulerAction, pageWidth: Float, pageHeight: Float): LayerRulerAction? {
    fun change(update: (LayerRulers) -> LayerRulers?) = view.editLayerRulers(layer.id) { current -> update(current ?: LayerRulers()) }
    when (action) {
        LayerRulerAction.TOGGLE_SHOW -> change { it.copy(visible = !it.visible) }
        LayerRulerAction.RANGE_EDITING_TARGET -> change { it.copy(range = RulerRange.EDITING_TARGET) }
        LayerRulerAction.RANGE_SAME_FOLDER -> change { it.copy(range = RulerRange.SAME_FOLDER) }
        LayerRulerAction.RANGE_ALL_LAYERS -> change { it.copy(range = RulerRange.ALL_LAYERS) }
        LayerRulerAction.TOGGLE_LINK_LAYER -> change { it.copy(linkedToLayer = !it.linkedToLayer) }
        LayerRulerAction.TOGGLE_LINK_GUIDES -> change { it.copy(linkGuides = !it.linkGuides) }
        LayerRulerAction.SELECTION -> view.selectFromRulers(layer.id)
        LayerRulerAction.FROM_VECTOR -> layer.vector?.let { content ->
            change { it.copy(shapes = it.shapes + RulerGeometry.fromVector(content, pageWidth, pageHeight), visible = true) }
        }
        LayerRulerAction.DELETE -> view.editLayerRulers(layer.id) { null }
        LayerRulerAction.DRAW_ALONG, LayerRulerAction.CREATE_PERSPECTIVE, LayerRulerAction.PERSPECTIVE_SETTINGS -> return action
    }
    return null
}

/** The dialogs of Draw along ruler, Create perspective ruler and the perspective ruler's settings. */
@Composable
internal fun LayerRulerDialog(action: LayerRulerAction, layer: InkLayer, view: InkCanvasView, language: UiLanguage, brushSize: Float,
                              onCreateOnNewLayer: (LayerRulers) -> Unit, onDismiss: () -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    when (action) {
        LayerRulerAction.DRAW_ALONG -> {
            var width by remember { mutableFloatStateOf(brushSize.coerceIn(1f, 200f)) }
            var antiAlias by remember { mutableStateOf(true) }
            StudioDialog(onDismissRequest = onDismiss, title = { Text(tr("Draw along ruler", "沿尺规描绘")) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StudioSliderRow(tr("Line width", "线宽"), width, 1f..200f, { width = it }, unit = "px")
                    StudioSegmented(listOf(tr("None", "无"), tr("On", "开")), if (antiAlias) 1 else 0, { antiAlias = it == 1 }, label = tr("Anti-aliasing", "抗锯齿"))
                }
            }, confirmButton = { TextButton({
                if (!view.drawAlongRulers(width, antiAlias)) Toast.makeText(context, tr("Nothing was drawn", "没有描绘任何内容"), Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "取消")) } })
        }
        LayerRulerAction.CREATE_PERSPECTIVE -> {
            var points by remember { mutableIntStateOf(2) }
            var newLayer by remember { mutableStateOf(false) }
            StudioDialog(onDismissRequest = onDismiss, title = { Text(tr("Create perspective ruler", "创建透视尺规")) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StudioSegmented(listOf(tr("1-point", "一点"), tr("2-point", "两点"), tr("3-point", "三点")), points - 1, { points = it + 1 },
                        label = tr("Type", "类型"))
                    StudioCheckRow(tr("Create on a new layer", "在新图层上创建"), newLayer, { newLayer = it })
                }
            }, confirmButton = { TextButton({
                val rulers = LayerRulers(perspective = PerspectiveRuler.create(points))
                if (newLayer) onCreateOnNewLayer(rulers)
                else view.editLayerRulers(layer.id) { (it ?: LayerRulers()).copy(perspective = rulers.perspective, visible = true) }
                onDismiss()
            }) { Text(tr("OK", "确定")) } }, dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "取消")) } })
        }
        LayerRulerAction.PERSPECTIVE_SETTINGS -> {
            val perspective = layer.rulers?.perspective ?: run { onDismiss(); return }
            fun update(change: (PerspectiveRuler) -> PerspectiveRuler?) =
                view.editLayerRulers(layer.id) { current -> current?.copy(perspective = current.perspective?.let(change)) }
            StudioDialog(onDismissRequest = onDismiss, placement = StudioDialogPlacement.SIDE, title = { Text(tr("Perspective ruler", "透视尺规")) }, text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (index in 0 until perspective.count) {
                        StudioSection(tr("Vanishing point ${index + 1}", "消失点 ${index + 1}"))
                        StudioCheckRow(tr("Snap", "吸附"), perspective.snap[index], { on -> update { it.copy(snap = it.snap.toMutableList().apply { this[index] = on }) } })
                        StudioCheckRow(tr("Fix point", "固定消失点"), perspective.fixed[index], { on -> update { it.copy(fixed = it.fixed.toMutableList().apply { this[index] = on }) } })
                    }
                    StudioSection(tr("Display", "显示"))
                    StudioCheckRow(tr("Grid", "网格"), perspective.grid, { on -> update { it.copy(grid = on) } })
                    StudioButton(tr("Delete perspective ruler", "删除透视尺规"), { update { null }; onDismiss() })
                }
            }, confirmButton = { TextButton(onDismiss) { Text(tr("Done", "完成")) } })
        }
        else -> onDismiss()
    }
}
