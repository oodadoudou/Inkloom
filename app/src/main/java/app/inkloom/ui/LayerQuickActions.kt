package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.inkloom.document.*
import app.inkloom.ui.theme.*
import app.inkloom.settings.text

/** Transparency is visible even before a thumbnail bitmap is available. */
internal fun Modifier.layerCheckerboard() = drawBehind {
    val cell = 5.dp.toPx()
    val light = Color(0xFFE2E2E2)
    val dark = Color(0xFFAAAAAA)
    drawRect(light)
    for (y in 0..(size.height / cell).toInt()) for (x in 0..(size.width / cell).toInt()) {
        if ((x + y) % 2 == 0) drawRect(dark, Offset(x * cell, y * cell),
            Size(minOf(cell, size.width - x * cell), minOf(cell, size.height - y * cell)))
    }
}

/** Compact, fixed controls act on the primary layer; secondary selection never retargets paint. */
@Composable
internal fun LayerQuickActions(
    layer: InkLayer?, layers: List<InkLayer>, language: app.inkloom.settings.UiLanguage, onAdd: () -> Unit, onFolder: () -> Unit,
    onAdjustment: (LayerAdjustmentKind) -> Unit,
    onLock: () -> Unit, onAlphaLock: () -> Unit, onClip: () -> Unit,
    onReference: () -> Unit, onDraft: () -> Unit, onMask: (LayerMaskCommand) -> Unit,
    onDuplicate: () -> Unit, onMerge: () -> Unit, onDelete: () -> Unit,
    onGenerated: (GeneratedLayerKind) -> Unit = {},
    onVector: () -> Unit = {},
) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val content = layer?.isPaintSurfaceLayer() == true
    val editable = layer != null && layer.role != LayerRole.BACKGROUND && !LayerHierarchy.effectiveLocked(layers, layer)
    var maskOpen by remember(layer?.id) { mutableStateOf(false) }
    var adjustmentOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
        LayerQuickIcon(Icons.Outlined.ContentCut, tr("Toggle clipping", "切换剪贴"), editable && layer?.canClipToBelow() == true, layer?.clippedToBelow == true, onClip)
        LayerQuickIcon(Icons.Outlined.Lightbulb, tr("Toggle reference", "切换参考图层"), editable, layer?.reference == true, onReference)
        LayerQuickIcon(Icons.Outlined.EditNote, tr("Toggle draft", "切换草稿图层"), editable, layer?.draft == true, onDraft)
        LayerQuickIcon(Icons.Outlined.Lock, tr("Toggle layer lock", "切换图层锁定"), layer != null && layer.role != LayerRole.BACKGROUND, layer?.locked == true, onLock)
        LayerQuickIcon(Icons.Outlined.GridOn, tr("Toggle alpha lock", "切换透明度锁定"), editable && content, layer?.alphaLock == true, onAlphaLock)
    }
    Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
        LayerQuickIcon(Icons.Outlined.NoteAdd, tr("Add layer", "添加图层"), true, false, onAdd)
        Box {
            LayerQuickIcon(Icons.Outlined.Tune, tr("Add adjustment layer", "添加调整图层"), true, false) { adjustmentOpen = true }
            DropdownMenu(adjustmentOpen, { adjustmentOpen = false }) {
                LayerAdjustmentKind.entries.forEach { kind ->
                    StudioMenuItem(text = { Text(adjustmentName(kind, language)) },
                        onClick = { adjustmentOpen = false; onAdjustment(kind) })
                }
            }
        }
        Box {
            var generatedOpen by remember { mutableStateOf(false) }
            LayerQuickIcon(Icons.Outlined.FormatColorFill, tr("Add vector, fill, gradient or tone layer", "添加矢量、填充、渐变或网点图层"), true, false) { generatedOpen = true }
            DropdownMenu(generatedOpen, { generatedOpen = false }) {
                StudioMenuItem(text = { Text(tr("Vector layer", "矢量图层")) }, onClick = { generatedOpen = false; onVector() })
                listOf(GeneratedLayerKind.FILL to tr("Fill layer", "填充图层"), GeneratedLayerKind.GRADIENT to tr("Gradient layer", "渐变图层"),
                    GeneratedLayerKind.TONE to tr("Tone layer", "网点图层")).forEach { (kind, label) ->
                    StudioMenuItem(text = { Text(label) }, onClick = { generatedOpen = false; onGenerated(kind) })
                }
            }
        }
        LayerQuickIcon(Icons.Outlined.CreateNewFolder, tr("Add layer group", "添加图层组"), true, false, onFolder)
        LayerQuickIcon(Icons.Outlined.ContentCopy, tr("Duplicate active layer", "复制当前图层"), editable, false, onDuplicate)
        LayerQuickIcon(Icons.Outlined.MergeType, tr("Merge active layer down", "当前图层向下合并"), editable && content, false, onMerge)
        Box {
            LayerQuickIcon(Icons.Outlined.Brightness4, tr("Active layer mask", "当前图层蒙版"), editable, layer?.mask != null) { maskOpen = true }
            DropdownMenu(maskOpen, { maskOpen = false }) {
                layer?.let { LayerMaskMenuItems(it) { command -> maskOpen = false; onMask(command) } }
            }
        }
        LayerQuickIcon(Icons.Outlined.Delete, tr("Delete active layer", "删除当前图层"), editable && layer?.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER, LayerRole.ADJUSTMENT), false, onDelete)
    }
}

@Composable
private fun LayerQuickIcon(icon: ImageVector, description: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(32.dp).background(if (selected) SageContainer else Color.Transparent), enabled = enabled) {
        Icon(icon, description, Modifier.size(21.dp), tint = if (!enabled) MutedInk.copy(alpha = .35f) else if (selected) Ink else MutedInk)
    }
}
