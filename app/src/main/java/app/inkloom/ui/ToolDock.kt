package app.inkloom.ui

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.*
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.EraserPreset
import app.inkloom.settings.ReferenceTarget
import app.inkloom.settings.ToolPaletteEntry
import app.inkloom.settings.ToolPaletteLayout
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

@Composable
private fun EraserPreset.displayName(): String = when (this) {
    EraserPreset.NORMAL_FREE -> tr("Free", "自由橡皮")
    EraserPreset.HARD -> tr("Hard", "硬橡皮")
    EraserPreset.SOFT -> tr("Soft", "软橡皮")
    EraserPreset.HARD_EDGE -> tr("Hard edge", "硬边缘")
    EraserPreset.SOFT_EDGE -> tr("Soft edge", "软边缘")
    EraserPreset.STROKE -> tr("Whole stroke", "整笔删除")
}

@Composable
private fun EraserPresetMenu(selected: EraserPreset, onSelect: (EraserPreset) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(selected.displayName()) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EraserPreset.entries.forEach { preset ->
                StudioMenuItem(
                    text = { Text(preset.displayName()) },
                    leadingIcon = { if (preset == selected) Icon(Icons.Outlined.Check, null) },
                    onClick = { expanded = false; onSelect(preset) },
                )
            }
        }
    }
}

