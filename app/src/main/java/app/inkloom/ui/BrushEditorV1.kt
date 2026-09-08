package app.inkloom.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.inkloom.brushpresetv5.*
import app.inkloom.document.LayerIds
import app.inkloom.engine.v5.surface.PixelTileSurface
import app.inkloom.settings.AppSettings
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/** The working copy is isolated until Done; every visible parameter reaches the real compiler. */
@Composable
fun BrushEditorV1Dialog(
    source: OwnedBrushPreset,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSaveAs: (BrushPresetV5) -> Unit,
    onSaveInPlace: (BrushPresetV5) -> Unit,
    onResetToFactory: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var session by remember(source.id) { mutableStateOf(EditorWorkingCopy.start(source)) }
    val language = settings.language
    fun tr(en: String, zh: String) = language.text(en, zh)
    var saveAsOpen by remember { mutableStateOf(false) }
    var saveAsName by remember(source.id) { mutableStateOf(session.current.identity.name + tr(" - Copy", " - 副本")) }
    var selectedGroup by remember(source.id) { mutableStateOf(BrushOptionGroup.SPACING) }
    var moreOpen by remember { mutableStateOf(false) }
    val panels = remember(session.current) { BrushOptionDescriptors.productionPanels(session.current) }
    val activePanel = panels.firstOrNull { it.first == selectedGroup } ?: panels.first()
    val orderedOptions = if (activePanel.first == BrushOptionGroup.PRESSURE) activePanel.second.sortedBy {
        when {
            it.id.contains("Size") -> 0
            it.id.contains("Opacity") -> 1
            else -> 2
        }
    } else activePanel.second
    val commit = {
        if (source.isFactory) saveAsOpen = true else onSaveInPlace(session.current)
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.fillMaxSize().widthIn(max = 1200.dp).heightIn(max = 860.dp),
                shape = RoundedCornerShape(16.dp), color = Color.White, contentColor = Color(0xFF202020)) {
                Column {
                    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Brush Studio", "笔刷工作室"), style = MaterialTheme.typography.titleLarge)
                            Text(session.current.identity.name, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        }
                        Box {
                            TextButton(onClick = { moreOpen = true }) { Text(tr("Actions", "操作")) }
                            DropdownMenu(moreOpen, { moreOpen = false }) {
                                DropdownMenuItem(text = { Text(tr("Save As", "另存为")) },
                                    onClick = { moreOpen = false; saveAsOpen = true })
                                if (!source.isFactory) {
                                    DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = {
                                        moreOpen = false
                                        onSaveAs(session.commitAsNew(session.current.identity.name + tr(" - Copy", " - 副本")).preset)
                                    })
                                    onResetToFactory?.let { reset ->
                                        DropdownMenuItem(text = { Text(tr("Reset", "重置")) }, onClick = { moreOpen = false; reset() })
                                    }
                                    DropdownMenuItem(text = { Text(tr("Delete", "删除"), color = MaterialTheme.colorScheme.error) },
                                        onClick = { moreOpen = false; onDelete() })
                                }
                            }
                        }
                        TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) }
                        Button(onClick = commit) { Text(tr("Done", "完成")) }
                    }
                    HorizontalDivider()
                    BoxWithConstraints(Modifier.weight(1f)) {
                        // Keep the three-column relationship on a tablet; stack the pad only on a phone.
                        val wide = maxWidth >= 720.dp
                        Column {
                            Row(Modifier.weight(1f)) {
                                LazyColumn(Modifier.width(if (wide) 174.dp else 128.dp).fillMaxHeight()
                                    .background(Color(0xFFF6F6F6)), contentPadding = PaddingValues(8.dp)) {
                                    item {
                                        Text(session.current.identity.family.localizedLabel(language),
                                            Modifier.padding(12.dp), color = Color.Gray, fontSize = 12.sp)
                                    }
                                    items(panels, key = { it.first.name }) { (group, _) ->
                                        Text(group.localizedLabel(language),
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                                                .background(if (group == activePanel.first) Color(0xFFE0EBE3) else Color.Transparent)
                                                .clickable { selectedGroup = group }
                                                .padding(horizontal = 12.dp, vertical = 16.dp),
                                            fontSize = 13.sp)
                                    }
                                }
                                VerticalDivider()
                                LazyColumn(Modifier.weight(if (wide) .42f else 1f).fillMaxHeight(),
                                    contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    item {
                                        Text(activePanel.first.localizedLabel(language), style = MaterialTheme.typography.titleMedium)
                                    }
                                    items(orderedOptions, key = { it.id }) { option ->
                                        BrushOptionRow(option, session.current, language) { session = session.withValue(it) }
                                    }
                                }
                                if (wide) {
                                    VerticalDivider()
                                    V5ScratchPad(session.current, settings, Modifier.weight(.58f).fillMaxHeight())
                                }
                            }
                            if (!wide) {
                                HorizontalDivider()
                                V5ScratchPad(session.current, settings, Modifier.fillMaxWidth().height(220.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (saveAsOpen) AlertDialog(
        onDismissRequest = { saveAsOpen = false },
        title = { Text(tr("Save brush as", "笔刷另存为")) },
        text = { OutlinedTextField(saveAsName, { saveAsName = it },
            label = { Text(tr("Brush name", "笔刷名称")) }, singleLine = true) },
        confirmButton = { TextButton(enabled = saveAsName.isNotBlank(), onClick = {
            val result = if (source.isFactory) session.commit(saveAsName.trim()) else session.commitAsNew(saveAsName.trim())
            onSaveAs(result.preset)
        }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { saveAsOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BrushOptionRow(
    option: BrushOptionDescriptor,
    value: BrushPresetV5,
    language: UiLanguage,
    onValue: (BrushPresetV5) -> Unit,
) {
    val current = option.get(value)
    val label = option.localizedLabel(language)
    if (option.id.endsWith("CurveShape")) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PressureCurveShape.entries.forEach { shape ->
                    TextButton(onClick = { if (shape != PressureCurveShape.CUSTOM) onValue(option.apply(value, shape.ordinal.toFloat())) },
                        contentPadding = PaddingValues(horizontal = 2.dp)) {
                        Text(shape.localizedLabel(language), fontSize = 11.sp)
                    }
                }
            }
            BrushPressureCurve(option.id, value, language, onValue)
        }
        return
    }
    if (option.id.endsWith("Enabled")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            Switch(current >= .5f, { onValue(option.apply(value, if (it) 1f else 0f)) },
                Modifier.semantics { contentDescription = label })
        }
        return
    }
    var numericOpen by remember { mutableStateOf(false) }
    var numeric by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = { numeric = "%.3f".format(java.util.Locale.ROOT, current).trimEnd('0').trimEnd('.'); numericOpen = true },
                contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("%.2f".format(java.util.Locale.ROOT, current), fontSize = 12.sp)
            }
        }
        Slider(current.coerceIn(option.valueRange.start, option.valueRange.endInclusive),
            { onValue(option.apply(value, it)) }, valueRange = option.valueRange,
            modifier = Modifier.semantics { contentDescription = label },
            thumb = { Box(Modifier.size(20.dp).shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape).border(1.dp, Color(0xFFD4D8D5), CircleShape)) },
            track = { state -> SliderDefaults.Track(state, Modifier.height(4.dp),
                colors = SliderDefaults.colors(activeTrackColor = Color(0xFF427658), inactiveTrackColor = Color(0xFFE1E5E2)),
                thumbTrackGapSize = 0.dp, trackInsideCornerSize = 2.dp) })
    }
    if (numericOpen) {
        val parsed = numeric.toFloatOrNull()?.takeIf { it.isFinite() && it in option.valueRange }
        val apply = { if (parsed != null) { onValue(option.apply(value, parsed)); numericOpen = false } }
        AlertDialog(onDismissRequest = { numericOpen = false }, title = { Text(label) },
            text = { OutlinedTextField(numeric, { numeric = it }, singleLine = true,
                isError = parsed == null,
                supportingText = { Text("${option.valueRange.start} – ${option.valueRange.endInclusive}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { apply() })) },
            confirmButton = { TextButton(enabled = parsed != null, onClick = apply) { Text(language.text("Apply", "应用")) } },
            dismissButton = { TextButton(onClick = { numericOpen = false }) { Text(language.text("Cancel", "取消")) } })
    }
}

@Composable
private fun V5ScratchPad(preset: BrushPresetV5, settings: AppSettings, modifier: Modifier) {
    var reset by remember { mutableIntStateOf(0) }
    Column(modifier.background(Color(0xFFF8F8F8)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(settings.language.text("Drawing pad", "试笔板"), Modifier.weight(1f), fontSize = 14.sp)
            TextButton(onClick = { reset++ }) { Text(settings.language.text("Clear", "清空")) }
        }
        key(reset) {
            AndroidView(factory = { ctx ->
                InkCanvasView(ctx).apply {
                    activeLayerId = LayerIds.INK
                    pageModelWidth = 900f
                    pageModelHeight = 1200f
                    setRasterLayerSurfaces(mapOf(LayerIds.INK to PixelTileSurface(900, 1200)))
                    layerOrder = listOf(LayerIds.INK)
                    pagePaperColor = AndroidColor.WHITE
                }
            }, update = { it.applyBrushPresetV5Configuration(preset, settings) },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White)
                    .semantics { contentDescription = settings.language.text("Brush test canvas", "笔刷测试画布") })
        }
    }
}
