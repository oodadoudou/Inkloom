package app.inkloom.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.inkloom.color.MixingBoard
import app.inkloom.settings.ColorPaletteStore
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
    var tab by remember { mutableIntStateOf(preferences.getInt("studioTab", 0).coerceIn(0, 3)) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            listOf(tr("Wheel", "色环"), tr("Palettes", "色板"), tr("Mix", "混色"), tr("Nearby", "近似色")).forEachIndexed { index, label ->
                Box(Modifier.weight(1f).height(40.dp)
                    .background(if (tab == index) SageContainer else Color.Transparent, RoundedCornerShape(5.dp))
                    .semantics { selected = tab == index }
                    .clickable { tab = index; preferences.edit().putInt("studioTab", index).apply() }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (tab == index) Ink else MutedInk, fontSize = 12.sp)
                }
            }
        }
        when (tab) {
            0 -> CspColorPanel(color, onColorPreview, onColorCommit, onResizeWidth = onResizeWidth, showHeader = false)
            1 -> SavedColorSwatches(color, language, onColorCommit)
            2 -> MixingPalette(color, language, onColorCommit)
            3 -> NearbyColorPalette(color, language, onColorCommit)
        }
    }
}

@Composable
private fun SavedColorSwatches(color: Int, language: UiLanguage, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val store = remember { ColorPaletteStore(context) }
    var colors by remember { mutableStateOf(store.load()) }
    var menuColor by remember { mutableStateOf<Int?>(null) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 360.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Saved colors", "已存颜色"), Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
            Text("${colors.size}/32", color = MutedInk, fontSize = 10.sp)
            StudioIcon(Icons.Outlined.Add, tr("Save current color", "保存当前颜色"), enabled = colors.size < 32 && color !in colors) {
                colors = store.add(color)
            }
        }
        if (colors.isEmpty()) Text(tr("Tap + to save the current color", "点击 + 保存当前颜色"), color = MutedInk, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
        colors.chunked(8).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { value ->
                    val hex = "#%06X".format(value and 0xffffff)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(vertical = 2.dp)
                        .background(Color(value), RoundedCornerShape(3.dp))
                        .border(if (value == color) 2.dp else .5.dp, if (value == color) Ink else OutlineSoft, RoundedCornerShape(3.dp))
                        .semantics { contentDescription = tr("Saved color $hex", "已存颜色 $hex") }
                        .combinedClickable(onClick = { onPick(value) }, onLongClick = { menuColor = value })) {
                        DropdownMenu(expanded = menuColor == value, onDismissRequest = { menuColor = null }) {
                            DropdownMenuItem(text = { Text(tr("Replace with current", "替换为当前颜色")) }, onClick = {
                                colors = store.replace(colors.map { if (it == value) color else it }.distinct()); menuColor = null
                            })
                            DropdownMenuItem(text = { Text(tr("Delete", "删除")) }, onClick = { colors = store.remove(value); menuColor = null })
                        }
                    }
                }
                repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Fixed reference prevents selecting a tile from shifting every following tile under the pen. */
@Composable
private fun NearbyColorPalette(color: Int, language: UiLanguage, onPick: (Int) -> Unit) {
    var reference by remember { mutableIntStateOf(color) }
    var saturationRange by remember { mutableFloatStateOf(.4f) }
    var valueRange by remember { mutableFloatStateOf(.4f) }
    val hsv = remember(reference) { FloatArray(3).also { AndroidColor.colorToHSV(reference, it) } }
    fun tr(en: String, zh: String) = language.text(en, zh)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Saturation × brightness (HSV)", "饱和度 × 明度（HSV）"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
            StudioIcon(Icons.Outlined.MyLocation, tr("Use current color as reference", "以当前颜色为基准")) { reference = color }
        }
        repeat(7) { row ->
            Row(Modifier.fillMaxWidth().height(32.dp)) {
                repeat(7) { column ->
                    val swatch = nearbyColor(hsv, column, row, saturationRange, valueRange)
                    val label = tr("Nearby color ${row + 1}, ${column + 1}", "近似色 ${row + 1}，${column + 1}")
                    Box(Modifier.weight(1f).fillMaxHeight().padding(.5.dp).background(Color(swatch))
                        .border(if (swatch == color) 2.dp else 0.dp, if (swatch == color) Ink else Color.Transparent)
                        .semantics { contentDescription = label }
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
private fun MixingPalette(color: Int, language: UiLanguage, onPick: (Int) -> Unit) {
    var tool by remember { mutableStateOf(MixingBoard.Tool.PAINT) }
    var radius by remember { mutableFloatStateOf(12f) }
    var view by remember { mutableStateOf<ColorMixingView?>(null) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf(false) }
    fun tr(en: String, zh: String) = language.text(en, zh)
    val boardLabel = tr("Color mixing board", "混色画板")
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf(Triple(MixingBoard.Tool.PAINT, Icons.Outlined.Brush, tr("Paint on mixing board", "混色画笔")),
                Triple(MixingBoard.Tool.BLEND, Icons.Outlined.BlurOn, tr("Blend on mixing board", "混合颜色")),
                Triple(MixingBoard.Tool.PICK, Icons.Outlined.Colorize, tr("Pick from mixing board", "吸取混色"))).forEach { (value, icon, label) ->
                StudioIcon(icon, label, selected = tool == value) { tool = value }
            }
            Spacer(Modifier.weight(1f))
            StudioIcon(Icons.Outlined.Undo, tr("Undo mixing", "撤销混色"), enabled = canUndo) { view?.undo() }
            StudioIcon(Icons.Outlined.Redo, tr("Redo mixing", "重做混色"), enabled = canRedo) { view?.redo() }
            StudioIcon(Icons.Outlined.DeleteOutline, tr("Clear mixing board", "清空混色画板")) { view?.clear() }
        }
        AndroidView(factory = { context -> ColorMixingView(context).also { view = it } },
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).border(1.dp, OutlineSoft),
            update = {
                it.ink = color; it.tool = tool; it.brushRadius = radius; it.onPick = onPick
                it.contentDescription = boardLabel
                it.onHistory = { undo, redo -> canUndo = undo; canRedo = redo }
                it.onStorageError = { storageError = true }
            })
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Size", "大小"), Modifier.width(48.dp), color = MutedInk, fontSize = 11.sp)
            BrushParameterSlider(radius, { radius = it }, Modifier.weight(1f), valueRange = 2f..48f)
            Text("${(radius * 2).toInt()} px", Modifier.width(42.dp), color = MutedInk, fontSize = 10.sp)
        }
        if (storageError) Text(tr("Mixing board could not be saved or restored", "混色画板保存或恢复失败"), color = Ink, fontSize = 11.sp)
    }
}

@Composable
private fun StudioIcon(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled, modifier = Modifier.size(40.dp)
        .background(if (selected) SageContainer else Color.Transparent, RoundedCornerShape(5.dp))) {
        Icon(icon, label, Modifier.size(19.dp), tint = if (enabled) Ink else MutedInk.copy(alpha = .35f))
    }
}
