package app.inkloom.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.inkloom.color.MixingBoard
import app.inkloom.color.ColorHarmony
import app.inkloom.color.ColorRelations
import app.inkloom.settings.MixingColorBarStore
import app.inkloom.settings.ColorSetLibrary
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

@Composable
internal fun ColorStudioPanel(
    color: Int,
    language: UiLanguage,
    onColorPreview: (Int) -> Unit,
    onColorCommit: (Int) -> Unit,
    onResizeWidth: ((Float) -> Unit)?,
    colorPair: ColorPairControls? = null,
    fixedTab: Int? = null,
    onSwapColors: (() -> Unit)? = null,
    availableHeight: androidx.compose.ui.unit.Dp? = null,
) {
    val context = LocalContext.current
    if (fixedTab == null) RelayoutPaletteOnWindowResize()
    val state = LocalColorStudioState.current
    val tab = fixedTab ?: state.tab
    val nearbyFooterHeight = if (tab == 3) 38.dp else 0.dp
    val contentHeight = availableHeight?.let { (it - 22.dp - nearbyFooterHeight).coerceAtLeast(100.dp) }
        ?: (LocalConfiguration.current.screenHeightDp - 200).coerceIn(160, 420).dp - nearbyFooterHeight
    val density = LocalDensity.current
    var visibleContentHeight by remember { mutableStateOf(contentHeight) }
    var draft by if (fixedTab != null) state.draftState(colorPair?.secondarySelected == true, color) else remember { mutableStateOf(ColorPickerDraft.fromColor(color)) }
    var pickerStart by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(color) { draft = draft.withColor(color) }
    val currentColorCommit by rememberUpdatedState(onColorCommit)
    fun pick(value: Int) { draft = draft.withColor(value); currentColorCommit(value) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column(Modifier.fillMaxWidth()) {
        if (fixedTab != null) Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (colorPair?.secondarySelected == true) tr("Secondary color", "副色") else tr("Primary color", "主色"), color = MutedInk, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
        if (fixedTab == null) Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            listOf(tr("Wheel", "色环"), tr("Palettes", "色板"), tr("Mix", "混色"), tr("Nearby", "近似色")).forEachIndexed { index, label ->
                Box(Modifier.weight(1f).height(40.dp)
                    .background(if (tab == index) SageContainer else Color.Transparent)
                    .semantics { selected = tab == index }
                    .clickable { state.selectTab(index) }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (tab == index) Ink else MutedInk, fontSize = 12.sp)
                }
            }
        }
        // Keep tab targets fixed: a shorter page must not relocate the popup under the pen.
        // The surrounding popup scroll container can clip a larger measured child after IME resize.
        Box(Modifier.fillMaxWidth().height(contentHeight).onGloballyPositioned { visibleContentHeight = with(density) { it.boundsInWindow().height.toDp() } }) {
            key(tab) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> CspColorPanel(color, onColorPreview, ::pick, onResizeWidth = onResizeWidth, showHeader = false, initialDraft = draft, onDraftChange = { draft = it }, maximumHeight = visibleContentHeight, colorPair = colorPair, onSwapColors = onSwapColors, language = language, onOpenQuickPicker = { pickerStart = it })
                        1 -> ColorSetPanel(color, language, visibleContentHeight, ::pick, onOpenQuickPicker = { pickerStart = it })
                        2 -> {
                            val mixingView = remember(context, state) { ColorMixingView(context, state.mixing) }
                            MixingPalette(color, language, ::pick, mixingView, state.mixingTool, state::selectTool, state.mixingRadius, state::setRadius, onOpenQuickPicker = { pickerStart = it })
                        }
                        3 -> NearbyColorPalette(draft, language, ::pick, onOpenQuickPicker = { pickerStart = it })
                    }
                }
            }
        }
        if (tab == 3) Row(Modifier.fillMaxWidth().height(nearbyFooterHeight).padding(start = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(Color(color)).border(.5.dp, OutlineSoft)
                .semantics { contentDescription = tr("Edit current color", "编辑当前颜色") }
                .combinedClickable(onClick = { pickerStart = color }, onLongClick = { pickerStart = color }))
            Spacer(Modifier.width(8.dp))
            Text(tr("Current color", "当前颜色"), color = MutedInk, fontSize = 11.sp)
        }
    }
    pickerStart?.let { start -> StandardColorPickerDialog(start, language, onConfirm = { pick(it); pickerStart = null }, onDismiss = { pickerStart = null }) }
}

