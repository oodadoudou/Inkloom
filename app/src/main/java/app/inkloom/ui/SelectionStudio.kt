package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.detectDragGestures
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.*
import app.inkloom.model.*
import app.inkloom.settings.FloatingToolbarPlacement
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.ReferenceTarget
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun SelectionStudioPanel(
    shape: SelectionShape, mode: SelectionMode, hasSelection: Boolean,
    brushSize: Float, onBrushSize: (Float) -> Unit,
    threshold: Float, onThreshold: (Float) -> Unit,
    referenceTarget: ReferenceTarget, onReferenceTarget: (ReferenceTarget) -> Unit,
    maskVisibility: Float, onMaskVisibility: (Float) -> Unit,
    onShape: (SelectionShape) -> Unit, onMode: (SelectionMode) -> Unit,
    onClear: () -> Unit, colorFill: Boolean, onColorFill: () -> Unit,
    quickMask: Boolean, onQuickMask: () -> Unit,
    colorRangeTarget: Int, onColorRange: (Float, Boolean) -> Boolean,
    onSaveLoad: () -> Unit, featherPixels: Float,
    interpolation: app.inkloom.engine.v5.tools.SelectionInterpolation,
    onInterpolation: (app.inkloom.engine.v5.tools.SelectionInterpolation) -> Unit,
    onDone: () -> Unit, onDeselect: () -> Unit, onInvert: () -> Unit,
    onCut: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit, onDeleteOutside: () -> Unit,
    strokeColor: Int, strokeOpacity: Float,
    onStroke: (Float, app.inkloom.engine.v5.selection.SelectionStrokePlacement, Int, Float) -> Boolean,
    onSelectAll: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onPasteAtView: () -> Unit,
    filter: SelectionFilter, onFilter: (SelectionFilter) -> Unit,
    onExpand: (Float) -> Unit, onShrink: (Float) -> Unit, onBorder: (Float) -> Unit,
    onFeather: (Float) -> Unit, onBeginFeather: () -> Boolean, onFinishFeather: (Boolean) -> Unit,
    onFill: () -> Unit, onTransform: () -> Unit,
    positionX: Float, positionY: Float, onPosition: (Float,Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var optionsOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var colorRangeOpen by remember { mutableStateOf(false) }
    var strokeOpen by remember { mutableStateOf(false) }
    val handwriting = LocalEditorBridge.current
    var convertOpen by remember { mutableStateOf(false) }
    if (convertOpen && handwriting != null) HandwritingToTextDialog(handwriting, LocalUiLanguage.current) { convertOpen = false }
    if (strokeOpen) SelectionStrokeDialog(strokeColor, strokeOpacity, LocalUiLanguage.current,
        onApply = { width, placement, color, opacity -> if (onStroke(width, placement, color, opacity)) strokeOpen = false },
        onDismiss = { strokeOpen = false })
    var colorRangeTolerance by rememberSaveable { mutableFloatStateOf(.18f) }
    var colorRangeAlpha by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableFloatStateOf(8f) }
    if (colorRangeOpen) StudioDialog(
        onDismissRequest = { colorRangeOpen = false },
        title = { Text(tr("Color range", "色域选择")) },
        text = { Column(Modifier.dialogContentWidth(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(Color(colorRangeTarget), RoundedCornerShape(5.dp)))
            }
            Text("${tr("Tolerance", "容差")} ${(colorRangeTolerance * 100).toInt()}%")
            BrushParameterSlider(colorRangeTolerance, { colorRangeTolerance = it }, valueRange = 0f..1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioCheckbox(colorRangeAlpha, { colorRangeAlpha = it })
                Text(tr("Include transparency difference", "包含透明度差异"), fontSize = 12.sp)
            }
        } },
        confirmButton = { TextButton(onClick = { if (onColorRange(colorRangeTolerance, colorRangeAlpha)) colorRangeOpen = false }) { Text(tr("Select", "选择")) } },
        dismissButton = { TextButton(onClick = { colorRangeOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    // Rarely changed preferences; the tool options themselves sit in the bar.
    if (optionsOpen) StudioDialog(
        onDismissRequest = { optionsOpen = false },
        title = { Text(tr("Selection settings", "选区设置")) },
        text = { Column(Modifier.dialogContentWidth(320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Text("${tr("Mask visibility", "蒙版可见度")}  ${(maskVisibility * 100).toInt()}%", fontSize = 12.sp)
            BrushParameterSlider(maskVisibility, onMaskVisibility, valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Selection mask visibility" })
            Text(tr("Select from", "选择范围"), fontSize = 12.sp)
            SelectionFilter.entries.forEach { option ->
                val label = when (option) {
                    SelectionFilter.ALL -> tr("All", "全部")
                    SelectionFilter.ACTIVE_LAYER -> tr("Current layer", "当前图层")
                    SelectionFilter.CURRENT_BRUSH -> tr("Current brush", "当前笔刷")
                    SelectionFilter.CURRENT_COLOR -> tr("Current color", "当前颜色")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioRadioButton(selected = filter == option, onClick = { onFilter(option) })
                    TextButton(onClick = { onFilter(option) }) { Text(label) }
                }
            }
            Text(tr("Interpolation", "插值"), fontSize = 12.sp)
            app.inkloom.engine.v5.tools.SelectionInterpolation.entries.forEach { method ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioRadioButton(selected = interpolation == method, onClick = { onInterpolation(method) })
                    TextButton(onClick = { onInterpolation(method) }) { Text(when (method) {
                        app.inkloom.engine.v5.tools.SelectionInterpolation.NEAREST -> tr("Nearest neighbor", "最近邻")
                        app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR -> tr("Bilinear", "双线性")
                        app.inkloom.engine.v5.tools.SelectionInterpolation.BICUBIC -> tr("Bicubic", "双三次")
                    }) }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { optionsOpen = false }) { Text(tr("Done", "完成")) } },
    )
    val studioDescription = tr("Selection studio", "选区面板")
    val bridge = LocalEditorBridge.current
    var featherMenu by remember { mutableStateOf(false) }
    // A feather preview still open when the bar goes away (tool change, transform) is cancelled.
    val finishFeather by rememberUpdatedState(onFinishFeather)
    val featherPreviewing by rememberUpdatedState(featherMenu)
    DisposableEffect(Unit) { onDispose { if (featherPreviewing) finishFeather(false) } }
    var modifyMenu by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
    val density=LocalDensity.current.density
    var measuredWidth by remember { mutableFloatStateOf(640f) }
    var measuredHeight by remember { mutableFloatStateOf(112f) }
    val containerWidth=maxWidth.value
    val containerHeight=maxHeight.value
    var dragX by remember(positionX) { mutableFloatStateOf(positionX) }
    var dragY by remember(positionY) { mutableFloatStateOf(positionY) }
    val desiredX=if(dragX>=0f) dragX else (containerWidth-measuredWidth)/2f
    val desiredY=if(dragY>=0f) dragY else containerHeight-measuredHeight-4f
    val bounded=FloatingToolbarPlacement.clamp(desiredX,desiredY,containerWidth,containerHeight,false,measuredWidth,measuredHeight)
    Surface(Modifier.offset { IntOffset((bounded.xDp*density).roundToInt(),(bounded.yDp*density).roundToInt()) }
        .widthIn(max = minOf(640.dp, maxWidth)).onGloballyPositioned {
            measuredWidth=it.size.width/density;measuredHeight=it.size.height/density
        }.semantics { contentDescription = studioDescription }, shape = RoundedCornerShape(14.dp), color = Paper, shadowElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            // How to select: the tools, then how a new selection combines with the current one. In a
            // narrow window these scroll; the menu and the exit buttons stay in view.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DragIndicator,tr("Move selection studio", "移动选区面板"),
                        Modifier.size(30.dp).padding(4.dp).pointerInput(containerWidth,containerHeight,measuredWidth,measuredHeight) {
                            detectDragGestures(onDragStart={dragX=bounded.xDp;dragY=bounded.yDp},
                                onDragEnd={onPosition(dragX,dragY)},onDragCancel={dragX=bounded.xDp;dragY=bounded.yDp}) { change,delta ->
                                change.consume()
                                val next=FloatingToolbarPlacement.clamp(dragX+delta.x/density,dragY+delta.y/density,
                                    containerWidth,containerHeight,false,measuredWidth,measuredHeight)
                                dragX=next.xDp;dragY=next.yDp
                            }
                        },tint=MutedInk)
                    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                        SelectionTools.forEach { option ->
                            SelectionChoice(option.icon(), selectionToolDescription(option), selected = shape == option) { onShape(option) }
                        }
                    }
                    SelectionDivider()
                    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                        SelectionMode.entries.forEach { option ->
                            SelectionChoice(null, selectionModeName(option), selected = mode == option, glyph = { SelectionModeGlyph(option, it) }) { onMode(option) }
                        }
                    }
                }
                SelectionDivider()
                Box {
                    SelectionBarButton(Icons.Outlined.MoreHoriz, tr("Selection actions", "选区操作")) { settingsOpen = true }
                    DropdownMenu(settingsOpen, { settingsOpen = false }) {
                        if (hasSelection) {
                            StudioMenuItem(text = { Text(tr("Stroke selection…", "选区描边…")) }, onClick = { settingsOpen = false; strokeOpen = true })
                            StudioMenuItem(text = { Text(tr("Clear outside selection", "删除选区外内容")) }, onClick = { settingsOpen = false; onDeleteOutside() })
                            if (handwriting != null) StudioMenuItem(text = { Text(tr("Convert to text…", "转换为文字…")) }, onClick = { settingsOpen = false; convertOpen = true })
                            StudioMenuItem(text = { Text(tr("Copy to clipboard", "复制到剪贴板")) }, onClick = { settingsOpen = false; onClipboardCopy() })
                            HorizontalDivider()
                        }
                        StudioMenuItem(text = { Text(tr("Select all", "全部选择")) }, onClick = { settingsOpen = false; onSelectAll() })
                        StudioMenuItem(text = { Text(tr("Color range…", "色域选择…")) }, onClick = { settingsOpen = false; colorRangeOpen = true })
                        StudioMenuItem(text = { Text(tr("Paste", "粘贴")) }, onClick = { settingsOpen = false; onPaste() })
                        StudioMenuItem(text = { Text(tr("Paste to shown position", "粘贴至显示位置")) }, onClick = { settingsOpen = false; onPasteAtView() })
                        StudioMenuItem(text = { Text(tr("Save & Load", "保存与载入")) }, onClick = { settingsOpen = false; onSaveLoad() })
                        HorizontalDivider()
                        StudioMenuItem(text = { Text(tr("Quick mask", "快速蒙版")) }, trailingIcon = { if (quickMask) Icon(Icons.Outlined.Check, null) }, onClick = { settingsOpen = false; onQuickMask() })
                        StudioMenuItem(text = { Text(tr("Fill new selections", "新选区自动填色")) }, trailingIcon = { if (colorFill) Icon(Icons.Outlined.Check, null) }, onClick = { settingsOpen = false; onColorFill() })
                        StudioMenuItem(text = { Text(tr("Selection settings", "选区设置")) }, onClick = { settingsOpen = false; optionsOpen = true })
                    }
                }
                SelectionBarButton(Icons.Outlined.Check, tr("Exit selection tool", "退出选区工具"), onDone)
                SelectionBarButton(Icons.Outlined.Close, tr("Deselect and exit", "取消选区并退出"), onDeselect)
            }
            // The current tool's options, where they are used (Photoshop's options bar, CSP's tool property).
            // Lasso, rectangle and ellipse have none, so the row is left out.
            val hasOptions = shape == SelectionShape.MAGIC_WAND || shape == SelectionShape.SMART_BORDER || shape == SelectionShape.BRUSH
            if (hasOptions) Row(Modifier.fillMaxWidth().heightIn(min = 36.dp).padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                when (shape) {
                    SelectionShape.MAGIC_WAND, SelectionShape.SMART_BORDER -> {
                        Text(if (shape == SelectionShape.MAGIC_WAND) tr("Tolerance", "容差") else tr("Detail", "细节"), fontSize = 11.sp, color = MutedInk)
                        BrushParameterSlider(threshold, onThreshold, Modifier.width(140.dp).semantics { contentDescription = "Selection tolerance" }, valueRange = 0f..1f)
                        Text("${(threshold * 100).roundToInt()}%", Modifier.widthIn(min = 34.dp), fontSize = 11.sp)
                        if (shape == SelectionShape.MAGIC_WAND && bridge != null) {
                            SelectionToggle(tr("Contiguous", "连续"), bridge.selectionContiguous) { bridge.setSelectionContiguous(it) }
                            val gap = bridge.selectionCloseGapLevel
                            SelectionToggle(tr("Gap", "闭合") + " · " + when (gap) { 0 -> tr("Off", "关"); 1 -> tr("S", "小"); 2 -> tr("M", "中"); else -> tr("L", "大") },
                                gap > 0) { bridge.cycleSelectionCloseGap() }
                        }
                        Text(tr("Sample", "参照"), Modifier.padding(start = 6.dp, end = 2.dp), fontSize = 11.sp, color = MutedInk)
                        ReferenceTarget.entries.forEach { target ->
                            SelectionToggle(when (target) {
                                ReferenceTarget.ACTIVE_LAYER -> tr("Layer", "当前图层")
                                ReferenceTarget.ALL_VISIBLE -> tr("All", "全部")
                                ReferenceTarget.REFERENCE_LAYERS -> tr("Reference", "参考图层")
                            }, referenceTarget == target) { onReferenceTarget(target) }
                        }
                    }
                    SelectionShape.BRUSH -> {
                        Text(tr("Size", "大小"), fontSize = 11.sp, color = MutedInk)
                        BrushParameterSlider(brushSize, onBrushSize, Modifier.width(180.dp).semantics { contentDescription = "Selection pen size" }, valueRange = 4f..300f)
                        Text("${brushSize.roundToInt()} px", fontSize = 11.sp)
                    }
                    else -> Unit
                }
            }
            // What to do with it: only while something is selected (CSP's selection launcher,
            // Photoshop's contextual task bar).
            if (hasSelection) {
                HorizontalDivider(color = OutlineSoft)
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    SelectionStudioAction(Icons.Outlined.Deselect, tr("Deselect", "取消选择"), onClick = onClear)
                    SelectionStudioAction(Icons.Outlined.Flip, tr("Invert", "反选"), onClick = onInvert)
                    Box {
                        SelectionStudioAction(Icons.Outlined.BlurOn, tr("Feather", "羽化")) { featherMenu = onBeginFeather() }
                        DropdownMenu(featherMenu, { onFinishFeather(false); featherMenu = false }) {
                            Column(Modifier.width(260.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("${tr("Feather", "羽化")} · ${featherPixels.roundToInt()} px", fontSize = 12.sp)
                                BrushParameterSlider(featherPixels.coerceIn(0f, 128f), onFeather, valueRange = 0f..128f,
                                    modifier = Modifier.semantics { contentDescription = "Selection feather radius" })
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { onFinishFeather(false); featherMenu = false }) { Text(tr("Cancel feather", "取消羽化")) }
                                    TextButton(onClick = { onFinishFeather(true); featherMenu = false }) { Text(tr("Apply feather", "应用羽化")) }
                                }
                            }
                        }
                    }
                    Box {
                        SelectionStudioAction(Icons.Outlined.OpenInFull, tr("Expand / shrink", "扩大／缩小")) { modifyMenu = true }
                        DropdownMenu(modifyMenu, { modifyMenu = false }) {
                            Column(Modifier.width(260.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("${tr("Amount", "调整量")} · ${amount.roundToInt()} px", fontSize = 12.sp)
                                BrushParameterSlider(amount, { amount = it }, valueRange = 1f..128f,
                                    modifier = Modifier.semantics { contentDescription = "Selection modify amount" })
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { onShrink(amount) }) { Text(tr("Shrink", "缩小")) }
                                    TextButton(onClick = { onExpand(amount) }) { Text(tr("Expand", "扩大")) }
                                    TextButton(onClick = { onBorder(amount); modifyMenu = false }) { Text(tr("Border", "边界")) }
                                }
                            }
                        }
                    }
                    SelectionStudioAction(Icons.Outlined.FormatColorFill, tr("Fill", "填充"), onClick = onFill)
                    SelectionStudioAction(Icons.Outlined.Delete, tr("Delete", "删除"), onClick = onDelete)
                    SelectionStudioAction(Icons.Outlined.ContentCopy, tr("Copy to layer", "复制到新图层"), onClick = onCopy)
                    SelectionStudioAction(Icons.Outlined.ContentCut, tr("Cut to layer", "剪切到新图层"), onClick = onCut)
                    SelectionStudioAction(Icons.Outlined.Transform, tr("Transform", "变换"), onClick = onTransform)
                }
            }
        }
    }
    }
}

