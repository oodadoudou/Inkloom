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
import app.inkloom.settings.SelectionLauncherAction
import app.inkloom.settings.SelectionLauncherLayout
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.ReferenceTarget
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.launch

@Composable
internal fun SelectionStudioPanel(
    shape: SelectionShape, mode: SelectionMode, hasSelection: Boolean,
    launcherVisible: Boolean, launcherOrder: List<SelectionLauncherAction>,
    onLauncherVisible: (Boolean) -> Unit, onLauncherOrder: (List<SelectionLauncherAction>) -> Unit,
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
    var launcherSettingsOpen by remember { mutableStateOf(false) }
    var featherOpen by remember { mutableStateOf(false) }
    var colorRangeOpen by remember { mutableStateOf(false) }
    var strokeOpen by remember { mutableStateOf(false) }
    if (strokeOpen) SelectionStrokeDialog(strokeColor, strokeOpacity, LocalUiLanguage.current,
        onApply = { width, placement, color, opacity -> if (onStroke(width, placement, color, opacity)) strokeOpen = false },
        onDismiss = { strokeOpen = false })
    var colorRangeTolerance by rememberSaveable { mutableFloatStateOf(.18f) }
    var colorRangeAlpha by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableFloatStateOf(8f) }
    if (featherOpen) {
        val finish by rememberUpdatedState(onFinishFeather)
        DisposableEffect(Unit) { onDispose { finish(false) } }
        StudioDialog(
            onDismissRequest = { onFinishFeather(false); featherOpen = false },
            title = { Text(tr("Feather selection", "羽化选区")) },
            text = { Column {
                Text("${featherPixels.toInt()} px")
                BrushParameterSlider(featherPixels.coerceIn(0f, 128f), onFeather, valueRange = 0f..128f,
                    modifier = Modifier.semantics { contentDescription = "Selection feather radius" })
                Text(tr("Preview soft edges on the canvas.", "在画布上预览柔化边缘。"))
            } },
            confirmButton = { TextButton(onClick = { onFinishFeather(true); featherOpen = false }) { Text(tr("Apply feather", "应用羽化")) } },
            dismissButton = { TextButton(onClick = { onFinishFeather(false); featherOpen = false }) { Text(tr("Cancel feather", "取消羽化")) } },
        )
    }
    if (colorRangeOpen) StudioDialog(
        onDismissRequest = { colorRangeOpen = false },
        title = { Text(tr("Color range", "色域选择")) },
        text = { Column(Modifier.dialogContentWidth(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(Color(colorRangeTarget), RoundedCornerShape(5.dp)))
                Spacer(Modifier.width(10.dp))
                Text(tr("Uses the current foreground color", "使用当前前景色"), fontSize = 12.sp)
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
    if (optionsOpen) StudioDialog(
        onDismissRequest = { optionsOpen = false },
        title = { Text(tr("Selection settings", "选区设置")) },
        text = { Column(Modifier.dialogContentWidth(320.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                            if (shape == SelectionShape.BRUSH) {
                                Text("${tr("Brush size", "画笔大小")}  ${brushSize.toInt()} px")
                                BrushParameterSlider(brushSize, onBrushSize, valueRange = 4f..300f)
                            }
                            if (shape in setOf(SelectionShape.MAGIC_WAND, SelectionShape.SMART_BORDER)) {
                                Text("${tr("Threshold", "阈值")}  ${(threshold * 100).toInt()}%")
                                BrushParameterSlider(threshold, onThreshold, valueRange = 0f..1f)
                            }
                            run {
                                Text(tr("Reference", "参照"))
                                ReferenceTarget.entries.forEach { target ->
                                    TextButton(onClick = { onReferenceTarget(target) }, colors = ButtonDefaults.textButtonColors(containerColor = if (referenceTarget == target) SageContainer else Color.Transparent, contentColor = if (referenceTarget == target) Ink else MutedInk)) {
                                        Text(when (target) {
                                            ReferenceTarget.ACTIVE_LAYER -> tr("Current layer", "当前图层")
                                            ReferenceTarget.ALL_VISIBLE -> tr("All visible layers", "全部可见图层")
                                            ReferenceTarget.REFERENCE_LAYERS -> tr("Reference layers", "参考图层")
                                        })
                                    }
                                }
                            }
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
                            Text("${tr("Mask visibility", "蒙版可见度")}  ${(maskVisibility * 100).toInt()}%", fontSize = 12.sp)
                            BrushParameterSlider(maskVisibility, onMaskVisibility, valueRange = 0f..1f,
                                modifier = Modifier.semantics { contentDescription = "Selection mask visibility" })
                            Text("${tr("Amount", "调整量")}  ${amount.toInt()} px", fontSize = 12.sp)
                            BrushParameterSlider(amount, { amount = it }, valueRange = 1f..128f)
                            Row {
                                TextButton(onClick = { onExpand(amount) }, enabled = hasSelection) { Text(tr("Expand", "扩大")) }
                                TextButton(onClick = { onShrink(amount) }, enabled = hasSelection) { Text(tr("Shrink", "缩小")) }
                                TextButton(onClick = { onBorder(amount) }, enabled = hasSelection) { Text(tr("Border", "边界")) }
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
    if (launcherSettingsOpen) StudioDialog(
        onDismissRequest = { launcherSettingsOpen = false },
        title = { Text(tr("Selection shortcuts", "选区快捷操作")) },
        text = { Column(Modifier.dialogContentWidth(320.dp).heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
            Text(tr("Arrange shortcuts with the arrows. Essential commands remain in More.", "用箭头调整顺序；必要操作始终可从「更多」访问。"), fontSize = 11.sp)
            launcherOrder.forEachIndexed { index, action ->
                val label = when (action) {
                    SelectionLauncherAction.ADD -> tr("Add", "添加")
                    SelectionLauncherAction.SUBTRACT -> tr("Remove", "移除")
                    SelectionLauncherAction.INVERT -> tr("Invert", "反选")
                    SelectionLauncherAction.COPY -> tr("Copy & Paste", "复制并粘贴")
                    SelectionLauncherAction.FEATHER -> tr("Feather", "羽化")
                    SelectionLauncherAction.SAVED -> tr("Save & Load", "保存与载入")
                    SelectionLauncherAction.QUICK_MASK -> tr("Quick mask", "快速蒙版")
                    SelectionLauncherAction.COLOR_FILL -> tr("Color Fill", "颜色填充")
                    SelectionLauncherAction.CLEAR -> tr("Clear", "清除")
                    SelectionLauncherAction.FILL -> tr("Fill selection", "填充选区")
                    SelectionLauncherAction.TRANSFORM -> tr("Transform", "变换")
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { onLauncherOrder(SelectionLauncherLayout.move(launcherOrder, action, -1)) }, enabled = index > 0) { Text("↑") }
                    TextButton(onClick = { onLauncherOrder(SelectionLauncherLayout.move(launcherOrder, action, 1)) }, enabled = index < launcherOrder.lastIndex) { Text("↓") }
                }
            }
            TextButton(onClick = { onLauncherOrder(SelectionLauncherLayout.default) }) { Text(tr("Restore default order", "恢复默认顺序")) }
        } },
        confirmButton = { TextButton(onClick = { launcherSettingsOpen = false }) { Text(tr("Done", "完成")) } },
    )
    val studioDescription = tr("Selection studio", "选区面板")
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
        .width(minOf(640.dp, maxWidth)).onGloballyPositioned {
            measuredWidth=it.size.width/density;measuredHeight=it.size.height/density
        }.semantics { contentDescription = studioDescription }, shape = RoundedCornerShape(14.dp), color = Paper, shadowElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                listOf(SelectionShape.MAGIC_WAND, SelectionShape.FREEFORM, SelectionShape.RECTANGLE, SelectionShape.ELLIPSE).forEach { option ->
                    val label = when (option) {
                        SelectionShape.MAGIC_WAND -> tr("Automatic", "自动")
                        SelectionShape.FREEFORM -> tr("Freehand", "手绘")
                        SelectionShape.RECTANGLE -> tr("Rectangle", "矩形")
                        else -> tr("Ellipse", "椭圆")
                    }
                    val selectionDescription = tr("$label selection", "${label}选区")
                    Box(Modifier.widthIn(min = 72.dp).height(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (shape == option) SageContainer else Color.Transparent)
                        .selectable(selected = shape == option, role = Role.RadioButton,
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { onShape(option) })
                        .semantics { contentDescription = selectionDescription }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 13.sp, color = if (shape == option) Ink else MutedInk)
                    }
                }
                }
                SelectionLauncherButton(Icons.Outlined.Check, tr("Exit selection tool", "退出选区工具"), onDone)
                SelectionLauncherButton(Icons.Outlined.Close, tr("Deselect and exit", "取消选区并退出"), onDeselect)
                Box {
                    SelectionLauncherButton(Icons.Outlined.MoreHoriz, tr("Selection actions", "选区操作")) { settingsOpen = true }
                    DropdownMenu(settingsOpen, { settingsOpen = false }) {
                        if (!launcherVisible) {
                            StudioMenuItem(text = { Text(tr("Add", "添加")) }, onClick = { settingsOpen = false; onMode(SelectionMode.ADD) })
                            StudioMenuItem(text = { Text(tr("Remove", "移除")) }, onClick = { settingsOpen = false; onMode(SelectionMode.SUBTRACT) })
                            StudioMenuItem(text = { Text(tr("Invert", "反选")) }, enabled = hasSelection, onClick = { settingsOpen = false; onInvert() })
                            StudioMenuItem(text = { Text(tr("Copy & Paste", "复制并粘贴")) }, enabled = hasSelection, onClick = { settingsOpen = false; onCopy() })
                            StudioMenuItem(text = { Text(tr("Feather", "羽化")) }, enabled = hasSelection, onClick = { settingsOpen = false; featherOpen = onBeginFeather() })
                            StudioMenuItem(text = { Text(tr("Save & Load", "保存与载入")) }, onClick = { settingsOpen = false; onSaveLoad() })
                            StudioMenuItem(text = { Text(tr("Color Fill", "颜色填充")) }, onClick = { settingsOpen = false; onColorFill() })
                            StudioMenuItem(text = { Text(tr("Transform", "变换")) }, enabled = hasSelection, onClick = { settingsOpen = false; onTransform() })
                        }
                        StudioMenuItem(text = { Text(tr("Clear", "清除")) }, onClick = { settingsOpen = false; onClear() })
                        StudioMenuItem(text = { Text(tr("Cut & Paste", "剪切并粘贴")) }, enabled = hasSelection, onClick = { settingsOpen = false; onCut() })
                        StudioMenuItem(text = { Text(tr("Delete selected pixels", "删除所选像素")) }, enabled = hasSelection, onClick = { settingsOpen = false; onDelete() })
                        StudioMenuItem(text = { Text(tr("Clear outside selection", "删除选区外内容")) }, enabled = hasSelection, onClick = { settingsOpen = false; onDeleteOutside() })
                        StudioMenuItem(text = { Text(tr("Select all", "全部选择")) }, onClick = { settingsOpen = false; onSelectAll() })
                        StudioMenuItem(text = { Text(tr("Copy to clipboard", "复制到剪贴板")) }, enabled = hasSelection, onClick = { settingsOpen = false; onClipboardCopy() })
                        StudioMenuItem(text = { Text(tr("Paste", "粘贴")) }, onClick = { settingsOpen = false; onPaste() })
                        StudioMenuItem(text = { Text(tr("Paste to shown position", "粘贴至显示位置")) }, onClick = { settingsOpen = false; onPasteAtView() })
                        StudioMenuItem(text = { Text(tr("Fill selection", "填充选区")) }, enabled = hasSelection, onClick = { settingsOpen = false; onFill() })
                        StudioMenuItem(text = { Text(tr("Stroke selection…", "选区描边…")) }, enabled = hasSelection, onClick = { settingsOpen = false; strokeOpen = true })
                        StudioMenuItem(text = { Text(tr("Quick mask", "快速蒙版")) }, trailingIcon = { if (quickMask) Icon(Icons.Outlined.Check, null) }, onClick = { settingsOpen = false; onQuickMask() })
                        HorizontalDivider()
                        StudioMenuItem(text = { Text(tr("New selection", "新建选区")) }, onClick = { settingsOpen = false; onMode(SelectionMode.REPLACE) })
                        StudioMenuItem(text = { Text(tr("Intersect", "交集")) }, onClick = { settingsOpen = false; onMode(SelectionMode.INTERSECT) })
                        StudioMenuItem(text = { Text(tr("Brush selection", "画笔选区")) }, onClick = { settingsOpen = false; onShape(SelectionShape.BRUSH) })
                        StudioMenuItem(text = { Text(tr("Smart border selection", "智能边界选区")) }, onClick = { settingsOpen = false; onShape(SelectionShape.SMART_BORDER) })
                        StudioMenuItem(text = { Text(tr("Color range…", "色域选择…")) }, onClick = { settingsOpen = false; colorRangeOpen = true })
                        StudioMenuItem(text = { Text(tr("Selection settings", "选区设置")) }, onClick = { settingsOpen = false; optionsOpen = true })
                        StudioMenuItem(text = { Text(if (launcherVisible) tr("Hide actions", "隐藏操作") else tr("Show actions", "显示操作")) }, onClick = { settingsOpen = false; onLauncherVisible(!launcherVisible) })
                        StudioMenuItem(text = { Text(tr("Arrange selection shortcuts", "排列选区快捷操作")) }, onClick = { settingsOpen = false; launcherSettingsOpen = true })
                    }
                }
            }
            if (launcherVisible) {
                HorizontalDivider(Modifier.widthIn(max = 880.dp), color = OutlineSoft)
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    launcherOrder.filterNot { it in setOf(SelectionLauncherAction.QUICK_MASK, SelectionLauncherAction.CLEAR, SelectionLauncherAction.FILL) }.forEach { action -> when (action) {
                        SelectionLauncherAction.ADD -> SelectionStudioAction(Icons.Outlined.Add, tr("Add", "添加"), selected = mode == SelectionMode.ADD) { onMode(SelectionMode.ADD) }
                        SelectionLauncherAction.SUBTRACT -> SelectionStudioAction(Icons.Outlined.Remove, tr("Remove", "移除"), selected = mode == SelectionMode.SUBTRACT) { onMode(SelectionMode.SUBTRACT) }
                        SelectionLauncherAction.INVERT -> SelectionStudioAction(Icons.Outlined.Flip, tr("Invert", "反选"), enabled = hasSelection, onClick = onInvert)
                        SelectionLauncherAction.COPY -> SelectionStudioAction(Icons.Outlined.ContentCopy, tr("Copy & Paste", "复制并粘贴"), enabled = hasSelection, onClick = onCopy)
                        SelectionLauncherAction.FEATHER -> SelectionStudioAction(Icons.Outlined.BlurOn, tr("Feather", "羽化"), enabled = hasSelection) { featherOpen = onBeginFeather() }
                        SelectionLauncherAction.SAVED -> SelectionStudioAction(Icons.Outlined.Bookmarks, tr("Save & Load", "保存与载入"), onClick = onSaveLoad)
                        SelectionLauncherAction.QUICK_MASK -> SelectionStudioAction(Icons.Outlined.Layers, tr("Quick mask", "快速蒙版"), selected = quickMask, onClick = onQuickMask)
                        SelectionLauncherAction.COLOR_FILL -> SelectionStudioAction(Icons.Outlined.FormatColorFill, tr("Color Fill", "颜色填充"), selected = colorFill, onClick = onColorFill)
                        SelectionLauncherAction.CLEAR -> SelectionStudioAction(Icons.Outlined.Close, tr("Clear", "清除"), onClick = onClear)
                        SelectionLauncherAction.FILL -> SelectionStudioAction(Icons.Outlined.FormatColorFill, tr("Fill selection", "填充选区"), enabled = hasSelection, onClick = onFill)
                        SelectionLauncherAction.TRANSFORM -> SelectionStudioAction(Icons.Outlined.Transform, tr("Transform", "变换"), enabled = hasSelection, onClick = onTransform)
                    } }
                }
            }
        }
    }
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
private fun SelectionLauncherButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    HoverTooltip(description) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp).semantics { contentDescription = description }) {
            Icon(icon, null, Modifier.size(17.dp), tint = Ink)
        }
    }
}