/** Compose 1.8 fixes a Popup's window dimensions after measuring, but does not remeasure it
 * when adjustPan's visible display frame grows again after a dialog closes the IME. Observe the
 * Activity's real frame; resizing only the popup preserves all palette and mixing state. */
@Composable
private fun RelayoutPaletteOnWindowResize() {
    val popupView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(popupView) {
        var context: android.content.Context? = popupView.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        val decor = (context as? android.app.Activity)?.window?.decorView
        val popup = popupView.rootView
        val previous = android.graphics.Rect()
        val current = android.graphics.Rect()
        decor?.getWindowVisibleDisplayFrame(previous)
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            decor?.getWindowVisibleDisplayFrame(current)
            if (current != previous) {
                previous.set(current)
                if (popup !== decor && popup.isAttachedToWindow) popup.requestLayout()
            }
        }
        decor?.viewTreeObserver?.addOnGlobalLayoutListener(listener)
        onDispose { decor?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(listener) }
    }
}

/** Fixed reference prevents selecting a tile from shifting every following tile under the pen. */
@Composable
private fun NearbyColorPalette(draft: ColorPickerDraft, language: UiLanguage, onPick: (Int) -> Unit, onOpenQuickPicker: (Int) -> Unit) {
    var mode by remember { mutableIntStateOf(0) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column {
        Row(Modifier.fillMaxWidth().height(34.dp)) {
            listOf(tr("Nearby", "近似色"), tr("Harmony", "色彩和谐"), tr("Intermediate", "中间色")).forEachIndexed { index, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(if (mode == index) SageContainer else Color.Transparent)
                        .semantics { selected = mode == index; contentDescription = label }.clickable { mode = index },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (mode == index) Ink else MutedInk, fontSize = 10.sp) }
            }
        }
        when (mode) {
            0 -> NearbyMatrix(draft, language, onPick, onOpenQuickPicker)
            1 -> HarmonyPalette(draft, language, onPick, onOpenQuickPicker)
            else -> IntermediateColorPalette(draft, language, onPick, onOpenQuickPicker)
        }
    }
}

@Composable
private fun NearbyMatrix(draft: ColorPickerDraft, language: UiLanguage, onPick: (Int) -> Unit, onOpenQuickPicker: (Int) -> Unit) {
    val color = draft.color
    var reference by remember { mutableStateOf(draft) }
    var saturationRange by remember { mutableFloatStateOf(.4f) }
    var valueRange by remember { mutableFloatStateOf(.4f) }
    val hsv = remember(reference) { floatArrayOf(reference.hue, reference.saturation, reference.value) }
    val selectedCell = remember(reference, color, saturationRange, valueRange) {
        (0 until 49).filter { nearbyColor(hsv, it % 7, it / 7, saturationRange, valueRange) == color }
            .minByOrNull { kotlin.math.abs(it % 7 - 3) + kotlin.math.abs(it / 7 - 3) }
    }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Saturation × brightness (HSV)", "饱和度 × 明度（HSV）"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
            StudioIcon(Icons.Outlined.MyLocation, tr("Use current color as reference", "以当前颜色为基准")) { reference = draft }
        }
        repeat(7) { row ->
            Row(Modifier.fillMaxWidth().height(32.dp)) {
                repeat(7) { column ->
                    val swatch = nearbyColor(hsv, column, row, saturationRange, valueRange)
                    val chosen = selectedCell == row * 7 + column
                    val label = tr("Nearby color ${row + 1}, ${column + 1}", "近似色 ${row + 1}，${column + 1}")
                    Box(Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(Color(swatch))
                        .border(if (chosen) 2.dp else 0.dp, if (chosen) Ink else Color.Transparent)
                        .semantics { contentDescription = label; selected = chosen }
                        .combinedClickable(onClick = { onPick(swatch) }, onLongClick = { onOpenQuickPicker(swatch) }))
                }
            }
        }
        RangeControl(tr("Saturation range", "饱和度范围"), saturationRange) { saturationRange = it }
        RangeControl(tr("Brightness range", "明度范围"), valueRange) { valueRange = it }
    }
}

