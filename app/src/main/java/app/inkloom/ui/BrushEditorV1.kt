package app.inkloom.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.inkloom.brushpresetv4.BrushOptionDescriptor
import app.inkloom.brushpresetv4.BrushOptionDescriptors
import app.inkloom.brushpresetv4.BrushPresetV4
import app.inkloom.brushpresetv4.CompiledBrushProgram
import app.inkloom.brushpresetv4.EditorWorkingCopy
import app.inkloom.brushpresetv4.OwnedBrushPreset
import app.inkloom.brushpresetv4.PresetReverseMigrator
import app.inkloom.brushpresetv4.PressureCurveShape
import app.inkloom.brushpresetv4.ScratchPadTrajectories
import app.inkloom.document.LayerIds
import app.inkloom.model.EditorTool
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset

private fun brTr(language: String, english: String, simplifiedChinese: String): String =
    if (language.startsWith("zh")) simplifiedChinese else english

@Composable
private fun currentLanguage(): String = LocalConfiguration.current.locales[0].language

/**
 * Brush Editor V1 — the capability-driven editor over [BrushPresetV4].
 *
 * Editing flow:
 *
 *   Selected preset (factory or user)
 *     -> EditorWorkingCopy (source immutable until Save)
 *     -> BrushPresetV4 being edited
 *     -> compile() -> CompiledBrushProgram
 *     -> Scratch Pad (production renderer)
 *
 * Panels are derived purely from the family's capability set; no brush-name branching.
 * Save follows ownership: factory -> Save As (new user copy), user -> Save / Save As / Reset /
 * Delete. Cancel just drops the working copy.
 */
@Composable
fun BrushEditorV1Dialog(
    source: OwnedBrushPreset,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSaveAs: (BrushPresetV4) -> Unit,
    onSaveInPlace: (BrushPresetV4) -> Unit,
    onResetToFactory: () -> Unit,
    onDelete: () -> Unit,
) {
    var session by remember(source.id) {
        mutableStateOf(EditorWorkingCopy.start(source))
    }
    var showScratchPad by remember { mutableStateOf(true) }
    val lang = currentLanguage()

    val family = session.current.identity.family
    val panels = remember(family) { BrushOptionDescriptors.panels(family) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(session.current.identity.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    brTr(lang, "Brush Editor", "笔刷编辑器") + " · " + family.label +
                        if (source.isFactory) brTr(lang, " · Factory", " · 预设") else brTr(lang, " · User", " · 用户"),
                    color = Color.Gray, fontSize = 11.sp,
                )
            }
        },
        text = {
            Column(Modifier.widthIn(min = 420.dp, max = 560.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showScratchPad = !showScratchPad }) {
                        Text(if (showScratchPad) brTr(lang, "Hide test pad", "隐藏试笔板") else brTr(lang, "Show test pad", "显示试笔板"))
                    }
                }
                if (showScratchPad) {
                    V4ScratchPad(preset = session.current, settings = settings, lang = lang)
                }
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(panels) { (group, options) ->
                        CapabilityPanel(
                            groupName = group.name,
                            options = options,
                            value = session.current,
                            onValue = { updated -> session = session.withValue(updated) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!source.isFactory) {
                    TextButton(onClick = onDelete) { Text(brTr(lang, "Delete", "删除"), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = onResetToFactory) { Text(brTr(lang, "Reset", "重置")) }
                }
                TextButton(onClick = onDismiss) { Text(brTr(lang, "Cancel", "取消")) }
                TextButton(onClick = {
                    if (source.isFactory) {
                        val result = session.commit()
                        onSaveAs(result.preset)
                    } else {
                        onSaveInPlace(session.current)
                    }
                }) { Text(if (source.isFactory) brTr(lang, "Save As", "另存为") else brTr(lang, "Save", "保存")) }
            }
        },
    )
}

@Composable
private fun CapabilityPanel(
    groupName: String,
    options: List<BrushOptionDescriptor>,
    value: BrushPresetV4,
    onValue: (BrushPresetV4) -> Unit,
) {
    val lang = currentLanguage()
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0x0DFFFFFF))) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(trGroup(lang, groupName), color = Color(0xFF9AC8A0), fontSize = 11.sp)
            options.forEach { option ->
                BrushOptionRow(option, value, onValue)
            }
        }
    }
}