@Composable
private fun EraserPresetPicker(selected: EraserPreset, onSelect: (EraserPreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EraserPreset.entries.toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { preset ->
                    StudioFilterChip(
                        selected = selected == preset,
                        onClick = { onSelect(preset) },
                        label = { Text(preset.displayName(), fontSize = 10.sp, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LiquifyToolControls(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Text("${tr("Force", "力度")} ${(settings.liquifyStrengthScale * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
    BrushParameterSlider(settings.liquifyStrengthScale, { onSettingsChange(settings.copy(liquifyStrengthScale = it)) }, valueRange = 0f..2f)
    Text("${tr("Distortion", "畸变")} ${(settings.liquifyDistortion * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
    BrushParameterSlider(settings.liquifyDistortion, { onSettingsChange(settings.copy(liquifyDistortion = it)) }, valueRange = 0f..1f)
    Text("${tr("Momentum", "惯性")} ${(settings.liquifyMomentum * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
    BrushParameterSlider(settings.liquifyMomentum, { onSettingsChange(settings.copy(liquifyMomentum = it)) }, valueRange = 0f..1f)
}

@Composable
internal fun GradientToolControls(settings: AppSettings, brushColor: Int, secondaryColor: Int, onBrushColor: (Int) -> Unit, onSettingsChange: (AppSettings) -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    val stops = GradientColors.normalize(settings.gradientStops).mapIndexed { index, stop ->
        if (index == 0) stop.copy(colorArgb = brushColor.toUInt().toLong()) else stop
    }
    fun updateStops(changed: List<GradientColorStop>) = onSettingsChange(settings.copy(gradientStops = GradientColors.normalize(changed)))
    fun currentDefinition() = GradientDefinition(settings.gradientKind, settings.gradientEdgeMode,
        settings.gradientReverse, settings.gradientDither, settings.gradientEllipseRatio, stops)
    var selectedPresetId by remember { mutableStateOf<String?>(null) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var nameAction by remember { mutableStateOf<String?>(null) }
    var enteredName by remember { mutableStateOf("") }
    val selectedPreset = settings.gradientPresets.presets.firstOrNull { it.id == selectedPresetId }
    val builtIns = app.inkloom.settings.BuiltInGradients.all(brushColor, secondaryColor)
    val selectedBuiltIn = builtIns.firstOrNull { it.id == selectedPresetId }
    val gradientLanguage = LocalUiLanguage.current
    fun builtInName(id: String) = when (id.removePrefix(app.inkloom.settings.BuiltInGradients.ID_PREFIX)) {
        "main-to-secondary" -> gradientLanguage.text("Drawing colour to background colour", "描绘色到背景色")
        "main-to-transparent" -> gradientLanguage.text("Drawing colour to transparent", "描绘色到透明色")
        "sky" -> gradientLanguage.text("Sky", "天空")
        "sunset" -> gradientLanguage.text("Sunset", "晚霞")
        "night-sky" -> gradientLanguage.text("Night sky", "夜空")
        "rainbow" -> gradientLanguage.text("Rainbow", "彩虹")
        else -> gradientLanguage.text("Black to white", "黑到白")
    }
    fun applyPreset(preset: app.inkloom.settings.NamedGradientPreset) {
        presetMenuOpen = false; selectedPresetId = preset.id
        val definition = preset.definition
        onBrushColor(definition.stops.first().colorArgb.toInt())
        onSettingsChange(settings.copy(gradientKind = definition.kind,
            gradientEdgeMode = definition.edgeMode, gradientReverse = definition.reverse,
            gradientDither = definition.dither, gradientEllipseRatio = definition.ellipseRatio,
            gradientStops = definition.stops))
    }
    val context = LocalContext.current
    fun editLibrary(change: () -> app.inkloom.settings.GradientPresetLibrary) {
        runCatching { change() }.onSuccess { onSettingsChange(settings.copy(gradientPresets = it)) }
            .onFailure { Toast.makeText(context, it.message ?: "Gradient preset operation failed", Toast.LENGTH_LONG).show() }
    }
    if (nameAction != null) StudioDialog(
        onDismissRequest = { nameAction = null },
        title = { Text(when (nameAction) { "rename" -> tr("Rename gradient", "重命名渐变"); "duplicate" -> tr("Duplicate gradient", "复制渐变"); else -> tr("Save gradient", "保存渐变") }) },
        text = { StudioOutlinedTextField(enteredName, { enteredName = it.take(64) }, Modifier.testTag("gradient-preset-name"), singleLine = true, label = { Text(tr("Name", "名称")) }) },
        confirmButton = { TextButton(onClick = {
            when (nameAction) {
                "rename" -> selectedPreset?.let { preset -> editLibrary { settings.gradientPresets.rename(preset.id, enteredName) } }
                "duplicate" -> selectedPreset?.let { preset -> editLibrary { settings.gradientPresets.duplicate(preset.id, enteredName) } }
                else -> editLibrary { settings.gradientPresets.add(enteredName, currentDefinition()) }
            }
            nameAction = null
        }, modifier = Modifier.testTag("gradient-preset-confirm"), enabled = enteredName.trim().isNotEmpty()) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { nameAction = null }) { Text(tr("Cancel", "取消")) } },
    )
    Text(tr("Presets", "预设"), color = MutedInk, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { presetMenuOpen = true }, modifier = Modifier.fillMaxWidth().testTag("gradient-preset-choose")) {
                Text(selectedPreset?.name ?: selectedBuiltIn?.let { builtInName(it.id) } ?: tr("Choose gradient", "选择渐变"), maxLines = 1)
            }
            DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                // Built-ins first, as CSP lists its standard set before your own materials.
                Text(tr("Built-in", "内置"), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MutedInk, fontSize = 11.sp)
                builtIns.forEach { preset -> StudioMenuItem(text = { Text(builtInName(preset.id)) }, onClick = { applyPreset(preset) }) }
                if (settings.gradientPresets.presets.isNotEmpty()) {
                    HorizontalDivider()
                    Text(tr("My gradients", "我的渐变"), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = MutedInk, fontSize = 11.sp)
                }
                settings.gradientPresets.presets.forEach { preset ->
                    StudioMenuItem(text = { Text(preset.name) }, onClick = { applyPreset(preset) })
                }
            }
        }
        TextButton(onClick = { enteredName = ""; nameAction = "save" }, modifier = Modifier.testTag("gradient-preset-save")) { Text(tr("Save", "保存")) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = { selectedPreset?.let { preset -> editLibrary { settings.gradientPresets.replace(preset.id, currentDefinition()) } } }, enabled = selectedPreset != null) { Text(tr("Update", "更新"), fontSize = 11.sp) }
        TextButton(onClick = { enteredName = selectedPreset?.name.orEmpty(); nameAction = "rename" }, enabled = selectedPreset != null) { Text(tr("Rename", "改名"), fontSize = 11.sp) }
        TextButton(onClick = { enteredName = selectedPreset?.name?.let { "$it copy" }.orEmpty(); nameAction = "duplicate" }, enabled = selectedPreset != null) { Text(tr("Copy", "复制"), fontSize = 11.sp) }
        TextButton(onClick = { selectedPreset?.let { preset -> editLibrary { settings.gradientPresets.remove(preset.id) }; selectedPresetId = null } }, enabled = selectedPreset != null) { Text(tr("Delete", "删除"), fontSize = 11.sp) }
        TextButton(onClick = onImport) { Text(tr("Import", "导入"), fontSize = 11.sp) }
        TextButton(onClick = onExport, enabled = settings.gradientPresets.presets.isNotEmpty()) { Text(tr("Export", "导出"), fontSize = 11.sp) }
    }
    HorizontalDivider()

    Text(tr("Shape", "形状"), color = MutedInk, fontSize = 11.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GradientKind.entries.forEach { kind ->
            StudioFilterChip(
                selected = settings.gradientKind == kind,
                onClick = { onSettingsChange(settings.copy(gradientKind = kind)) },
                label = { Text(when (kind) {
                    GradientKind.LINEAR -> tr("Linear", "线性")
                    GradientKind.RADIAL -> tr("Radial", "圆形")
                    GradientKind.ELLIPSE -> tr("Ellipse", "椭圆")
                    GradientKind.ANGULAR -> tr("Angular", "角度")
                    GradientKind.REFLECTED -> tr("Reflected", "对称")
                    GradientKind.DIAMOND -> tr("Diamond", "菱形")
                }, fontSize = 9.sp) },
            )
        }
    }
    Text(tr("Edge process", "边缘处理"), color = MutedInk, fontSize = 11.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GradientEdgeMode.entries.forEach { edge ->
            StudioFilterChip(
                selected = settings.gradientEdgeMode == edge,
                onClick = { onSettingsChange(settings.copy(gradientEdgeMode = edge)) },
                label = { Text(when (edge) {
                    GradientEdgeMode.CLAMP -> tr("No repeat", "不重复")
                    GradientEdgeMode.REPEAT -> tr("Repeat", "重复")
                    GradientEdgeMode.MIRROR -> tr("Reverse repeat", "反向重复")
                    GradientEdgeMode.TRANSPARENT -> tr("Do not draw", "不绘制")
                }, fontSize = 9.sp) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Reverse", "反转"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
        Switch(settings.gradientReverse, { onSettingsChange(settings.copy(gradientReverse = it)) })
        Text(tr("Dither", "抖动"), Modifier.padding(start = 8.dp), color = MutedInk, fontSize = 11.sp)
        Switch(settings.gradientDither, { onSettingsChange(settings.copy(gradientDither = it)) })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Angle snap", "角度吸附"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
        Switch(settings.gradientAngleSnapEnabled, { onSettingsChange(settings.copy(gradientAngleSnapEnabled = it)) })
    }
    if (settings.gradientAngleSnapEnabled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(5f, 15f, 30f, 45f).forEach { step ->
            StudioFilterChip(selected = settings.gradientAngleSnapDegrees == step, onClick = { onSettingsChange(settings.copy(gradientAngleSnapDegrees = step)) }, label = { Text("${step.toInt()}°", fontSize = 9.sp) })
        }
    }
    if (settings.gradientKind == GradientKind.ELLIPSE) {
        Text("${tr("Ellipse thickness", "椭圆厚度")} · ${(settings.gradientEllipseRatio * 100).toInt()}%", color = MutedInk, fontSize = 11.sp)
        BrushParameterSlider(settings.gradientEllipseRatio, { onSettingsChange(settings.copy(gradientEllipseRatio = it)) }, valueRange = .1f..1f)
    }
    HorizontalDivider()
    Text(tr("Color stops", "颜色节点"), color = MutedInk, fontSize = 11.sp)
    stops.forEachIndexed { index, stop ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${(stop.position * 100).toInt()}%", Modifier.width(34.dp), fontSize = 9.sp, color = MutedInk)
            BrushParameterSlider(
                value = stop.position,
                onValueChange = { position ->
                    if (index in 1 until stops.lastIndex) updateStops(stops.toMutableList().also { it[index] = stop.copy(position = position) })
                },
                enabled = index in 1 until stops.lastIndex,
                modifier = Modifier.weight(1f),
            )
            BrushColorPicker(stop.colorArgb.toInt(), { color ->
                if (index == 0) onBrushColor(color)
                updateStops(stops.toMutableList().also { it[index] = stop.copy(colorArgb = color.toUInt().toLong()) })
            }, square = true)
            IconButton(
                onClick = { updateStops(stops.filterIndexed { candidate, _ -> candidate != index }) },
                enabled = index in 1 until stops.lastIndex,
                modifier = Modifier.size(28.dp),
            ) { Icon(Icons.Outlined.Delete, tr("Delete color stop", "删除颜色节点"), Modifier.size(15.dp)) }
        }
    }
    OutlinedButton(
        onClick = {
            if (stops.size >= 8) return@OutlinedButton
            val gap = stops.zipWithNext().maxByOrNull { (left, right) -> right.position - left.position } ?: return@OutlinedButton
            val position = (gap.first.position + gap.second.position) / 2f
            val color = GradientColors.sample(stops, position, GradientEdgeMode.CLAMP).toUInt().toLong()
            updateStops(stops + GradientColorStop(position, color))
        },
        enabled = stops.size < 8,
        modifier = Modifier.fillMaxWidth(),
    ) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Add color stop", "添加颜色节点")) }
}

@Composable
internal fun ProcreateTopButton(
    icon: ImageVector,
    description: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    HoverTooltip(description, longPressShows = onLongClick == null) { ProcreateTopButtonBody(icon, description, selected, onClick, onLongClick) }
}

@Composable
private fun ProcreateTopButtonBody(icon: ImageVector, description: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    if (onLongClick != null) {
        Box(Modifier.size(42.dp).background(if (selected) SageContainer else Color.Transparent, RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(23.dp), tint = if (selected) Ink else MutedInk)
        }
        return
    }
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).background(if (selected) SageContainer else Color.Transparent, RoundedCornerShape(8.dp))) {
        Icon(
            icon,
            description,
            Modifier.size(23.dp),
            tint = if (selected) Ink else MutedInk,
        )
    }
}

@Composable
internal fun ToolDock(tool: EditorTool, toolPaletteLayout: String, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, referenceTarget: ReferenceTarget, onReferenceTarget: (ReferenceTarget) -> Unit, correctLineMode: CorrectLineMode, correctLineAmount: Float, onCorrectLineMode: (CorrectLineMode) -> Unit, onCorrectLineAmount: (Float) -> Unit, eraserPreset: EraserPreset, onEraserPreset: (EraserPreset) -> Unit, rulerEnabled: Boolean, rulerSnapDegrees: Float, onToggleRuler: () -> Unit, onRulerSnap: (Float) -> Unit, onResetRuler: () -> Unit, gridSnapEnabled: Boolean, gridSpacing: Float, alignmentGuidesEnabled: Boolean, onToggleGridSnap: () -> Unit, onGridSpacing: (Float) -> Unit, onToggleAlignmentGuides: () -> Unit, onResetAlignmentGuides: () -> Unit, onDragStart: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, onDragCancel: () -> Unit, layoutLocked: Boolean, vertical: Boolean, brushSize: Float, brushOpacity: Float, brushColor: Int, brushes: List<ImportedBrush>, selectedBrushId: String?, onSystemPreset: (EditorTool) -> Unit, onBrushPreset: (ImportedBrush) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit, symmetryMode: SymmetryMode, onSymmetryMode: (SymmetryMode) -> Unit, gradientControls: @Composable ColumnScope.() -> Unit, liquifyControls: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    /** Snapped to a window edge: drawn as a docked palette, flush and square, along the whole edge. */
    docked: Boolean = false) {
    var moreToolsOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentDragCancel by rememberUpdatedState(onDragCancel)
    val dragToolbarDescription = tr("Drag toolbar", "拖动工具栏")
    val dragModifier = (if (vertical) Modifier.width(32.dp).height(10.dp) else Modifier.width(10.dp).height(32.dp)).pointerInput(vertical, layoutLocked) {
        if (layoutLocked) return@pointerInput
        detectDragGestures(
            onDragStart = { currentDragStart() },
            onDragEnd = { currentDragEnd() },
            onDragCancel = { currentDragCancel() },
        ) { change, amount -> change.consume(); currentDrag(amount.x, amount.y) }
    }
    val shape = if (vertical && !docked) RoundedCornerShape(18.dp) else RoundedCornerShape(0.dp)
    Surface(
        (if (docked) modifier else modifier.shadow(if (vertical) 5.dp else 1.dp, shape)).testTag("tool-dock"),
        shape = shape,
        color = Paper,
        contentColor = Ink,
        border = CardDefaults.outlinedCardBorder(),
        tonalElevation = 0.dp,
    ) {
        val content: @Composable () -> Unit = {
            ToolDockItems(
                tool = tool,
                toolPaletteLayout = toolPaletteLayout,
                onTool = onTool,
                referenceTarget = referenceTarget,
                onReferenceTarget = onReferenceTarget,
                correctLineMode = correctLineMode,
                correctLineAmount = correctLineAmount,
                onCorrectLineMode = onCorrectLineMode,
                onCorrectLineAmount = onCorrectLineAmount,
                eraserPreset = eraserPreset,
                onEraserPreset = onEraserPreset,
                rulerEnabled = rulerEnabled,
                rulerSnapDegrees = rulerSnapDegrees,
                onToggleRuler = onToggleRuler,
                onRulerSnap = onRulerSnap,
                onResetRuler = onResetRuler,
                gridSnapEnabled = gridSnapEnabled,
                gridSpacing = gridSpacing,
                alignmentGuidesEnabled = alignmentGuidesEnabled,
                onToggleGridSnap = onToggleGridSnap,
                onGridSpacing = onGridSpacing,
                onToggleAlignmentGuides = onToggleAlignmentGuides,
                onResetAlignmentGuides = onResetAlignmentGuides,
                dragModifier = dragModifier,
                vertical = vertical,
                moreToolsOpen = moreToolsOpen,
                onMoreToolsOpen = { moreToolsOpen = it },
                controlsOpen = controlsOpen,
                onControlsOpen = { controlsOpen = it },
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                brushColor = brushColor,
                brushes = brushes,
                selectedBrushId = selectedBrushId,
                onSystemPreset = onSystemPreset,
                onBrushPreset = onBrushPreset,
                onSize = onSize,
                onOpacity = onOpacity,
                onColor = onColor,
                symmetryMode = symmetryMode,
                onSymmetryMode = onSymmetryMode,
                gradientControls = gradientControls,
                liquifyControls = liquifyControls,
            )
        }
        if (vertical) Row(verticalAlignment = Alignment.Top) {
            val railScroll = rememberScrollState()
            // Docked, the tools use the whole edge and scroll only when it is too short.
            Column(
                Modifier.width(40.dp).then(if (docked) Modifier.fillMaxHeight() else Modifier.heightIn(max = 600.dp))
                    .scrollEdgeFades(railScroll, Paper).verticalScroll(railScroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
            VerticalDivider(if (docked) Modifier.fillMaxHeight() else Modifier.height(600.dp), color = OutlineSoft)
            ProcreateSidebar(
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                gestureOwnerKey = "${tool.name}:${selectedBrushId ?: "default"}",
                onSize = onSize,
                onOpacity = onOpacity,
                onModify = { onTool(EditorTool.EYEDROPPER) },
                onUndo = onUndo,
                onRedo = onRedo,
                showGradientControls = tool == EditorTool.GRADIENT,
                gradientControls = gradientControls,
                dragModifier = dragModifier.semantics { contentDescription = dragToolbarDescription },
            )
        } else Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** Fades the clipped edge of a scrolling column so tools cut off by the window read as "scroll for more". */
private fun Modifier.scrollEdgeFades(state: androidx.compose.foundation.ScrollState, background: Color): Modifier = drawWithContent {
    drawContent()
    val fade = 28.dp.toPx().coerceAtMost(size.height / 3f)
    if (state.canScrollBackward) drawRect(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f)), endY = fade), size = androidx.compose.ui.geometry.Size(size.width, fade))
    if (state.canScrollForward) drawRect(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background), startY = size.height - fade, endY = size.height),
        topLeft = Offset(0f, size.height - fade), size = androidx.compose.ui.geometry.Size(size.width, fade))
}

@Composable
private fun ProcreateSidebar(
    brushSize: Float,
    brushOpacity: Float,
    gestureOwnerKey: String,
    onSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onModify: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    showGradientControls: Boolean,
    gradientControls: @Composable ColumnScope.() -> Unit,
    dragModifier: Modifier,
) {
    var gradientOpen by remember { mutableStateOf(false) }
    val sizeProgress = kotlin.math.sqrt(((brushSize.coerceIn(.5f, 400f) - .5f) / 399.5f).coerceIn(0f, 1f))
    Column(
        Modifier.width(64.dp).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(dragModifier) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(Color(0xFF777777), Offset(size.width * .22f, size.height * .35f), androidx.compose.ui.geometry.Size(size.width * .56f, 2f), androidx.compose.ui.geometry.CornerRadius(1f))
            }
        }
        BrushMasterSlider(
            progress = sizeProgress,
            description = tr("Brush size", "笔刷大小"),
            gestureOwnerKey = gestureOwnerKey,
            memoryKind = "size",
            display = { progress ->
                val value = .5f + progress * progress * 399.5f
                (if (value < 10f) "%.1f".format(value) else value.toInt().toString()) + " px"
            },
            onProgress = { onSize(.5f + it * it * 399.5f) },
            onStep = { direction -> onSize((brushSize + direction).coerceIn(.5f, 400f)) },
        )
        IconButton(onClick = onModify, modifier = Modifier.size(32.dp)) {
            Surface(Modifier.size(17.dp), shape = RoundedCornerShape(4.dp), color = Color.Transparent, border = androidx.compose.foundation.BorderStroke(1.5.dp, Ink)) {}
        }
        if (showGradientControls) Box {
            IconButton(onClick = { gradientOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Tune, tr("Gradient controls", "渐变参数"), Modifier.size(18.dp))
            }
            DropdownMenu(expanded = gradientOpen, onDismissRequest = { gradientOpen = false }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Gradient controls", "渐变参数"), fontWeight = FontWeight.SemiBold)
                    gradientControls()
                }
            }
        }
        BrushMasterSlider(
            progress = ((brushOpacity.coerceIn(.05f, 1f) - .05f) / .95f),
            description = tr("Brush opacity", "笔刷不透明度"),
            gestureOwnerKey = gestureOwnerKey,
            memoryKind = "opacity",
            display = { "${((.05f + it * .95f) * 100).toInt()}%" },
            onProgress = { onOpacity(.05f + it * .95f) },
            onStep = { direction -> onOpacity((brushOpacity + direction * .01f).coerceIn(.05f, 1f)) },
        )
        IconButton(onClick = onUndo, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Undo, tr("Undo", "撤销"), Modifier.size(20.dp), tint = Ink) }
        IconButton(onClick = onRedo, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Redo, tr("Redo", "重做"), Modifier.size(20.dp), tint = Ink) }
    }
}

