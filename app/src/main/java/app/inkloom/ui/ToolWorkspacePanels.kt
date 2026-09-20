package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.FillSubTool
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.text
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.MutedInk
import app.inkloom.ui.theme.Paper

/** CSP-style subtool palette: one current tool, its executable variants, and no duplicate editor state. */
@Composable
internal fun WorkspaceSubtoolsPanel(
    tool: EditorTool,
    brushes: List<ImportedBrush>,
    selectedBrushId: String?,
    settings: AppSettings,
    onBrush: (ImportedBrush) -> Unit,
    onSelectionShape: (SelectionShape) -> Unit,
    onFillSubTool: (FillSubTool) -> Unit,
    onGradientKind: (GradientKind) -> Unit,
    onCorrectLineMode: (CorrectLineMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = settings.language
    val brushTool = tool in setOf(EditorTool.PEN, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)
    val variants = remember(brushes, tool) {
        if (brushTool) brushes.filter { !it.hidden && BrushRoleClassifier.editorTool(it) == tool }
        else emptyList()
    }
    Column(modifier.fillMaxSize().background(Paper)) {
        HorizontalDivider(color = Color(0xFF595959))
        LazyColumn(contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (brushTool) {
                items(variants, key = ImportedBrush::id) { brush ->
                    val selected = brush.id == selectedBrushId
                    Row(Modifier.fillMaxWidth().background(if (selected) Color(0xFF5A5A5A) else Color.Transparent,
                        RoundedCornerShape(5.dp)).clickable { onBrush(brush) }.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BrushStrokePreview(brush, language, Modifier.size(width = 74.dp, height = 27.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(brush.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, lineHeight = 15.sp, color = if (selected) Ink else MutedInk)
                    }
                }
            } else if (tool == EditorTool.SELECT) {
                items(SelectionShape.entries) { shape ->
                    WorkspaceSubtoolRow(selectionShapeLabel(shape, settings), shape == settings.selectionShape) { onSelectionShape(shape) }
                }
            } else if (tool == EditorTool.FILL) {
                items(FillSubTool.entries) { subtool ->
                    WorkspaceSubtoolRow(fillSubtoolLabel(subtool, settings), subtool == settings.fillSubTool) { onFillSubTool(subtool) }
                }
            } else if (tool == EditorTool.GRADIENT) {
                items(GradientKind.entries) { kind ->
                    WorkspaceSubtoolRow(gradientKindLabel(kind, settings), kind == settings.gradientKind) { onGradientKind(kind) }
                }
            } else if (tool == EditorTool.CORRECT_LINE) {
                items(CorrectLineMode.entries) { mode ->
                    WorkspaceSubtoolRow(correctLineModeLabel(mode, settings), mode == settings.correctLineMode) { onCorrectLineMode(mode) }
                }
            } else {
                item {
                    Text(language.text("This tool has no subtool variants.", "此工具没有子工具变体。"),
                        Modifier.padding(12.dp), color = MutedInk, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSubtoolRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().background(if (selected) Color(0xFF5A5A5A) else Color.Transparent,
        RoundedCornerShape(5.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        color = if (selected) Ink else MutedInk, fontSize = 12.sp)
}

private fun selectionShapeLabel(shape: SelectionShape, settings: AppSettings): String = when (shape) {
    SelectionShape.FREEFORM -> settings.language.text("Freeform", "自由形")
    SelectionShape.RECTANGLE -> settings.language.text("Rectangle", "矩形")
    SelectionShape.ELLIPSE -> settings.language.text("Ellipse", "椭圆")
    SelectionShape.BRUSH -> settings.language.text("Selection brush", "选区画笔")
    SelectionShape.SMART_BORDER -> settings.language.text("Smart border", "智能边框")
    SelectionShape.MAGIC_WAND -> settings.language.text("Magic wand", "魔棒")
}

private fun fillSubtoolLabel(subtool: FillSubTool, settings: AppSettings): String = when (subtool) {
    FillSubTool.BUCKET -> settings.language.text("Bucket", "油漆桶")
    FillSubTool.ENCLOSE_AND_FILL -> settings.language.text("Enclose and fill", "包围并填充")
    FillSubTool.LASSO_FILL -> settings.language.text("Lasso fill", "套索填充")
    FillSubTool.LEFTOVER_PEN -> settings.language.text("Leftover pen", "漏涂笔")
}

private fun gradientKindLabel(kind: GradientKind, settings: AppSettings): String = when (kind) {
    GradientKind.LINEAR -> settings.language.text("Linear", "线性")
    GradientKind.RADIAL -> settings.language.text("Radial", "圆形")
    GradientKind.ELLIPSE -> settings.language.text("Ellipse", "椭圆")
    GradientKind.ANGULAR -> settings.language.text("Angular", "角度")
    GradientKind.REFLECTED -> settings.language.text("Reflected", "对称")
    GradientKind.DIAMOND -> settings.language.text("Diamond", "菱形")
}

private fun correctLineModeLabel(mode: CorrectLineMode, settings: AppSettings): String = when (mode) {
    CorrectLineMode.SMOOTH -> settings.language.text("Smooth", "平滑")
    CorrectLineMode.SIMPLIFY -> settings.language.text("Simplify", "简化")
    CorrectLineMode.ADJUST_WIDTH -> settings.language.text("Width", "线宽")
    CorrectLineMode.REDRAW_WIDTH -> settings.language.text("Redraw width", "重绘线宽")
    CorrectLineMode.CONNECT -> settings.language.text("Connect", "连接")
    CorrectLineMode.CONTROL_POINTS -> settings.language.text("Control points", "控制点")
}

/** Current tool controls follow the selected tool while their panel can dock independently. */
@Composable
internal fun WorkspaceToolPropertiesPanel(
    tool: EditorTool,
    brushSize: Float,
    brushOpacity: Float,
    settings: AppSettings,
    onBrushSize: (Float) -> Unit,
    onBrushOpacity: (Float) -> Unit,
    onSettings: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = settings.language
    Column(modifier.fillMaxSize().background(Paper)) {
        HorizontalDivider(color = Color(0xFF595959))
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            when (tool) {
                EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY -> {
                    WorkspacePropertySlider(language.text("Size", "大小"), "${brushSize.toInt()} px",
                        brushSize.coerceIn(1f, 500f), 1f..500f, onBrushSize)
                    WorkspacePropertySlider(language.text("Opacity", "不透明度"), "${(brushOpacity * 100).toInt()}%",
                        brushOpacity.coerceIn(0f, 1f), 0f..1f, onBrushOpacity)
                }
                EditorTool.SELECT -> {
                    if (settings.selectionShape == SelectionShape.BRUSH) WorkspacePropertySlider(
                        language.text("Brush size", "画笔大小"), "${settings.selectionBrushSize.toInt()} px",
                        settings.selectionBrushSize, 4f..300f, { onSettings(settings.copy(selectionBrushSize = it)) })
                    if (settings.selectionShape in setOf(SelectionShape.SMART_BORDER, SelectionShape.MAGIC_WAND)) WorkspacePropertySlider(
                        language.text("Threshold", "识别阈值"), "${(settings.selectionThreshold * 100).toInt()}%",
                        settings.selectionThreshold, 0f..1f, { onSettings(settings.copy(selectionThreshold = it)) })
                }
                EditorTool.FILL -> {
                    WorkspacePropertySlider(language.text("Tolerance", "容差"), "${(settings.fillSensitivity * 100).toInt()}%",
                        settings.fillSensitivity, 0f..1f, { onSettings(settings.copy(fillSensitivity = it)) })
                    WorkspacePropertySlider(language.text("Close gap", "闭合间隙"), "${settings.fillCloseGapPixels.toInt()} px",
                        settings.fillCloseGapPixels, 0f..64f, { onSettings(settings.copy(fillCloseGapPixels = it)) })
                    WorkspacePropertySlider(language.text("Area scaling", "区域缩放"), "${settings.fillAreaScalingPixels.toInt()} px",
                        settings.fillAreaScalingPixels, -64f..64f, { onSettings(settings.copy(fillAreaScalingPixels = it)) })
                }
                EditorTool.GRADIENT -> {
                    Text(language.text("Edge process", "边缘处理"), color = Ink, fontSize = 11.sp)
                    GradientEdgeMode.entries.forEach { edge ->
                        WorkspaceSubtoolRow(when (edge) {
                            GradientEdgeMode.CLAMP -> language.text("No repeat", "不重复")
                            GradientEdgeMode.REPEAT -> language.text("Repeat", "重复")
                            GradientEdgeMode.MIRROR -> language.text("Reverse repeat", "反向重复")
                            GradientEdgeMode.TRANSPARENT -> language.text("Do not draw", "不绘制")
                        }, edge == settings.gradientEdgeMode) { onSettings(settings.copy(gradientEdgeMode = edge)) }
                    }
                    WorkspacePropertySwitch(language.text("Reverse", "反转"), settings.gradientReverse) {
                        onSettings(settings.copy(gradientReverse = it))
                    }
                    WorkspacePropertySwitch(language.text("Dither", "抖动"), settings.gradientDither,
                        Modifier.testTag("workspace-gradient-dither")) {
                        onSettings(settings.copy(gradientDither = it))
                    }
                    WorkspacePropertySwitch(language.text("Angle snap", "角度吸附"), settings.gradientAngleSnapEnabled) {
                        onSettings(settings.copy(gradientAngleSnapEnabled = it))
                    }
                    if (settings.gradientKind == GradientKind.ELLIPSE) WorkspacePropertySlider(
                        language.text("Ellipse thickness", "椭圆厚度"), "${(settings.gradientEllipseRatio * 100).toInt()}%",
                        settings.gradientEllipseRatio, .1f..1f, { onSettings(settings.copy(gradientEllipseRatio = it)) })
                }
                EditorTool.CORRECT_LINE -> WorkspacePropertySlider(
                    language.text("Correction strength", "修正强度"), "${(settings.correctLineAmount * 100).toInt()}%",
                    settings.correctLineAmount, 0f..1f, { onSettings(settings.copy(correctLineAmount = it)) })
                else -> Text(language.text("Select a drawing tool to adjust its properties.", "选择绘画工具后调整其属性。"),
                    Modifier.padding(vertical = 8.dp), color = MutedInk, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WorkspacePropertySwitch(label: String, checked: Boolean, modifier: Modifier = Modifier,
                                    onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink, fontSize = 11.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
    }
}

@Composable
private fun WorkspacePropertySlider(label: String, valueLabel: String, value: Float,
                                    range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Ink, fontSize = 11.sp)
        Text(valueLabel, color = MutedInk, fontSize = 11.sp)
    }
    BrushParameterSlider(value, onValue, valueRange = range)
}