/** The selection tools, in the order Clip Studio Paint and Photoshop show them. */
private val SelectionTools = listOf(SelectionShape.MAGIC_WAND, SelectionShape.FREEFORM, SelectionShape.RECTANGLE,
    SelectionShape.ELLIPSE, SelectionShape.BRUSH, SelectionShape.SMART_BORDER)

private fun SelectionShape.icon(): ImageVector = when (this) {
    SelectionShape.MAGIC_WAND -> Icons.Outlined.AutoFixNormal
    SelectionShape.FREEFORM -> Icons.Outlined.Gesture
    SelectionShape.RECTANGLE -> Icons.Outlined.CropSquare
    SelectionShape.ELLIPSE -> Icons.Outlined.PanoramaFishEye
    SelectionShape.BRUSH -> Icons.Outlined.Brush
    SelectionShape.SMART_BORDER -> Icons.Outlined.HighlightAlt
}

/** Named as a selection tool, so the bar's magic wand differs from the rail's. */
@Composable
private fun selectionToolDescription(shape: SelectionShape): String = when (shape) {
    SelectionShape.MAGIC_WAND -> tr("Magic wand selection", "魔棒选区")
    SelectionShape.FREEFORM -> tr("Lasso selection", "套索选区")
    SelectionShape.RECTANGLE -> tr("Rectangle selection", "矩形选区")
    SelectionShape.ELLIPSE -> tr("Ellipse selection", "椭圆选区")
    SelectionShape.BRUSH -> tr("Selection pen", "选区笔")
    SelectionShape.SMART_BORDER -> tr("Smart border selection", "智能边界选区")
}