@Composable
private fun BrushOptionRow(
    option: BrushOptionDescriptor,
    value: BrushPresetV4,
    onValue: (BrushPresetV4) -> Unit,
) {
    val current = option.get(value)
    if (option.id.endsWith("CurveShape")) {
        val shapeIndex = current.toInt().coerceIn(0, PressureCurveShape.entries.lastIndex)
        Column(Modifier.fillMaxWidth()) {
            Text(option.label, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                PressureCurveShape.entries.forEach { shape ->
                    TextButton(
                        onClick = { onValue(option.apply(value, shape.ordinal.toFloat())) },
                        modifier = Modifier.height(28.dp),
                    ) {
                        Text(shape.label, fontSize = 10.sp, color = if (shape.ordinal == shapeIndex) Color(0xFF9AC8A0) else Color.Gray)
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(option.label, Modifier.weight(1f), fontSize = 12.sp)
            Text(
                if (option.id.endsWith("Enabled")) (if (current >= .5f) "ON" else "OFF")
                else "%.2f".format(current),
                color = Color.Gray, fontSize = 11.sp,
            )
        }
        Slider(
            value = current.coerceIn(option.valueRange.start, option.valueRange.endInclusive),
            onValueChange = { v -> onValue(option.apply(value, v)) },
            valueRange = option.valueRange,
        )
    }
}

/**
 * Scratch Pad — renders through the production renderer.
 *
 * Preset test trajectories go through BrushPresetV4 -> compile -> InkStroke ->
 * setDocumentStrokes on a real InkCanvasView, i.e. the exact same renderer the real canvas uses
 * (BrushPresetV4EditorCanvasParityTest proves the configured canvas fields equal the compiled
 * stroke's). Free drawing is driven by the canvas's own input path on the reverse-migrated
 * preset, so there is no editor-specific preview renderer.
 */
@Composable
private fun V4ScratchPad(
    preset: BrushPresetV4,
    settings: AppSettings,
    lang: String,
) {
    var trajectory by remember(preset.renderSeed) { mutableStateOf("straight") }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(brTr(lang, "SCRATCH PAD", "试笔板"), color = Color(0xFF9AC8A0), fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ScratchPadTrajectories.all.forEach { name ->
                TextButton(onClick = { trajectory = name }, modifier = Modifier.height(30.dp)) {
                    Text(trTrajectory(lang, name), fontSize = 11.sp)
                }
            }
        }
        AndroidView(
            factory = { ctx ->
                InkCanvasView(ctx).apply {
                    activeLayerId = LayerIds.INK
                    pageModelWidth = 900f
                    pageModelHeight = 240f
                }
            },
            update = { view ->
                view.activeLayerId = LayerIds.INK
                view.pageModelWidth = 900f
                view.pageModelHeight = 240f
                view.applyBrushConfiguration(
                    selectedTool = EditorTool.PEN,
                    settings = settings,
                    selectedBrush = PresetReverseMigrator.toV3(preset),
                    brushSize = preset.tip.size,
                    brushOpacity = preset.deposition.opacity,
                    brushColor = AndroidColor.rgb(17, 17, 17),
                    eraserPreset = EraserPreset.NORMAL_FREE,
                )
                val stroke = CompiledBrushProgram.compile(preset).toInkStroke(
                    0xFF111111L,
                    ScratchPadTrajectories.byName(trajectory),
                )
                view.setDocumentStrokes(listOf(stroke), "v4scratch:$trajectory:${preset.renderSeed}")
            },
            modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
        )
    }
}

private fun trTrajectory(lang: String, name: String): String = when (name) {
    "straight" -> brTr(lang, "Straight", "直线")
    "pressureRamp" -> brTr(lang, "Pressure Ramp", "压力渐变")
    "tiltRamp" -> brTr(lang, "Tilt Ramp", "侧锋渐变")
    "circle" -> brTr(lang, "Circle", "圆")
    "sCurve" -> brTr(lang, "S-Curve", "S 曲线")
    else -> name
}

private fun trGroup(lang: String, group: String): String = when (group) {
    "BASIC" -> brTr(lang, "Basic", "基础")
    "TIP" -> brTr(lang, "Tip", "笔尖")
    "SPACING" -> brTr(lang, "Spacing", "间距")
    "PRESSURE" -> brTr(lang, "Pressure", "压力")
    "TILT" -> brTr(lang, "Tilt", "侧锋")
    "PAPER" -> brTr(lang, "Paper", "纸张")
    "STROKE" -> brTr(lang, "Stroke", "笔画")
    else -> group
}