@Composable
private fun HarmonyPalette(draft: ColorPickerDraft, language: UiLanguage, onPick: (Int) -> Unit, onOpenQuickPicker: (Int) -> Unit) {
    var mode by remember { mutableStateOf(ColorHarmony.COMPLEMENTARY) }
    var menu by remember { mutableStateOf(false) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    fun label(value: ColorHarmony) = when (value) {
        ColorHarmony.COMPLEMENTARY -> tr("Complementary", "互补色")
        ColorHarmony.SPLIT_COMPLEMENTARY -> tr("Split complementary", "分裂互补色")
        ColorHarmony.ANALOGOUS -> tr("Analogous", "类似色")
        ColorHarmony.TRIADIC -> tr("Triadic", "三角色")
        ColorHarmony.TETRADIC -> tr("Tetradic", "四色")
    }
    val colors = remember(draft.hue, draft.saturation, draft.value, mode) {
        ColorRelations.harmony(draft.hue, draft.saturation, draft.value, mode)
    }
    Column {
        Box {
            Row(
                Modifier.fillMaxWidth().height(36.dp).clickable { menu = true }.semantics { contentDescription = tr("Harmony mode", "和谐模式") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(mode), Modifier.weight(1f).padding(horizontal = 6.dp), color = Ink, fontSize = 11.sp)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = MutedInk)
            }
            ColorPaletteDropdownMenu(menu, { menu = false }) {
                ColorHarmony.entries.forEach { candidate ->
                    DropdownMenuItem(text = { Text(label(candidate)) }, trailingIcon = { if (candidate == mode) Icon(Icons.Outlined.Check, null) }, onClick = { mode = candidate; menu = false })
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(94.dp)) {
            colors.forEachIndexed { index, color ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(Color(color)).border(.5.dp, OutlineSoft)
                        .semantics { contentDescription = tr("Harmony color ${index + 1}", "和谐色 ${index + 1}") }
                        .combinedClickable(onClick = { onPick(color) }, onLongClick = { onOpenQuickPicker(color) }),
                )
            }
        }
        Text(tr("Harmony keeps the current saturation and brightness.", "和谐色保留当前饱和度和明度。"), Modifier.padding(6.dp), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun IntermediateColorPalette(draft: ColorPickerDraft, language: UiLanguage, onPick: (Int) -> Unit, onOpenQuickPicker: (Int) -> Unit) {
    fun defaults() = listOf(
        draft.color,
        ColorRelations.harmony(draft.hue, draft.saturation, draft.value, ColorHarmony.COMPLEMENTARY).last(),
        0xff151515.toInt(),
        0xfff2f2f2.toInt(),
    )
    var corners by remember { mutableStateOf(defaults()) }
    var cornerToEdit by remember { mutableStateOf<Int?>(null) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column {
        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Tap a corner to replace it with the current color", "点击角点，用当前颜色替换"), Modifier.weight(1f).padding(horizontal = 4.dp), color = MutedInk, fontSize = 9.sp)
            StudioIcon(Icons.Outlined.Refresh, tr("Reset intermediate colors", "重置中间色")) { corners = defaults() }
        }
        Row(Modifier.fillMaxWidth().height(32.dp)) {
            corners.forEachIndexed { index, value ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(1.dp).background(Color(value)).border(.5.dp, OutlineSoft)
                        .semantics { contentDescription = tr("Intermediate corner ${index + 1}", "中间色角点 ${index + 1}") }
                        .combinedClickable(
                            onClick = { corners = corners.toMutableList().also { it[index] = draft.color } },
                            onLongClick = { cornerToEdit = index },
                        ),
                )
            }
        }
        repeat(7) { row ->
            Row(Modifier.fillMaxWidth().height(32.dp)) {
                repeat(7) { column ->
                    val mixed = ColorRelations.cornerMix(corners[0], corners[1], corners[2], corners[3], column, row, 7)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(Color(mixed))
                            .semantics { contentDescription = tr("Intermediate color ${row + 1}, ${column + 1}", "中间色 ${row + 1}，${column + 1}") }
                            .combinedClickable(onClick = { onPick(mixed) }, onLongClick = { onOpenQuickPicker(mixed) }),
                    )
                }
            }
        }
    }
    cornerToEdit?.let { index -> StandardColorPickerDialog(corners[index], language,
        onConfirm = { chosen -> corners = corners.toMutableList().also { it[index] = chosen }; onPick(chosen); cornerToEdit = null },
        onDismiss = { cornerToEdit = null }) }
}

internal fun nearbyColor(hsv: FloatArray, column: Int, row: Int, saturationRange: Float, valueRange: Float): Int =
    AndroidColor.HSVToColor(floatArrayOf(hsv[0], (hsv[1] + (column - 3) / 3f * saturationRange).coerceIn(0f, 1f),
        (hsv[2] + (3 - row) / 3f * valueRange).coerceIn(0f, 1f)))

@Composable
private fun RangeControl(label: String, value: Float, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(80.dp), color = MutedInk, fontSize = 10.sp)
        BrushParameterSlider(value, onValue, Modifier.weight(1f).semantics { contentDescription = label }, valueRange = .05f..1f)
        Text("${(value * 100).toInt()}%", Modifier.width(36.dp), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun MixingPalette(
    color: Int, language: UiLanguage, onPick: (Int) -> Unit, view: ColorMixingView,
    tool: MixingBoard.Tool, onTool: (MixingBoard.Tool) -> Unit,
    radius: Float, onRadius: (Float) -> Unit,
    onOpenQuickPicker: (Int) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE) }
    var canUndo by remember { mutableStateOf(view.board.canUndo) }
    var canRedo by remember { mutableStateOf(view.board.canRedo) }
    var storageError by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(prefs.getBoolean("mixShowColors", true)) }
    var background by remember { mutableIntStateOf(prefs.getInt("mixBackground", 4).coerceIn(0, 4)) }
    val tones = listOf<Int?>(null, 0xffffffff.toInt(), 0xff000000.toInt(), 0xff808080.toInt(), 0xffeeeeea.toInt())
    val paletteStore = remember { MixingColorBarStore(context) }
    var library by remember { mutableStateOf(runCatching { paletteStore.load() }.getOrElse { storageError = true; ColorSetLibrary() }) }
    var editingSwatchId by remember { mutableStateOf<String?>(null) }
    var resetColors by remember { mutableStateOf(false) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column {
        AndroidView(factory = { view },
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clipToBounds().border(.5.dp, OutlineSoft)
                .semantics { contentDescription = tr("Color mixing board", "混色画板") },
            update = {
                it.ink = color; it.tool = tool; it.brushRadius = radius; it.onPick = onPick
                it.backgroundTone = tones[background]
                it.contentDescription = tr("Color mixing board", "混色画板")
                it.onHistory = { undo, redo -> canUndo = undo; canRedo = redo }
                it.onStorageError = { storageError = true }
            })
        if (showColors) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                library.activeSet?.swatches.orEmpty().forEach { swatch ->
                    val label = tr("Mixing color #%06X".format(swatch.color and 0xffffff), "混色色块 #%06X".format(swatch.color and 0xffffff))
                    Box(Modifier.size(32.dp).padding(horizontal = 3.dp, vertical = 7.dp)
                    .background(Color(swatch.color), RoundedCornerShape(2.dp)).border(if (library.activeSet?.selectedSwatchId == swatch.id) 1.5.dp else .5.dp, if (library.activeSet?.selectedSwatchId == swatch.id) Ink else OutlineSoft)
                    .semantics { contentDescription = label }
                    .combinedClickable(onClick = {
                        runCatching { paletteStore.update { it.selectSwatch(MixingColorBarStore.SET_ID, swatch.id) } }.onSuccess { library = it }.onFailure { storageError = true }
                        onPick(swatch.color)
                    }, onLongClick = { editingSwatchId = swatch.id }))
                }
            }
            StudioIcon(Icons.Outlined.Add, tr("Add mixing color", "添加混色色块")) {
                runCatching { paletteStore.update { it.insertSwatch(MixingColorBarStore.SET_ID, color, beforeIndex = it.activeSet?.swatches?.size) } }.onSuccess { library = it }.onFailure { storageError = true }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 5) {
            Box(Modifier.size(32.dp).padding(7.dp).background(Color(color)).border(.5.dp, OutlineSoft)
                .semantics { contentDescription = tr("Edit current color", "编辑当前颜色") }
                .combinedClickable(onClick = { onOpenQuickPicker(color) }, onLongClick = { onOpenQuickPicker(color) }))
            StudioIcon(Icons.Outlined.DeleteOutline, tr("Clear mixing board", "清空混色画板")) { view.clear() }
            StudioIcon(Icons.Outlined.Undo, tr("Undo mixing", "撤销混色"), enabled = canUndo) { view.undo() }
            StudioIcon(Icons.Outlined.Redo, tr("Redo mixing", "重做混色"), enabled = canRedo) { view.redo() }
            Box {
                StudioIcon(Icons.Outlined.FiberManualRecord, tr("Mixing brush size", "混色笔刷大小")) { sizeMenu = true }
                ColorPaletteDropdownMenu(sizeMenu, { sizeMenu = false }) {
                    listOf(4f to tr("Small", "小"), 12f to tr("Medium", "中"), 28f to tr("Large", "大")).forEach { (size, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { onRadius(size); sizeMenu = false })
                    }
                    Row(Modifier.width(240.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BrushParameterSlider(radius, onRadius, Modifier.weight(1f), valueRange = 2f..48f)
                        Text("${(radius * 2).toInt()} px", fontSize = 10.sp)
                    }
                }
            }
            listOf(Triple(MixingBoard.Tool.PAINT, Icons.Outlined.Brush, tr("Paint on mixing board", "混色画笔")),
                Triple(MixingBoard.Tool.BLEND, Icons.Outlined.BlurOn, tr("Blend on mixing board", "混合颜色")),
                Triple(MixingBoard.Tool.PICK, Icons.Outlined.Colorize, tr("Pick from mixing board", "吸取混色"))).forEach { (value, icon, label) ->
                StudioIcon(icon, label, selected = tool == value) { onTool(value) }
            }
            Box {
                StudioIcon(Icons.Outlined.MoreHoriz, tr("Mixing palette menu", "混色面板菜单")) { menu = true }
                ColorPaletteDropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Reset view", "复位视图")) }, onClick = { view.resetViewport(); menu = false })
                    DropdownMenuItem(text = { Text(tr("Color set bar", "常用色条")) }, trailingIcon = { if (showColors) Icon(Icons.Outlined.Check, null) }, onClick = {
                        showColors = !showColors; prefs.edit().putBoolean("mixShowColors", showColors).apply()
                    })
                    DropdownMenuItem(text = { Text(tr("Replace selected color", "替换所选颜色")) }, enabled = library.activeSet?.selectedSwatchId != null, onClick = {
                        val id = library.activeSet?.selectedSwatchId
                        if (id != null) runCatching { paletteStore.update { it.replaceSwatch(MixingColorBarStore.SET_ID, id, color) } }.onSuccess { library = it }.onFailure { storageError = true }
                        menu = false
                    })
                    DropdownMenuItem(text = { Text(tr("Edit selected color…", "编辑所选颜色…")) }, enabled = library.activeSet?.selectedSwatchId != null,
                        onClick = { editingSwatchId = library.activeSet?.selectedSwatchId; menu = false })
                    DropdownMenuItem(text = { Text(tr("Delete selected color", "删除所选颜色")) }, enabled = library.activeSet?.selectedSwatchId != null,
                        onClick = {
                            library.activeSet?.selectedSwatchId?.let { selectedId ->
                                runCatching { paletteStore.update { it.removeSwatch(MixingColorBarStore.SET_ID, selectedId) } }
                                    .onSuccess { library = it }.onFailure { storageError = true }
                            }
                            menu = false
                        })
                    DropdownMenuItem(text = { Text(tr("Reset color bar", "重置常用色条")) }, onClick = { menu = false; resetColors = true })
                    HorizontalDivider()
                    listOf(tr("Transparent", "透明"), tr("White", "白色"), tr("Black", "黑色"), tr("Gray", "灰色"), tr("Beige", "米色")).forEachIndexed { index, label ->
                        DropdownMenuItem(text = { Text(label) }, trailingIcon = { if (background == index) Icon(Icons.Outlined.Check, null) }, onClick = {
                            background = index; prefs.edit().putInt("mixBackground", index).apply(); menu = false
                        })
                    }
                }
            }
        }
        if (storageError) Text(tr("Mixing board could not be saved or restored", "混色画板保存或恢复失败"), color = Ink, fontSize = 11.sp)
    }
    if (resetColors) AlertDialog(onDismissRequest = { resetColors = false }, title = { Text(tr("Reset color bar", "重置常用色条")) },
        text = { Text(tr("Replace the mixing colors with the original colors?", "将混色区的常用色条恢复为初始颜色？")) },
        confirmButton = { TextButton(onClick = {
            runCatching { paletteStore.reset() }.onSuccess { library = it }.onFailure { storageError = true }
            resetColors = false
        }) { Text(tr("Reset", "重置")) } },
        dismissButton = { TextButton(onClick = { resetColors = false }) { Text(tr("Cancel", "取消")) } })
    editingSwatchId?.let { id -> library.activeSet?.swatches?.firstOrNull { it.id == id }?.let { swatch ->
        StandardColorPickerDialog(swatch.color, language, onConfirm = { chosen ->
            runCatching { paletteStore.update { it.replaceSwatch(MixingColorBarStore.SET_ID, id, chosen) } }
                .onSuccess { library = it }.onFailure { storageError = true }
            onPick(chosen)
            editingSwatchId = null
        }, onDismiss = { editingSwatchId = null })
    } }
}

@Composable
private fun StudioIcon(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(32.dp).semantics { this.selected = selected }
        .background(if (selected) SageContainer else Color.Transparent, RoundedCornerShape(2.dp))) {
        Icon(icon, label, Modifier.size(17.dp), tint = if (enabled) Ink else MutedInk.copy(alpha = .35f))
    }
}