@Composable
private fun ToolDockItems(tool: EditorTool, toolPaletteLayout: String, onTool: (EditorTool) -> Unit, referenceTarget: ReferenceTarget, onReferenceTarget: (ReferenceTarget) -> Unit, correctLineMode: CorrectLineMode, correctLineAmount: Float, onCorrectLineMode: (CorrectLineMode) -> Unit, onCorrectLineAmount: (Float) -> Unit, eraserPreset: EraserPreset, onEraserPreset: (EraserPreset) -> Unit, rulerEnabled: Boolean, rulerSnapDegrees: Float, onToggleRuler: () -> Unit, onRulerSnap: (Float) -> Unit, onResetRuler: () -> Unit, gridSnapEnabled: Boolean, gridSpacing: Float, alignmentGuidesEnabled: Boolean, onToggleGridSnap: () -> Unit, onGridSpacing: (Float) -> Unit, onToggleAlignmentGuides: () -> Unit, onResetAlignmentGuides: () -> Unit, dragModifier: Modifier, vertical: Boolean, moreToolsOpen: Boolean, onMoreToolsOpen: (Boolean) -> Unit, controlsOpen: Boolean, onControlsOpen: (Boolean) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, brushes: List<ImportedBrush>, selectedBrushId: String?, onSystemPreset: (EditorTool) -> Unit, onBrushPreset: (ImportedBrush) -> Unit, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit, symmetryMode: SymmetryMode, onSymmetryMode: (SymmetryMode) -> Unit, gradientControls: @Composable ColumnScope.() -> Unit, liquifyControls: @Composable ColumnScope.() -> Unit) {
    val dragToolbarDescription = tr("Drag toolbar", "拖动工具栏")
    var toolLibraryLayout by rememberSaveable { mutableStateOf(ToolLibraryLayout.LIST) }
    var toolLibrarySort by rememberSaveable { mutableStateOf(ToolLibrarySort.DEFAULT) }
    val libraryPresets = remember(brushes) {
        val defaults = systemBrushCatalog()
        defaults + brushes.filterNot { imported -> defaults.any { it.id == imported.id } }
    }
    Canvas(dragModifier.semantics { contentDescription = dragToolbarDescription }) {
        val lineColor = Color(0xFF8A8A8A)
        if (vertical) {
            repeat(3) { index ->
                val y = 3f + index * 3f
                drawLine(lineColor, Offset(size.width * .28f, y), Offset(size.width * .72f, y), 1f)
            }
        } else {
            repeat(3) { index ->
                val x = 3f + index * 3f
                drawLine(lineColor, Offset(x, size.height * .28f), Offset(x, size.height * .72f), 1f)
            }
        }
    }
    if (vertical) {
        ToolPaletteLayout.decode(toolPaletteLayout).forEach { entry -> when(entry) {
            ToolPaletteEntry.Separator -> HorizontalDivider(Modifier.width(30.dp).padding(vertical = 2.dp), color = OutlineSoft)
            is ToolPaletteEntry.Tool -> ToolPaletteButton(entry.tool, tool, libraryPresets, selectedBrushId, eraserPreset,
                onTool, onSystemPreset, onEraserPreset, onBrushPreset)
        } }
    } else {
        BrushPresetToolButton(EditorTool.PEN, Icons.Outlined.Brush, tr("Brush", "笔刷"), tool, libraryPresets.filterNot(BrushRoleClassifier::isEraser), selectedBrushId, eraserPreset, onTool, onSystemPreset, onEraserPreset, onBrushPreset)
        ToolButton(EditorTool.HIGHLIGHTER, Icons.Outlined.BorderColor, tr("Marker", "标记笔"), tool, onTool)
        BrushPresetToolButton(EditorTool.ERASER, InkloomIcons.Eraser, tr("Eraser", "橡皮"), tool, libraryPresets.filter(BrushRoleClassifier::isEraser), selectedBrushId, eraserPreset, onTool, onSystemPreset, onEraserPreset, onBrushPreset)
        ToolButton(EditorTool.SELECT, Icons.Outlined.Gesture, tr("Select", "选择"), tool, onTool)
    }
    Box {
        CompactToolbarIconButton(onClick = { onMoreToolsOpen(true) }, description = tr("More tools", "更多工具"), selected = rulerEnabled || gridSnapEnabled || alignmentGuidesEnabled || tool in setOf(EditorTool.EYEDROPPER, EditorTool.FILL, EditorTool.GRADIENT, EditorTool.SMUDGE, EditorTool.LIQUIFY, EditorTool.CORRECT_LINE, EditorTool.SHAPE, EditorTool.TEXT, EditorTool.NOTE, EditorTool.LINK, EditorTool.IMAGE, EditorTool.LASER)) { Icon(Icons.Outlined.Apps, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = moreToolsOpen, onDismissRequest = { onMoreToolsOpen(false) }) {
            val unsortedTools = listOf(
                Triple(EditorTool.SELECT, Icons.Outlined.TouchApp, tr("Operation", "对象操作")),
                Triple(EditorTool.EYEDROPPER, Icons.Outlined.Colorize, tr("Eyedropper", "吸管")),
                Triple(EditorTool.FILL, Icons.Outlined.FormatColorFill, tr("Paint bucket", "油漆桶")),
                Triple(EditorTool.GRADIENT, Icons.Outlined.Gradient, tr("Gradient", "渐变")),
                Triple(EditorTool.SMUDGE, Icons.Outlined.BlurOn, tr("Blend", "混色")),
                Triple(EditorTool.LIQUIFY, Icons.Outlined.Waves, tr("Liquify", "液化")),
                Triple(EditorTool.CORRECT_LINE, Icons.Outlined.AutoFixHigh, tr("Correct line", "线条修正")),
                Triple(EditorTool.RULER, Icons.Outlined.Straighten, tr("Ruler", "尺规")),
                Triple(EditorTool.FRAME, Icons.Outlined.ViewQuilt, tr("Frame border", "分格")),
                Triple(EditorTool.SHAPE, Icons.Outlined.Category, tr("Figure", "图形")),
                Triple(EditorTool.TEXT, Icons.Outlined.Title, tr("Text", "文字")),
                Triple(EditorTool.NOTE, Icons.Outlined.StickyNote2, tr("Note", "便笺")),
                Triple(EditorTool.LINK, Icons.Outlined.Link, tr("Link", "链接")),
                Triple(EditorTool.IMAGE, Icons.Outlined.Image, tr("Image", "图片")),
                Triple(EditorTool.LASER, Icons.Outlined.TrackChanges, tr("Laser pointer", "激光笔")),
            )
            val displayedTools = if (toolLibrarySort == ToolLibrarySort.NAME) unsortedTools.sortedBy { it.third.lowercase() } else unsortedTools
            Row(Modifier.width(292.dp).height(40.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Tools", "工具"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                listOf(
                    ToolLibraryLayout.LIST to Icons.Outlined.ViewList,
                    ToolLibraryLayout.GRID to Icons.Outlined.GridView,
                    ToolLibraryLayout.COMPACT to Icons.Outlined.DensitySmall,
                ).forEach { (layout, icon) ->
                    IconButton(
                        onClick = { toolLibraryLayout = layout },
                        modifier = Modifier.size(30.dp).background(if (toolLibraryLayout == layout) SageContainer else Color.Transparent, RoundedCornerShape(4.dp)),
                    ) { Icon(icon, layout.name, Modifier.size(16.dp)) }
                }
                IconButton(onClick = { toolLibrarySort = if (toolLibrarySort == ToolLibrarySort.DEFAULT) ToolLibrarySort.NAME else ToolLibrarySort.DEFAULT }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.SortByAlpha, tr("Toggle tool sorting", "切换工具排序"), Modifier.size(16.dp), tint = if (toolLibrarySort == ToolLibrarySort.NAME) Ink else MutedInk)
                }
            }
            HorizontalDivider()
            if (toolLibraryLayout == ToolLibraryLayout.LIST) displayedTools.forEach { (value, icon, label) ->
                StudioMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = {
                    onMoreToolsOpen(false)
                    onTool(value)
                    if (value == EditorTool.CORRECT_LINE) onControlsOpen(true)
                })
            } else {
                val columns = if (toolLibraryLayout == ToolLibraryLayout.COMPACT) 5 else 3
                Column(Modifier.width(292.dp).padding(5.dp)) {
                    displayedTools.chunked(columns).forEach { rowTools ->
                        Row(Modifier.fillMaxWidth()) {
                            rowTools.forEach { (value, icon, label) ->
                                Column(
                                    Modifier.weight(1f).height(if (toolLibraryLayout == ToolLibraryLayout.COMPACT) 44.dp else 68.dp)
                                        .clickable {
                                            onMoreToolsOpen(false)
                                            onTool(value)
                                            if (value == EditorTool.CORRECT_LINE) onControlsOpen(true)
                                        }
                                        .background(if (tool == value) SageContainer else Color.Transparent, RoundedCornerShape(4.dp))
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(icon, label, Modifier.size(if (toolLibraryLayout == ToolLibraryLayout.COMPACT) 22.dp else 25.dp))
                                    if (toolLibraryLayout == ToolLibraryLayout.GRID) Text(label, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                            repeat(columns - rowTools.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            HorizontalDivider()
            StudioMenuItem(
                text = { Text(tr(if (rulerEnabled) "Hide ruler" else "Show ruler", if (rulerEnabled) "隐藏直尺" else "显示直尺")) },
                leadingIcon = { Icon(Icons.Outlined.Straighten, null) },
                trailingIcon = { if (rulerEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleRuler() },
            )
            StudioMenuItem(
                text = { Text(tr(if (gridSnapEnabled) "Hide snap grid" else "Show snap grid", if (gridSnapEnabled) "隐藏吸附网格" else "显示吸附网格")) },
                leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                trailingIcon = { if (gridSnapEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleGridSnap() },
            )
            StudioMenuItem(
                text = { Text(tr(if (alignmentGuidesEnabled) "Hide alignment guides" else "Show alignment guides", if (alignmentGuidesEnabled) "隐藏对齐参考线" else "显示对齐参考线")) },
                leadingIcon = { Icon(Icons.Outlined.AlignHorizontalCenter, null) },
                trailingIcon = { if (alignmentGuidesEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleAlignmentGuides() },
            )
            HorizontalDivider()
            StudioMenuItem(
                text = { Text(tr("Symmetry", "对称") + ": " + when (symmetryMode) { SymmetryMode.NONE -> tr("Off", "关"); SymmetryMode.HORIZONTAL -> tr("Horizontal", "水平"); SymmetryMode.VERTICAL -> tr("Vertical", "垂直"); SymmetryMode.QUADRANT -> tr("Quadrant", "四象限"); SymmetryMode.RADIAL_4 -> tr("Radial 4", "径向 4"); SymmetryMode.RADIAL_6 -> tr("Radial 6", "径向 6"); SymmetryMode.RADIAL_8 -> tr("Radial 8", "径向 8") }) },
                leadingIcon = { Icon(Icons.Outlined.Flip, null) },
                onClick = {
                    val next = SymmetryMode.entries[(symmetryMode.ordinal + 1) % SymmetryMode.entries.size]
                    onSymmetryMode(next)
                },
            )
        }
    }
    if (tool != EditorTool.SELECT) Box {
        CompactToolbarIconButton(onClick = { onControlsOpen(true) }, description = tr("Tool controls", "工具参数")) { Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = controlsOpen, onDismissRequest = { onControlsOpen(false) }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${when (tool) { EditorTool.PEN -> tr("Pen", "钢笔"); EditorTool.HIGHLIGHTER -> tr("Marker", "标记笔"); EditorTool.ERASER -> tr("Eraser", "橡皮"); EditorTool.SMUDGE -> tr("Blend", "混色"); EditorTool.LIQUIFY -> tr("Liquify", "液化"); EditorTool.CORRECT_LINE -> tr("Correct line", "线条修正"); EditorTool.GRADIENT -> tr("Gradient", "渐变"); EditorTool.SELECT -> tr("Selection", "选择"); else -> tool.name.lowercase().replaceFirstChar(Char::uppercase) }} ${tr("controls", "参数")}", fontWeight = FontWeight.SemiBold)
                if (tool == EditorTool.GRADIENT) gradientControls()
                if (tool == EditorTool.LIQUIFY) liquifyControls()
                if (tool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER, EditorTool.SMUDGE, EditorTool.LIQUIFY)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Size", "大小"), Modifier.weight(1f), color = MutedInk)
                        IconButton(onClick = { onSize(brushSize - maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Remove, tr("Smaller", "减小")) }
                        Text(if (brushSize < 10f) "%.1f".format(brushSize) else brushSize.toInt().toString(), Modifier.width(38.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { onSize(brushSize + maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, tr("Larger", "增大")) }
                    }
                    if (tool !in setOf(EditorTool.ERASER, EditorTool.LIQUIFY)) {
                        Text("${tr("Opacity", "不透明度")}  ${(brushOpacity * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
                        BrushParameterSlider(value = brushOpacity, onValueChange = onOpacity, valueRange = .05f..1f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f), color = MutedInk); BrushColorPicker(brushColor, onColor) }
                    }
                }
                if (tool == EditorTool.ERASER) {
                    Text(tr("Eraser brush", "橡皮笔刷"), color = MutedInk, fontSize = 12.sp)
                    EraserPresetPicker(eraserPreset, onEraserPreset)
                }
                if (symmetryMode != SymmetryMode.NONE) {
                    HorizontalDivider()
                    Text(tr("Symmetry axis", "对称轴"), color = MutedInk, fontSize = 12.sp)
                    Text(tr("Drag the center node to move the axis. Drag the outer node to rotate it.", "拖动中心节点移动对称轴，拖动外侧节点旋转。"), color = MutedInk, fontSize = 10.sp)
                }
                if (rulerEnabled) {
                    HorizontalDivider()
                    Text(tr("Ruler angle snap", "直尺角度吸附"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1f, 5f, 15f, 45f).forEach { step ->
                            StudioFilterChip(selected = rulerSnapDegrees == step, onClick = { onRulerSnap(step) }, label = { Text("${step.toInt()}°", fontSize = 10.sp) })
                        }
                    }
                    OutlinedButton(onClick = onResetRuler, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Reset ruler", "重置直尺")) }
                }
                if (gridSnapEnabled) {
                    HorizontalDivider()
                    Text(tr("Snap grid spacing", "吸附网格间距"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(32f, 64f, 96f, 128f).forEach { spacing ->
                            StudioFilterChip(selected = gridSpacing == spacing, onClick = { onGridSpacing(spacing) }, label = { Text(spacing.toInt().toString(), fontSize = 10.sp) })
                        }
                    }
                }
                if (alignmentGuidesEnabled) {
                    HorizontalDivider()
                    Text(tr("Alignment guides", "对齐参考线"), color = MutedInk, fontSize = 12.sp)
                    Text(tr("Drag either blue line with one finger. Pen strokes and shape points snap when they start nearby.", "用单指拖动任一蓝色参考线；笔迹落点和形状端点靠近时会自动吸附。"), color = MutedInk, fontSize = 10.sp)
                    OutlinedButton(onClick = onResetAlignmentGuides, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Center alignment guides", "参考线居中")) }
                }
                if (tool == EditorTool.FILL) {
                    Text(tr("Reference", "参照范围"), color = MutedInk, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        ReferenceTarget.entries.forEach { target ->
                            StudioFilterChip(
                                selected = referenceTarget == target,
                                onClick = { onReferenceTarget(target) },
                                label = {
                                    Text(
                                        when (target) {
                                            ReferenceTarget.ALL_VISIBLE -> tr("All visible", "全部可见")
                                            ReferenceTarget.ACTIVE_LAYER -> tr("Current", "当前图层")
                                            ReferenceTarget.REFERENCE_LAYERS -> tr("Reference", "参考图层")
                                        },
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                        }
                    }
                    Text(tr("Configure draft exclusion in Tool property. If no eligible reference layer is marked, the current layer is used.", "在工具属性中设置是否排除草稿；若没有可用参考图层，则使用当前图层。"), color = MutedInk, fontSize = 9.sp)
                }
                if (tool == EditorTool.CORRECT_LINE) {
                    Text(tr("Correct line", "线条修正"), color = MutedInk, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(CorrectLineMode.entries, key = { it.name }) { mode ->
                            val correctionDescription = when (mode) {
                                CorrectLineMode.SMOOTH -> tr("Smooth line correction", "平滑线条修正")
                                CorrectLineMode.SIMPLIFY -> tr("Simplify line correction", "简化线条修正")
                                CorrectLineMode.ADJUST_WIDTH -> tr("Adjust line width correction", "调整线宽修正")
                                CorrectLineMode.REDRAW_WIDTH -> tr("Redraw vector line width", "重绘矢量线宽")
                                CorrectLineMode.CONNECT -> tr("Connect vector lines", "连接矢量线")
                                CorrectLineMode.CONTROL_POINTS -> tr("Move vector control points", "移动矢量控制点")
                            }
                            StudioFilterChip(
                                selected = correctLineMode == mode,
                                onClick = { onCorrectLineMode(mode) },
                                modifier = Modifier.semantics { contentDescription = correctionDescription },
                                label = {
                                    Text(
                                        when (mode) {
                                            CorrectLineMode.SMOOTH -> tr("Smooth", "平滑")
                                            CorrectLineMode.SIMPLIFY -> tr("Simplify", "简化")
                                            CorrectLineMode.ADJUST_WIDTH -> tr("Width", "线宽")
                                            CorrectLineMode.REDRAW_WIDTH -> tr("Redraw", "重绘线宽")
                                            CorrectLineMode.CONNECT -> tr("Connect", "连接")
                                            CorrectLineMode.CONTROL_POINTS -> tr("Points", "控制点")
                                        },
                                        fontSize = 10.sp,
                                    )
                                },
                            )
                        }
                    }
                    Text("${tr("Correction strength", "修正强度")}  ${(correctLineAmount * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
                    val correctionStrengthDescription = tr("Line correction strength", "线条修正强度")
                    BrushParameterSlider(
                        value = correctLineAmount,
                        onValueChange = onCorrectLineAmount,
                        valueRange = 0f..1f,
                        modifier = Modifier.semantics { contentDescription = correctionStrengthDescription },
                    )
                    Text(
                        when (correctLineMode) {
                            CorrectLineMode.SMOOTH -> tr("Drag across an existing line to reduce shake while preserving its endpoints.", "在已有线条上拖动，减少抖动并保留首尾端点。")
                            CorrectLineMode.SIMPLIFY -> tr("Drag across a vector line to reduce redundant control points.", "在矢量线条上拖动，减少多余控制点。")
                            CorrectLineMode.ADJUST_WIDTH -> tr("Drag across a line to change its entire width by the selected strength.", "在线条上拖动，按所选强度调整整条线宽。")
                            CorrectLineMode.REDRAW_WIDTH -> tr("Drag horizontally over a vector line to redraw its pressure-based width.", "在矢量线条上横向拖动，重绘基于压力的局部线宽。")
                            CorrectLineMode.CONNECT -> tr("Drag from one vector line endpoint to another to connect them.", "从一条矢量线端点拖到另一条端点以连接线条。")
                            CorrectLineMode.CONTROL_POINTS -> tr("Drag the nearest vector control point to reshape the line.", "拖动最近的矢量控制点以调整线条形状。")
                        } + tr(" Undo restores the original vector line.", " 撤销可恢复原始矢量线条。"),
                        color = MutedInk,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
    if (vertical) {
        CspToolbarColorSwatches(brushColor, onColor)
        HorizontalDivider(Modifier.width(30.dp).padding(vertical = 2.dp), color = OutlineSoft)
    } else {
        BrushColorPicker(brushColor, onColor, square = true)
    }
}

@Composable
private fun ToolPaletteButton(value: EditorTool, selected: EditorTool, brushes: List<ImportedBrush>, selectedBrushId: String?,
                              eraserPreset: EraserPreset, onTool: (EditorTool) -> Unit, onSystemPreset: (EditorTool) -> Unit,
                              onEraserPreset: (EraserPreset) -> Unit, onBrushPreset: (ImportedBrush) -> Unit) {
    if (value == EditorTool.PEN) {
        BrushPresetToolButton(value, Icons.Outlined.Brush, tr("Brush", "笔刷"), selected,
            brushes.filterNot(BrushRoleClassifier::isEraser), selectedBrushId, eraserPreset,
            onTool, onSystemPreset, onEraserPreset, onBrushPreset)
        return
    }
    if (value == EditorTool.ERASER) {
        BrushPresetToolButton(value, InkloomIcons.Eraser, tr("Eraser tool", "橡皮工具"), selected,
            brushes.filter(BrushRoleClassifier::isEraser), selectedBrushId, eraserPreset,
            onTool, onSystemPreset, onEraserPreset, onBrushPreset)
        return
    }
    val icon = when(value) {
        EditorTool.HIGHLIGHTER -> Icons.Outlined.BorderColor
        EditorTool.EYEDROPPER -> Icons.Outlined.Colorize
        EditorTool.SELECT -> Icons.Outlined.Gesture
        EditorTool.SHAPE -> Icons.Outlined.Category
        EditorTool.TEXT -> Icons.Outlined.Title
        EditorTool.NOTE -> Icons.Outlined.StickyNote2
        EditorTool.LINK -> Icons.Outlined.Link
        EditorTool.IMAGE -> Icons.Outlined.Image
        EditorTool.LASER -> Icons.Outlined.TrackChanges
        EditorTool.FILL -> Icons.Outlined.FormatColorFill
        EditorTool.GRADIENT -> Icons.Outlined.Gradient
        EditorTool.SMUDGE -> Icons.Outlined.BlurOn
        EditorTool.LIQUIFY -> Icons.Outlined.Waves
        EditorTool.CORRECT_LINE -> Icons.Outlined.AutoFixHigh
        EditorTool.RULER -> Icons.Outlined.Straighten
        EditorTool.FRAME -> Icons.Outlined.ViewQuilt
        EditorTool.PEN, EditorTool.ERASER -> error("handled above")
    }
    val label = when(value) {
        EditorTool.HIGHLIGHTER -> tr("Marker", "标记笔")
        EditorTool.EYEDROPPER -> tr("Eyedropper", "吸管")
        EditorTool.SELECT -> tr("Select", "选择")
        EditorTool.SHAPE -> tr("Shape", "形状")
        EditorTool.TEXT -> tr("Text", "文字")
        EditorTool.NOTE -> tr("Note", "便笺")
        EditorTool.LINK -> tr("Link", "链接")
        EditorTool.IMAGE -> tr("Image", "图片")
        EditorTool.LASER -> tr("Laser pointer", "激光笔")
        EditorTool.FILL -> tr("Paint bucket", "油漆桶")
        EditorTool.GRADIENT -> tr("Gradient", "渐变")
        EditorTool.SMUDGE -> tr("Blend", "混色")
        EditorTool.LIQUIFY -> tr("Liquify", "液化")
        EditorTool.CORRECT_LINE -> tr("Correct line", "线条修正")
        EditorTool.RULER -> tr("Ruler", "尺规")
        EditorTool.FRAME -> tr("Frame border", "分格")
        EditorTool.PEN, EditorTool.ERASER -> ""
    }
    ToolButton(value, icon, label, selected, onTool)
}

@Composable
private fun CompactToolbarIconButton(onClick: () -> Unit, description: String, selected: Boolean = false, content: @Composable () -> Unit) =
    // Hover only: the rail keeps long-press for its own gestures.
    HoverTooltip(description, longPressShows = false) {
    Box(
        Modifier.size(32.dp).background(if (selected) SageContainer else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides Ink) { Box(Modifier.semantics { contentDescription = description }) { content() } }
    }
    }

@Composable
internal fun BrushColorPicker(color: Int, onColor: (Int) -> Unit, square: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    var quickPicker by remember { mutableStateOf(false) }
    var width by rememberSaveable { mutableFloatStateOf(360f) }
    Box {
        val shape = if (square) RoundedCornerShape(2.dp) else CircleShape
        Box(Modifier.size(28.dp).clip(shape).background(Color(color)).border(1.dp, OutlineSoft, shape)
            .combinedClickable(onClick = { open = true }, onLongClick = { quickPicker = true }))
        DropdownMenu(open, { open = false }, Modifier.width(width.dp).background(Paper).padding(6.dp)) {
            ColorStudioPanel(color, LocalUiLanguage.current, {}, onColor, { delta -> width = (width + delta).coerceIn(280f, 680f) })
        }
    }
    if (quickPicker) StandardColorPickerDialog(color, LocalUiLanguage.current,
        onConfirm = { onColor(it); quickPicker = false }, onDismiss = { quickPicker = false })
}

@Composable
private fun CspToolbarColorSwatches(color: Int, onColor: (Int) -> Unit) {
    val description = tr("Foreground and background colors", "前景色和背景色")
    Box(
        Modifier.size(width = 38.dp, height = 43.dp).semantics { contentDescription = description },
    ) {
        Box(
            Modifier.align(Alignment.BottomEnd).size(27.dp).background(Color.White).border(1.dp, Ink, RoundedCornerShape(2.dp)),
        )
        Box(Modifier.align(Alignment.TopStart)) { BrushColorPicker(color, onColor, square = true) }
        Box(
            Modifier.align(Alignment.BottomStart).size(width = 32.dp, height = 7.dp).border(1.dp, OutlineSoft).background(
                Brush.horizontalGradient(listOf(Color.White, Color(0xFFD0D0D0), Color.White, Color(0xFFD0D0D0))),
            ),
        )
    }
}

private data class HslColor(val hue: Float, val saturation: Float, val lightness: Float)

private fun colorToHsl(color: Int): HslColor {
    val red = AndroidColor.red(color) / 255f
    val green = AndroidColor.green(color) / 255f
    val blue = AndroidColor.blue(color) / 255f
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hueSector = when {
        delta == 0f -> 0f
        maximum == red -> ((green - blue) / delta) % 6f
        maximum == green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    }
    return HslColor((hueSector * 60f).let { if (it < 0f) it + 360f else it }, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
}

private fun hslToColor(alpha: Int, hue: Float, saturation: Float, lightness: Float): Int {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val section = normalizedHue / 60f
    val intermediate = chroma * (1f - kotlin.math.abs(section % 2f - 1f))
    val (rawRed, rawGreen, rawBlue) = when (section.toInt()) {
        0 -> Triple(chroma, intermediate, 0f)
        1 -> Triple(intermediate, chroma, 0f)
        2 -> Triple(0f, chroma, intermediate)
        3 -> Triple(0f, intermediate, chroma)
        4 -> Triple(intermediate, 0f, chroma)
        else -> Triple(chroma, 0f, intermediate)
    }
    val match = lightness - chroma / 2f
    return AndroidColor.argb(
        alpha,
        ((rawRed + match) * 255f).toInt().coerceIn(0, 255),
        ((rawGreen + match) * 255f).toInt().coerceIn(0, 255),
        ((rawBlue + match) * 255f).toInt().coerceIn(0, 255),
    )
}

@Composable
private fun ToolButton(value: EditorTool, icon: ImageVector, label: String, selected: EditorTool, onTool: (EditorTool) -> Unit) =
    HoverTooltip(label, longPressShows = false) {
        Box(Modifier.size(32.dp).background(if (value == selected) SageContainer else Color.Transparent).clickable { onTool(value) }, contentAlignment = Alignment.Center) {
            Icon(icon, label, Modifier.size(19.dp), tint = Ink)
        }
    }

@Composable
private fun BrushPresetToolButton(
    value: EditorTool,
    icon: ImageVector,
    label: String,
    selected: EditorTool,
    presets: List<ImportedBrush>,
    selectedBrushId: String?,
    eraserPreset: EraserPreset,
    onTool: (EditorTool) -> Unit,
    onSystemPreset: (EditorTool) -> Unit,
    onEraserPreset: (EraserPreset) -> Unit,
    onPreset: (ImportedBrush) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(32.dp)
                .background(if (value == selected) SageContainer else Color.Transparent)
                .clickable {
                    if (value == selected) open = true else onTool(value)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(19.dp), tint = Ink)
            Canvas(Modifier.align(Alignment.BottomEnd).size(8.dp).padding(1.dp)) {
                val path = Path().apply { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width, 0f); close() }
                drawPath(path, MutedInk)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 172.dp, max = 224.dp).background(Paper),
        ) {
            Text(
                if (value == EditorTool.ERASER) tr("Eraser sub tools", "橡皮子工具") else tr("Brush sub tools", "笔刷子工具"),
                Modifier.fillMaxWidth().height(25.dp).background(Paper).padding(horizontal = 7.dp).wrapContentHeight(Alignment.CenterVertically),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (value == EditorTool.PEN) {
                Row(
                    Modifier.fillMaxWidth().height(38.dp).background(if (selectedBrushId == null && selected == value) SageContainer else Color.Transparent)
                        .clickable { open = false; onSystemPreset(EditorTool.PEN) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("System pen", "系统钢笔"), fontSize = 11.sp)
                }
            } else {
                EraserPreset.entries.forEach { preset ->
                    Row(
                        Modifier.fillMaxWidth().height(36.dp).background(if (selectedBrushId == null && eraserPreset == preset && selected == value) SageContainer else Color.Transparent)
                            .clickable { open = false; onSystemPreset(EditorTool.ERASER); onEraserPreset(preset) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoFixOff, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(preset.displayName(), fontSize = 11.sp)
                    }
                }
            }
            if (presets.isNotEmpty()) {
                HorizontalDivider()
                Text(tr("Brush library", "笔刷库"), Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 7.dp).wrapContentHeight(Alignment.CenterVertically), color = MutedInk, fontSize = 9.sp)
            }
            presets.sortedWith(compareBy<ImportedBrush> { it.groupName }.thenBy { it.sortOrder }).forEach { brush ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(if (brush.id == selectedBrushId) SageContainer else Color.Transparent)
                        .clickable { open = false; onPreset(brush) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrushTipPreview(brush, Modifier.width(58.dp).height(28.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(brush.name, maxLines = 1, fontSize = 11.sp)
                        if (brush.groupName.isNotBlank()) Text(BrushGroupOperations.displayName(brush.groupName), maxLines = 1, fontSize = 9.sp, color = MutedInk)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FillOptionsLauncher(
    subTool: FillSubTool,
    sensitivity: Float,
    closeGapPixels: Float,
    areaScalingPixels: Float,
    targetMode: app.inkloom.settings.FillTargetMode,
    targetColor: Int,
    expansionShape: app.inkloom.settings.FillExpansionShape,
    antiAlias: Boolean,
    onSensitivity: (Float) -> Unit,
    onCloseGap: (Float) -> Unit,
    onAreaScaling: (Float) -> Unit,
    onTargetMode: (app.inkloom.settings.FillTargetMode) -> Unit,
    onTargetColor: (Int) -> Unit,
    onExpansionShape: (app.inkloom.settings.FillExpansionShape) -> Unit,
    onAntiAlias: (Boolean) -> Unit,
    onSubTool: (FillSubTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(modifier, color = Paper, shape = RoundedCornerShape(10.dp), border = CardDefaults.outlinedCardBorder(), shadowElevation = 5.dp) {
        Row(Modifier.height(42.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FormatColorFill, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(when (subTool) { FillSubTool.BUCKET -> tr("Bucket", "油漆桶"); FillSubTool.ENCLOSE_AND_FILL -> tr("Enclose", "包围并填充"); FillSubTool.LASSO_FILL -> tr("Lasso", "套索填充"); FillSubTool.LEFTOVER_PEN -> tr("Leftover pen", "漏涂笔") }, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Text("${tr("Close gap", "闭合缝隙")} ${closeGapPixels.toInt()} px", color = MutedInk, fontSize = 10.sp)
            Spacer(Modifier.width(8.dp))
            Text("${tr("Area scaling", "区域缩放")} ${areaScalingPixels.toInt()} px", color = MutedInk, fontSize = 10.sp)
            Box {
                CompactToolbarIconButton({ expanded = true }, tr("Advanced fill settings", "高级填充设置")) { Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp)) }
                DropdownMenu(expanded, { expanded = false }, Modifier.width(300.dp)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(tr("Fill sub tool", "填充子工具"), fontSize = 11.sp)
                        FillSubTool.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().height(34.dp).clickable { onSubTool(option) }, verticalAlignment = Alignment.CenterVertically) {
                                StudioRadioButton(subTool == option, { onSubTool(option) })
                                Text(when (option) { FillSubTool.BUCKET -> tr("Refer and fill", "参照并填充"); FillSubTool.ENCLOSE_AND_FILL -> tr("Enclose and fill", "包围并填充"); FillSubTool.LASSO_FILL -> tr("Lasso fill", "套索填充"); FillSubTool.LEFTOVER_PEN -> tr("Leftover pen", "漏涂笔") }, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider()
                        Text(tr("Fill target", "填充目标"), fontSize = 11.sp)
                        app.inkloom.settings.FillTargetMode.entries.forEach { option ->
                            Row(Modifier.fillMaxWidth().height(34.dp).clickable { onTargetMode(option) }, verticalAlignment = Alignment.CenterVertically) {
                                StudioRadioButton(targetMode == option, { onTargetMode(option) })
                                Text(when (option) {
                                    app.inkloom.settings.FillTargetMode.TOUCHED_COLOR -> tr("Touched color", "触点颜色")
                                    app.inkloom.settings.FillTargetMode.TRANSPARENT_ONLY -> tr("Transparent area", "透明区域")
                                    app.inkloom.settings.FillTargetMode.SPECIFIED_COLOR -> tr("Specified color", "指定颜色")
                                }, fontSize = 11.sp)
                            }
                        }
                        if (targetMode == app.inkloom.settings.FillTargetMode.SPECIFIED_COLOR) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Target color", "目标颜色"), Modifier.weight(1f), fontSize = 11.sp)
                                BrushColorPicker(targetColor, onTargetColor, square = true)
                            }
                        }
                        HorizontalDivider()
                        Text("${tr("Tolerance", "容差")} ${(sensitivity * 100).toInt()}%", fontSize = 11.sp)
                        BrushParameterSlider(sensitivity, onSensitivity, valueRange = 0f..1f)
                        Text("${tr("Close gap", "闭合缝隙")} ${closeGapPixels.toInt()} px", fontSize = 11.sp)
                        BrushParameterSlider(closeGapPixels, onCloseGap, valueRange = 0f..64f)
                        Text("${tr("Area scaling", "区域缩放")} ${areaScalingPixels.toInt()} px", fontSize = 11.sp)
                        BrushParameterSlider(areaScalingPixels, onAreaScaling, valueRange = -64f..64f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Anti-aliasing", "抗锯齿"), Modifier.weight(1f), fontSize = 11.sp)
                            Switch(antiAlias, onAntiAlias)
                        }
                        Text(tr("Scaling corners", "扩张边角"), fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            app.inkloom.settings.FillExpansionShape.entries.forEach { shape ->
                                StudioFilterChip(
                                    selected = expansionShape == shape,
                                    onClick = { onExpansionShape(shape) },
                                    label = { Text(if (shape == app.inkloom.settings.FillExpansionShape.ROUND) tr("Round", "圆形") else tr("Rectangle", "矩形")) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