@Composable
private fun selectionModeName(mode: SelectionMode): String = when (mode) {
    SelectionMode.REPLACE -> tr("New selection", "新建选区")
    SelectionMode.ADD -> tr("Add to selection", "添加到选区")
    SelectionMode.SUBTRACT -> tr("Subtract from selection", "从选区减去")
    SelectionMode.INTERSECT -> tr("Intersect with selection", "与选区交叉")
}

@Composable
private fun SelectionDivider() = VerticalDivider(Modifier.height(24.dp).padding(horizontal = 3.dp), color = OutlineSoft)

/** An icon choice in a group: a tool or a combine mode. Named on hover and for accessibility. */
@Composable
private fun SelectionChoice(icon: ImageVector?, description: String, selected: Boolean,
    glyph: (@Composable (Color) -> Unit)? = null, onClick: () -> Unit) {
    HoverTooltip(description) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) SageContainer else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
            contentAlignment = Alignment.Center) {
            val tint = if (selected) Ink else MutedInk
            if (glyph != null) glyph(tint) else if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = tint)
        }
    }
}

/** Photoshop's combine-mode pictures: two overlapping squares, the result filled. */
@Composable
private fun SelectionModeGlyph(mode: SelectionMode, tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val unit = size.minDimension
        val first = androidx.compose.ui.graphics.Path().apply { addRect(androidx.compose.ui.geometry.Rect(unit * .08f, unit * .08f, unit * .64f, unit * .64f)) }
        val second = androidx.compose.ui.graphics.Path().apply { addRect(androidx.compose.ui.geometry.Rect(unit * .36f, unit * .36f, unit * .92f, unit * .92f)) }
        val filled = when (mode) {
            SelectionMode.REPLACE -> first
            SelectionMode.ADD -> androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Union, first, second)
            SelectionMode.SUBTRACT -> androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, first, second)
            SelectionMode.INTERSECT -> androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Intersect, first, second)
        }
        drawPath(filled, tint.copy(alpha = .55f))
        val outline = Stroke(width = 1.4.dp.toPx())
        drawPath(first, tint, style = outline)
        if (mode != SelectionMode.REPLACE) drawPath(second, tint, style = outline)
    }
}

