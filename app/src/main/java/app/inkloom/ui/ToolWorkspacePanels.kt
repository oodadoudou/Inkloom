package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.FillSubTool
import app.inkloom.document.CorrectLineMode
import app.inkloom.document.GradientEdgeMode
import app.inkloom.document.GradientKind
import app.inkloom.document.ShapeKind
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.model.EditorTool
import app.inkloom.model.ImportedBrush
import app.inkloom.settings.AppSettings
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.text
import app.inkloom.settings.ToolPaletteEntry
import app.inkloom.settings.ToolPaletteLayout
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
    onRulerToolMode: (app.inkloom.document.RulerToolMode) -> Unit = {},
    onFrameToolMode: (app.inkloom.document.FrameToolMode) -> Unit = {},
) {
    val language = settings.language
    val brushTool = tool in setOf(EditorTool.PEN, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)
    val variants = remember(brushes, tool) {
        if (brushTool) brushes.filter { !it.hidden && BrushRoleClassifier.editorTool(it) == tool }
        else emptyList()
    }
    val listState = key(tool) { rememberLazyListState() }
    val selectedVariant = when (tool) {
        EditorTool.SELECT -> settings.selectionShape.name to settings.selectionShape.ordinal
        EditorTool.FILL -> settings.fillSubTool.name to settings.fillSubTool.ordinal
        EditorTool.GRADIENT -> settings.gradientKind.name to settings.gradientKind.ordinal
        EditorTool.CORRECT_LINE -> settings.correctLineMode.name to settings.correctLineMode.ordinal
        EditorTool.RULER -> settings.rulerToolMode.name to settings.rulerToolMode.ordinal
        EditorTool.FRAME -> settings.frameToolMode.name to settings.frameToolMode.ordinal
        else -> selectedBrushId to variants.indexOfFirst { it.id == selectedBrushId }
    }
    RevealSelectedItem(listState, tool, selectedVariant.first, selectedVariant.second)
    Column(modifier.fillMaxSize().background(Paper)) {
        HorizontalDivider(color = Color(0xFF595959))
        LazyColumn(state = listState, modifier = Modifier.semantics { contentDescription = language.text("Subtool list", "子工具列表") }, contentPadding = PaddingValues(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (brushTool) {
                items(variants, key = ImportedBrush::id) { brush ->
                    val isSelected = brush.id == selectedBrushId
                    Row(Modifier.fillMaxWidth().semantics { selected = isSelected }.background(if (isSelected) Color(0xFF5A5A5A) else Color.Transparent,
                        RoundedCornerShape(5.dp)).clickable { onBrush(brush) }.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BrushStrokePreview(brush, language, Modifier.size(width = 74.dp, height = 27.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(brush.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f), fontSize = 12.sp, lineHeight = 15.sp, color = if (isSelected) Ink else MutedInk)
                        if (isSelected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = Ink)
                    }
                }
            } else if (tool == EditorTool.SELECT) {
                items(SelectionShape.entries, key = { it.name }) { shape ->
                    WorkspaceSubtoolRow(selectionShapeLabel(shape, settings), shape == settings.selectionShape) { onSelectionShape(shape) }
                }
            } else if (tool == EditorTool.FILL) {
                items(FillSubTool.entries, key = { it.name }) { subtool ->
                    WorkspaceSubtoolRow(fillSubtoolLabel(subtool, settings), subtool == settings.fillSubTool) { onFillSubTool(subtool) }
                }
            } else if (tool == EditorTool.GRADIENT) {
                items(GradientKind.entries, key = { it.name }) { kind ->
                    WorkspaceSubtoolRow(gradientKindLabel(kind, settings), kind == settings.gradientKind) { onGradientKind(kind) }
                }
            } else if (tool == EditorTool.CORRECT_LINE) {
                items(CorrectLineMode.entries, key = { it.name }) { mode ->
                    WorkspaceSubtoolRow(correctLineModeLabel(mode, settings), mode == settings.correctLineMode) { onCorrectLineMode(mode) }
                }
            } else if (tool == EditorTool.RULER) {
                items(app.inkloom.document.RulerToolMode.entries, key = { it.name }) { mode ->
                    WorkspaceSubtoolRow(rulerToolModeLabel(mode, settings), mode == settings.rulerToolMode) { onRulerToolMode(mode) }
                }
            } else if (tool == EditorTool.FRAME) {
                items(app.inkloom.document.FrameToolMode.entries, key = { it.name }) { mode ->
                    WorkspaceSubtoolRow(frameToolModeLabel(mode, settings), mode == settings.frameToolMode) { onFrameToolMode(mode) }
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
    Text(label, Modifier.fillMaxWidth().semantics { this.selected = selected }.background(if (selected) Color(0xFF5A5A5A) else Color.Transparent,
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

internal fun frameToolModeLabel(mode: app.inkloom.document.FrameToolMode, settings: AppSettings): String = when (mode) {
    app.inkloom.document.FrameToolMode.DIVIDE -> settings.language.text("Divide frame border", "分割分格")
    app.inkloom.document.FrameToolMode.RECTANGLE -> settings.language.text("Rectangle frame", "矩形分格")
    app.inkloom.document.FrameToolMode.SELECT -> settings.language.text("Select frames", "选择分格")
}

internal fun rulerToolModeLabel(mode: app.inkloom.document.RulerToolMode, settings: AppSettings): String = when (mode) {
    app.inkloom.document.RulerToolMode.EDIT -> settings.language.text("Edit rulers", "编辑尺规")
    app.inkloom.document.RulerToolMode.STRAIGHT -> settings.language.text("Linear ruler", "直线尺规")
    app.inkloom.document.RulerToolMode.CURVE -> settings.language.text("Curve ruler", "曲线尺规")
    app.inkloom.document.RulerToolMode.POLYLINE -> settings.language.text("Polyline ruler", "折线尺规")
    app.inkloom.document.RulerToolMode.RECTANGLE -> settings.language.text("Rectangle ruler", "矩形尺规")
    app.inkloom.document.RulerToolMode.ELLIPSE -> settings.language.text("Ellipse ruler", "椭圆尺规")
    app.inkloom.document.RulerToolMode.POLYGON -> settings.language.text("Polygon ruler", "多边形尺规")
    app.inkloom.document.RulerToolMode.PEN -> settings.language.text("Ruler pen", "尺规笔")
    app.inkloom.document.RulerToolMode.GUIDE -> settings.language.text("Guide", "参考线")
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
    shapeKind: ShapeKind,
    onShapeKind: (ShapeKind) -> Unit,
    modifier: Modifier = Modifier,
    onRulerCommand: (RulerCommand) -> Unit = {},
    onFrameAction: (FrameAction) -> Unit = {},
) {
    val language = settings.language
    var organizerOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(Paper)) {
        HorizontalDivider(color = Color(0xFF595959))
        Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(language.text("Tool property", "工具属性"), Modifier.weight(1f), color = Ink, fontSize = 12.sp)
            IconButton({ organizerOpen = true }, Modifier.size(32.dp)) {
                Icon(Icons.Outlined.EditNote, language.text("Customize tool rail", "自定义工具栏"), Modifier.size(18.dp), tint = MutedInk)
            }
        }
        HorizontalDivider(color = Color(0xFF595959))
        // Short docked panels and large fonts: every property stays reachable by scrolling.
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (tool in setOf(EditorTool.FILL, EditorTool.SELECT)) WorkspacePropertySwitch(
                language.text("Exclude draft layers", "排除草稿图层"), settings.excludeDraftLayersFromReference,
            ) { onSettings(settings.copy(excludeDraftLayersFromReference = it)) }
            if (tool in setOf(EditorTool.FILL, EditorTool.SELECT)) WorkspacePropertySwitch(
                language.text("Exclude locked layers", "排除锁定图层"), settings.excludeLockedLayersFromReference,
            ) { onSettings(settings.copy(excludeLockedLayersFromReference = it)) }
            if (tool in setOf(EditorTool.FILL, EditorTool.SELECT)) WorkspacePropertySwitch(
                language.text("Exclude editing layer", "排除编辑中的图层"), settings.excludeActiveLayerFromReference,
            ) { onSettings(settings.copy(excludeActiveLayerFromReference = it)) }
            if (tool in setOf(EditorTool.FILL, EditorTool.SELECT)) WorkspacePropertySwitch(language.text("Exclude paper", "排除纸张"), settings.excludePaperFromReference) {
                onSettings(settings.copy(excludePaperFromReference = it))
            }
            if (tool in setOf(EditorTool.FILL, EditorTool.SELECT)) WorkspacePropertySwitch(language.text("Exclude imported page background", "排除导入的页面背景"), settings.excludeImportedBackgroundFromReference) {
                onSettings(settings.copy(excludeImportedBackgroundFromReference = it))
            }
            if (tool in setOf(EditorTool.FILL, EditorTool.SELECT)) WorkspacePropertySwitch(language.text("Exclude text", "排除文字"), settings.excludeTextFromReference) {
                onSettings(settings.copy(excludeTextFromReference = it))
            }
            when (tool) {
                EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY -> {
                    WorkspacePropertySlider(language.text("Size", "大小"), "${brushSize.toInt()} px",
                        brushSize.coerceIn(1f, 500f), 1f..500f, onBrushSize)
                    if (tool == EditorTool.LIQUIFY) {
                        WorkspacePropertySlider(language.text("Force", "力度"), "${(settings.liquifyStrengthScale * 100).toInt()}%",
                            settings.liquifyStrengthScale, 0f..2f, { onSettings(settings.copy(liquifyStrengthScale = it)) })
                        WorkspacePropertySlider(language.text("Distortion", "畸变"), "${(settings.liquifyDistortion * 100).toInt()}%",
                            settings.liquifyDistortion, 0f..1f, { onSettings(settings.copy(liquifyDistortion = it)) })
                        WorkspacePropertySlider(language.text("Momentum", "惯性"), "${(settings.liquifyMomentum * 100).toInt()}%",
                            settings.liquifyMomentum, 0f..1f, { onSettings(settings.copy(liquifyMomentum = it)) })
                    } else WorkspacePropertySlider(language.text("Opacity", "不透明度"), "${(brushOpacity * 100).toInt()}%",
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
                    Text(language.text("Fill target", "填充目标"), color = Ink, fontSize = 11.sp)
                    app.inkloom.settings.FillTargetMode.entries.forEach { option -> WorkspaceSubtoolRow(when (option) {
                        app.inkloom.settings.FillTargetMode.TOUCHED_COLOR -> language.text("Touched color", "触点颜色")
                        app.inkloom.settings.FillTargetMode.TRANSPARENT_ONLY -> language.text("Transparent area", "透明区域")
                        app.inkloom.settings.FillTargetMode.SPECIFIED_COLOR -> language.text("Specified color", "指定颜色")
                    }, option == settings.fillTargetMode) { onSettings(settings.copy(fillTargetMode = option)) } }
                    if (settings.fillTargetMode == app.inkloom.settings.FillTargetMode.SPECIFIED_COLOR) {
                        var showTargetPicker by remember { mutableStateOf(false) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(language.text("Target color", "目标颜色"), Modifier.weight(1f), color = Ink, fontSize = 11.sp)
                            Surface(Modifier.size(26.dp).clickable { showTargetPicker = true }, color = Color(settings.fillTargetColor),
                                shape = RoundedCornerShape(3.dp), border = CardDefaults.outlinedCardBorder()) {}
                        }
                        if (showTargetPicker) StandardColorPickerDialog(settings.fillTargetColor, language,
                            onConfirm = { onSettings(settings.copy(fillTargetColor = it)); showTargetPicker = false },
                            onDismiss = { showTargetPicker = false })
                    }
                    WorkspacePropertySlider(language.text("Tolerance", "容差"), "${(settings.fillSensitivity * 100).toInt()}%",
                        settings.fillSensitivity, 0f..1f, { onSettings(settings.copy(fillSensitivity = it)) })
                    WorkspacePropertySlider(language.text("Close gap", "闭合间隙"), "${settings.fillCloseGapPixels.toInt()} px",
                        settings.fillCloseGapPixels, 0f..64f, { onSettings(settings.copy(fillCloseGapPixels = it)) })
                    WorkspacePropertySlider(language.text("Area scaling", "区域缩放"), "${settings.fillAreaScalingPixels.toInt()} px",
                        settings.fillAreaScalingPixels, -64f..64f, { onSettings(settings.copy(fillAreaScalingPixels = it)) })
                    WorkspacePropertySwitch(language.text("Anti-aliasing", "抗锯齿"), settings.fillAntiAlias) {
                        onSettings(settings.copy(fillAntiAlias = it))
                    }
                    if (settings.fillAreaScalingPixels != 0f) {
                        Text(language.text("Scaling corners", "扩张边角"), color = Ink, fontSize = 11.sp)
                        app.inkloom.settings.FillExpansionShape.entries.forEach { shape -> WorkspaceSubtoolRow(
                            if (shape == app.inkloom.settings.FillExpansionShape.ROUND) language.text("Round", "圆形") else language.text("Rectangle", "矩形"),
                            shape == settings.fillExpansionShape,
                        ) { onSettings(settings.copy(fillExpansionShape = shape)) } }
                    }
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
                EditorTool.EYEDROPPER -> {
                    Text(language.text("Sample from", "取样范围"), color = Ink, fontSize = 11.sp)
                    app.inkloom.settings.ReferenceTarget.entries.forEach { target -> WorkspaceSubtoolRow(when(target) {
                        app.inkloom.settings.ReferenceTarget.ALL_VISIBLE -> language.text("All visible layers", "全部可见图层")
                        app.inkloom.settings.ReferenceTarget.ACTIVE_LAYER -> language.text("Current layer", "当前图层")
                        app.inkloom.settings.ReferenceTarget.REFERENCE_LAYERS -> language.text("Reference layers", "参考图层")
                    }, target == settings.eyedropperReferenceTarget) { onSettings(settings.copy(eyedropperReferenceTarget=target)) } }
                    if (settings.eyedropperReferenceTarget == app.inkloom.settings.ReferenceTarget.ALL_VISIBLE) {
                        WorkspacePropertySwitch(language.text("Include paper", "包含纸张"), settings.eyedropperIncludePaper) {
                            onSettings(settings.copy(eyedropperIncludePaper = it))
                        }
                    }
                }
                EditorTool.SHAPE -> {
                    Text(language.text("Shape", "形状"), color = Ink, fontSize = 11.sp)
                    ShapeKind.entries.forEach { kind -> WorkspaceSubtoolRow(when(kind) {
                        ShapeKind.RECTANGLE -> language.text("Rectangle", "矩形")
                        ShapeKind.ELLIPSE -> language.text("Ellipse", "椭圆")
                        ShapeKind.TRIANGLE -> language.text("Triangle", "三角形")
                        ShapeKind.DIAMOND -> language.text("Diamond", "菱形")
                        ShapeKind.LINE -> language.text("Line", "直线")
                        ShapeKind.ARROW -> language.text("Arrow", "箭头")
                        ShapeKind.FREEFORM -> language.text("Freeform", "自由形")
                    },kind==shapeKind) { onShapeKind(kind) } }
                }
                EditorTool.LASER -> WorkspacePropertySlider(language.text("Pointer size", "指示大小"), "${brushSize.toInt()} px",
                    brushSize.coerceIn(1f,500f),1f..500f,onBrushSize)
                EditorTool.TEXT -> WorkspacePropertyHint(language.text("Tap the canvas to create editable text. Select text to edit typography and spacing.", "点击画布创建可编辑文字；选中文字后可调整排版和间距。"))
                EditorTool.NOTE -> WorkspacePropertyHint(language.text("Tap the canvas to place a note. Its content remains editable.", "点击画布放置便笺，内容会保持可编辑。"))
                EditorTool.LINK -> WorkspacePropertyHint(language.text("Tap the canvas to create an external or notebook link.", "点击画布创建外部链接或笔记内部链接。"))
                EditorTool.IMAGE -> WorkspacePropertyHint(language.text("Tap the canvas to import an image as an editable object.", "点击画布导入图片并保持为可编辑对象。"))
                EditorTool.RULER -> RulerToolProperties(settings, onSettings, onRulerCommand)
                EditorTool.FRAME -> FrameToolProperties(settings, onSettings, onFrameAction)
            }
        }
    }
    if (organizerOpen) ToolPaletteOrganizerDialog(settings, onSettings, { organizerOpen = false })
}

@Composable
private fun WorkspacePropertyHint(text: String) {
    Text(text, Modifier.padding(vertical=8.dp), color=MutedInk, fontSize=12.sp, lineHeight=16.sp)
}

@Composable
private fun ToolPaletteOrganizerDialog(settings: AppSettings, onSettings: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    val language=settings.language
    var entries by remember(settings.toolPaletteLayout) { mutableStateOf(ToolPaletteLayout.decode(settings.toolPaletteLayout)) }
    val latestEntries by rememberUpdatedState(entries)
    fun label(tool: EditorTool)=when(tool) {
        EditorTool.PEN -> language.text("Brush", "笔刷")
        EditorTool.HIGHLIGHTER -> language.text("Marker", "标记笔")
        EditorTool.ERASER -> language.text("Eraser", "橡皮")
        EditorTool.EYEDROPPER -> language.text("Eyedropper", "吸管")
        EditorTool.SELECT -> language.text("Selection", "选区")
        EditorTool.SHAPE -> language.text("Shape", "形状")
        EditorTool.TEXT -> language.text("Text", "文字")
        EditorTool.NOTE -> language.text("Note", "便笺")
        EditorTool.LINK -> language.text("Link", "链接")
        EditorTool.IMAGE -> language.text("Image", "图片")
        EditorTool.LASER -> language.text("Laser", "激光笔")
        EditorTool.FILL -> language.text("Fill", "填充")
        EditorTool.GRADIENT -> language.text("Gradient", "渐变")
        EditorTool.SMUDGE -> language.text("Blend", "混色")
        EditorTool.LIQUIFY -> language.text("Liquify", "液化")
        EditorTool.CORRECT_LINE -> language.text("Correct line", "线条修正")
        EditorTool.RULER -> language.text("Ruler", "尺规")
        EditorTool.FRAME -> language.text("Frame border", "分格")
    }
    StudioDialog(
        onDismissRequest=onDismiss,
        title={Text(language.text("Customize tool rail", "自定义工具栏"))},
        text={Column(Modifier.dialogContentWidth(300.dp, 420.dp).heightIn(max=560.dp)) {
            Text(language.text("Long-press and drag tools across separators. Changes are saved with the workspace.", "长按拖动工具可跨越分隔线；设置会随工作区保存。"),fontSize=11.sp,color=MutedInk)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                itemsIndexed(entries,key={ index,entry -> when(entry) { is ToolPaletteEntry.Tool -> entry.tool.name; ToolPaletteEntry.Separator -> "separator-$index" } }) { index,entry ->
                    when(entry) {
                        is ToolPaletteEntry.Tool -> {
                            val latestIndex by rememberUpdatedState(index)
                            Row(Modifier.fillMaxWidth().height(42.dp).background(app.inkloom.ui.theme.SageContainer,RoundedCornerShape(5.dp))
                                .pointerInput(entry.tool) {
                                    var current=latestIndex
                                    var travel=0f
                                    var pending=latestEntries
                                    detectDragGesturesAfterLongPress(onDragEnd={
                                        onSettings(settings.copy(toolPaletteLayout=ToolPaletteLayout.encode(pending)))
                                    },onDragCancel={}) { change,amount ->
                                        change.consume();travel+=amount.y
                                        while(kotlin.math.abs(travel)>=30f) {
                                            val direction=if(travel>0) 1 else -1
                                            val next=(current+direction).coerceIn(0,pending.lastIndex)
                                            if(next==current) { travel=0f;break }
                                            pending=ToolPaletteLayout.move(pending,current,if(direction>0) current+2 else current-1)
                                            entries=pending;current=next;travel-=direction*30f
                                        }
                                    }
                                }.semantics { contentDescription = language.text("Reorder tool ${label(entry.tool)}", "拖动排序工具 ${label(entry.tool)}") }
                                .padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Outlined.DragIndicator,null,Modifier.size(18.dp),tint=MutedInk)
                                Spacer(Modifier.width(8.dp));Text(label(entry.tool),Modifier.weight(1f),color=Ink,fontSize=12.sp)
                            }
                        }
                        ToolPaletteEntry.Separator -> Row(Modifier.fillMaxWidth().height(30.dp),verticalAlignment=Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f),color=Color(0xFF777777))
                            IconButton({
                                val next=ToolPaletteLayout.removeSeparator(entries,index);entries=next
                                onSettings(settings.copy(toolPaletteLayout=ToolPaletteLayout.encode(next)))
                            },Modifier.size(30.dp)) { Icon(Icons.Outlined.Delete,language.text("Remove separator", "删除分隔线"),Modifier.size(16.dp),tint=MutedInk) }
                        }
                    }
                }
            }
            TextButton({
                val next=ToolPaletteLayout.addSeparator(entries);entries=next
                onSettings(settings.copy(toolPaletteLayout=ToolPaletteLayout.encode(next)))
            }) { Icon(Icons.Outlined.HorizontalRule,null);Spacer(Modifier.width(6.dp));Text(language.text("Add separator", "添加分隔线")) }
        }},
        confirmButton={TextButton(onClick=onDismiss){Text(language.text("Done", "完成"))}},
        dismissButton={TextButton(onClick={
            val next=ToolPaletteLayout.defaultEntries;entries=next
            onSettings(settings.copy(toolPaletteLayout=ToolPaletteLayout.encode(next)))
        }){Text(language.text("Reset", "恢复默认"))}},
    )
}

@Composable
private fun WorkspacePropertySwitch(label: String, checked: Boolean, modifier: Modifier = Modifier,
                                    onCheckedChange: (Boolean) -> Unit) {
    // The whole row, including its label, is one switch target for touch and accessibility.
    Row(modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Ink, fontSize = 11.sp)
        Switch(checked = checked, onCheckedChange = null)
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

/** The ruler tool: snapping, polygon sides, finishing curves and deleting the selected ruler. */
@Composable
private fun RulerToolProperties(settings: AppSettings, onSettings: (AppSettings) -> Unit, onCommand: (RulerCommand) -> Unit) {
    val language = settings.language
    WorkspacePropertySwitch(language.text("Snap to rulers", "吸附到尺规"), settings.snapToRulers) { onSettings(settings.copy(snapToRulers = it)) }
    if (settings.rulerToolMode == app.inkloom.document.RulerToolMode.POLYGON) WorkspacePropertySlider(language.text("Sides", "边数"),
        "${settings.rulerPolygonSides}", settings.rulerPolygonSides.toFloat(), 3f..32f, { onSettings(settings.copy(rulerPolygonSides = it.roundToInt())) })
    WorkspacePropertyHint(when (settings.rulerToolMode) {
        app.inkloom.document.RulerToolMode.EDIT -> language.text("Drag a point to reshape a ruler on the current layer, or drag its line to move it.", "拖动控制点调整当前图层的尺规，拖动线条可移动尺规。")
        app.inkloom.document.RulerToolMode.CURVE, app.inkloom.document.RulerToolMode.POLYLINE ->
            language.text("Tap to add points. Tap the first point to close the ruler, or double-tap to finish.", "点击添加控制点；点击第一个点闭合尺规，双击结束。")
        app.inkloom.document.RulerToolMode.GUIDE -> language.text("Drag across the page for a horizontal guide, or down it for a vertical one.", "横向拖动创建水平参考线，纵向拖动创建垂直参考线。")
        else -> language.text("Drag on the canvas to place the ruler on the current layer.", "在画布上拖动，将尺规放到当前图层。")
    })
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (settings.rulerToolMode == app.inkloom.document.RulerToolMode.CURVE || settings.rulerToolMode == app.inkloom.document.RulerToolMode.POLYLINE) {
            StudioButton(language.text("Finish", "完成"), { onCommand(RulerCommand.FINISH) })
            StudioButton(language.text("Cancel", "取消"), { onCommand(RulerCommand.CANCEL) })
        }
        if (settings.rulerToolMode == app.inkloom.document.RulerToolMode.EDIT) StudioButton(language.text("Delete ruler", "删除尺规"), { onCommand(RulerCommand.DELETE) })
    }
}

/** The frame border tool: gutters, how dividing treats folders, and the frame commands. */
@Composable
private fun FrameToolProperties(settings: AppSettings, onSettings: (AppSettings) -> Unit, onAction: (FrameAction) -> Unit) {
    val language = settings.language
    WorkspacePropertySlider(language.text("Left–right gutter", "左右间隔"), "${settings.frameHorizontalGutter.roundToInt()}",
        settings.frameHorizontalGutter, 0f..300f, { onSettings(settings.copy(frameHorizontalGutter = it)) })
    WorkspacePropertySlider(language.text("Top–bottom gutter", "上下间隔"), "${settings.frameVerticalGutter.roundToInt()}",
        settings.frameVerticalGutter, 0f..300f, { onSettings(settings.copy(frameVerticalGutter = it)) })
    Text(language.text("Dividing", "分割方式"), color = Ink, fontSize = 11.sp)
    app.inkloom.document.FrameDivision.entries.forEach { division -> WorkspaceSubtoolRow(when (division) {
        app.inkloom.document.FrameDivision.BORDER_ONLY -> language.text("Divide the frame border only", "仅分割分格框线")
        app.inkloom.document.FrameDivision.FOLDERS_EMPTY -> language.text("New folders, empty", "分割文件夹（空白）")
        app.inkloom.document.FrameDivision.FOLDERS_DUPLICATE -> language.text("New folders, copying layers", "分割文件夹并复制图层")
    }, division == settings.frameDivision) { onSettings(settings.copy(frameDivision = division)) } }
    WorkspacePropertyHint(when (settings.frameToolMode) {
        app.inkloom.document.FrameToolMode.DIVIDE -> language.text("Drag across a frame to divide it.", "在分格上拖动以分割。")
        app.inkloom.document.FrameToolMode.RECTANGLE -> language.text("Drag to add a rectangle frame to the folder.", "拖动为文件夹添加矩形分格。")
        app.inkloom.document.FrameToolMode.SELECT -> language.text("Tap frames to select them.", "点按分格以选择。")
    })
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudioButton(language.text("New folder…", "新建…"), { onAction(FrameAction.CREATE) })
        StudioButton(language.text("Divide equally…", "等分…"), { onAction(FrameAction.DIVIDE_EQUALLY) })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudioButton(language.text("Combine", "合并"), { onAction(FrameAction.COMBINE) })
        StudioButton(language.text("Delete frames", "删除分格"), { onAction(FrameAction.DELETE_SELECTED) })
    }
}
