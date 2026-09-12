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
    onResizeWidth: (Float) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE) }
    val mixingView = remember { ColorMixingView(context) }
    var mixingTool by remember { mutableStateOf(MixingBoard.Tool.entries.getOrElse(preferences.getInt("mixTool", 0)) { MixingBoard.Tool.PAINT }) }
    var mixingRadius by remember { mutableFloatStateOf(preferences.getFloat("mixRadius", 12f).coerceIn(2f, 48f)) }
    val contentHeight = (LocalConfiguration.current.screenHeightDp - 200).coerceIn(160, 420).dp
    val density = LocalDensity.current
    var visibleContentHeight by remember { mutableStateOf(contentHeight) }
    var draft by remember { mutableStateOf(ColorPickerDraft.fromColor(color)) }
    LaunchedEffect(color) { draft = draft.withColor(color) }
    fun pick(value: Int) { draft = draft.withColor(value); onColorCommit(value) }
    var tab by remember { mutableIntStateOf(preferences.getInt("studioTab", 0).coerceIn(0, 3)) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            listOf(tr("Wheel", "色环"), tr("Palettes", "色板"), tr("Mix", "混色"), tr("Nearby", "近似色")).forEachIndexed { index, label ->
                Box(Modifier.weight(1f).height(40.dp)
                    .background(if (tab == index) SageContainer else Color.Transparent)
                    .semantics { selected = tab == index }
                    .clickable { tab = index; preferences.edit().putInt("studioTab", index).apply() }, contentAlignment = Alignment.Center) {
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
                        0 -> CspColorPanel(color, onColorPreview, ::pick, onResizeWidth = onResizeWidth, showHeader = false, initialDraft = draft, onDraftChange = { draft = it })
                        1 -> ColorSetPanel(color, language, visibleContentHeight, ::pick)
                        2 -> MixingPalette(color, language, ::pick, mixingView, mixingTool, { mixingTool = it; preferences.edit().putInt("mixTool", it.ordinal).apply() }, mixingRadius, { mixingRadius = it; preferences.edit().putFloat("mixRadius", it).apply() })
                        3 -> NearbyColorPalette(draft, language, ::pick)
                    }
                }
            }
        }
    }
}

/** Fixed reference prevents selecting a tile from shifting every following tile under the pen. */
@Composable
private fun NearbyColorPalette(draft: ColorPickerDraft, language: UiLanguage, onPick: (Int) -> Unit) {
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
                        .clickable { onPick(swatch) })
                }
            }
        }
        RangeControl(tr("Saturation range", "饱和度范围"), saturationRange) { saturationRange = it }
        RangeControl(tr("Brightness range", "明度范围"), valueRange) { valueRange = it }
    }
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
    var swatchMenu by remember { mutableStateOf<String?>(null) }
    var resetColors by remember { mutableStateOf(false) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column {
        AndroidView(factory = { view },
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clipToBounds().border(.5.dp, OutlineSoft),
            update = {
                it.ink = color; it.tool = tool; it.brushRadius = radius; it.onPick = onPick
                it.backgroundTone = tones[background]
                it.contentDescription = tr("Color mixing board", "混色画板")
                it.onHistory = { undo, redo -> canUndo = undo; canRedo = redo }
                it.onStorageError = { storageError = true }
            })
        if (showColors) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            library.activeSet?.swatches.orEmpty().forEach { swatch ->
                val label = tr("Mixing color #%06X".format(swatch.color and 0xffffff), "混色色块 #%06X".format(swatch.color and 0xffffff))
                Box(Modifier.size(32.dp).padding(horizontal = 3.dp, vertical = 7.dp)
                    .background(Color(swatch.color), RoundedCornerShape(2.dp)).border(if (library.activeSet?.selectedSwatchId == swatch.id) 1.5.dp else .5.dp, if (library.activeSet?.selectedSwatchId == swatch.id) Ink else OutlineSoft)
                    .semantics { contentDescription = label }
                    .combinedClickable(onClick = {
                        runCatching { paletteStore.update { it.selectSwatch(MixingColorBarStore.SET_ID, swatch.id) } }.onSuccess { library = it }.onFailure { storageError = true }
                        onPick(swatch.color)
                    }, onLongClick = { swatchMenu = swatch.id })) {
                    ColorPaletteDropdownMenu(swatchMenu == swatch.id, { swatchMenu = null }) {
                        DropdownMenuItem(text = { Text(tr("Replace with current", "替换为当前颜色")) }, onClick = {
                            runCatching { paletteStore.update { it.replaceSwatch(MixingColorBarStore.SET_ID, swatch.id, color) } }.onSuccess { library = it }.onFailure { storageError = true }; swatchMenu = null
                        })
                        DropdownMenuItem(text = { Text(tr("Delete color", "删除颜色")) }, onClick = {
                            runCatching { paletteStore.update { it.removeSwatch(MixingColorBarStore.SET_ID, swatch.id) } }.onSuccess { library = it }.onFailure { storageError = true }; swatchMenu = null
                        })
                    }
                }
            }
            StudioIcon(Icons.Outlined.Add, tr("Add mixing color", "添加混色色块")) {
                runCatching { paletteStore.update { it.insertSwatch(MixingColorBarStore.SET_ID, color, beforeIndex = it.activeSet?.swatches?.size) } }.onSuccess { library = it }.onFailure { storageError = true }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).padding(7.dp).background(Color(color)).border(.5.dp, OutlineSoft))
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
}

@Composable
private fun StudioIcon(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(32.dp).semantics { this.selected = selected }
        .background(if (selected) SageContainer else Color.Transparent, RoundedCornerShape(2.dp))) {
        Icon(icon, label, Modifier.size(17.dp), tint = if (enabled) Ink else MutedInk.copy(alpha = .35f))
    }
}