/** A small on/off chip for a tool option. */
@Composable
private fun SelectionToggle(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    Box(Modifier.padding(horizontal = 2.dp).height(30.dp).clip(RoundedCornerShape(6.dp))
        .background(if (selected) SageContainer else Color.Transparent)
        .border(1.dp, if (selected) Ink.copy(alpha = .35f) else OutlineSoft, RoundedCornerShape(6.dp))
        .selectable(selected = selected, role = Role.Checkbox, onClick = { onChange(!selected) })
        .semantics { contentDescription = label }
        .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 11.sp, color = if (selected) Ink else MutedInk, maxLines = 1)
    }
}

@Composable
private fun SelectionStudioAction(icon: ImageVector, label: String, selected: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    // Keep the persistent selection highlight stable under taps and pen hover. A second
    // pressed ripple looked like the mode was repeatedly deselecting and reselecting.
    Column(Modifier.widthIn(min = 58.dp).heightIn(min = 48.dp)
        .clip(RoundedCornerShape(6.dp)).background(if (selected) SageContainer else Color.Transparent)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            enabled = enabled, onClick = onClick)
        .semantics { contentDescription = label; this.selected = selected }.padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val tint = if (!enabled) MutedInk.copy(alpha = .4f) else if (selected) Ink else MutedInk
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, lineHeight = 13.sp, color = tint, maxLines = 1)
    }
}

@Composable
private fun SelectionBarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    HoverTooltip(description) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp).semantics { contentDescription = description }) {
            Icon(icon, null, Modifier.size(17.dp), tint = Ink)
        }
    }
}
