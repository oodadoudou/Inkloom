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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import app.inkloom.ui.theme.Paper
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.MutedInk
import app.inkloom.ui.theme.SageContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.inkloom.brushpresetv5.*
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
    val context=androidx.compose.ui.platform.LocalContext.current
    val sources=remember(source.id) { BrushSourceWorkspace(context) }
    DisposableEffect(sources) { onDispose { sources.close() } }
    var sourceRole by remember { mutableStateOf<BrushSourceRole?>(null) }
    fun saveInPlace(value:BrushPresetV5) { onSaveInPlace(value);sources.retain(value) }
    fun saveAs(value:BrushPresetV5) { onSaveAs(value);sources.retain(value) }
    var session by remember(source.id) { mutableStateOf(EditorWorkingCopy.start(source)) }
    val language = settings.language
    fun tr(en: String, zh: String) = language.text(en, zh)
    var saveAsOpen by remember { mutableStateOf(false) }
    var saveAsName by remember(source.id) { mutableStateOf(session.current.identity.name + tr(" - Copy", " - 副本")) }
    var selectedGroup by remember(source.id) { mutableStateOf(BrushOptionGroup.SPACING) }
    var moreOpen by remember { mutableStateOf(false) }
    var compactPadOpen by remember { mutableStateOf(false) }
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
        if (source.isFactory) saveAsOpen = true else saveInPlace(session.current)
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.fillMaxSize().widthIn(max = 1200.dp).heightIn(max = 860.dp),
                shape = RoundedCornerShape(16.dp), color = Paper, contentColor = Ink) {
                Column {
                    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 18.dp, vertical = 8.dp)) {
                        val title: @Composable () -> Unit = {
                            Column {
                                Text(tr("Brush Studio", "笔刷工作室"), style = MaterialTheme.typography.titleLarge)
                                Text(session.current.identity.name, color = MutedInk, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        val actions: @Composable RowScope.() -> Unit = {
                            Box {
                                TextButton(onClick = { moreOpen = true }) { Text(tr("Actions", "操作")) }
                                DropdownMenu(moreOpen, { moreOpen = false }) {
                                    DropdownMenuItem(text = { Text(tr("Save As", "另存为")) },
                                        onClick = { moreOpen = false; saveAsOpen = true })
                                    if (!source.isFactory) {
                                        DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = {
                                            moreOpen = false
                                            saveAs(session.commitAsNew(session.current.identity.name + tr(" - Copy", " - 副本")).preset)
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
                        if (maxWidth < 560.dp) {
                            Column {
                                title()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically, content = actions)
                            }
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { title() }
                            actions()
                        }
                    }
                    HorizontalDivider()
                    BoxWithConstraints(Modifier.weight(1f)) {
                        // Keep the three-column relationship on a tablet; stack the pad only on a phone.
                        val wide = maxWidth >= 720.dp
                        // In a short window the pad must not consume the parameter viewport.
                        val compactHeight = !wide && maxHeight < 360.dp
                        Column {
                            if (compactHeight) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { compactPadOpen = false }) { Text(tr("Parameters", "参数")) }
                                    TextButton(onClick = { compactPadOpen = true }) { Text(tr("Drawing pad", "试画板")) }
                                }
                            }
                            if (compactHeight && compactPadOpen) {
                                V5ScratchPad(session.current, settings, Modifier.fillMaxWidth().weight(1f))
                            } else Row(Modifier.weight(1f)) {
                                LazyColumn(Modifier.width(if (wide) 174.dp else 128.dp).fillMaxHeight()
                                    .semantics { contentDescription = tr("Brush categories", "笔刷类别") }
                                    .background(Paper), contentPadding = PaddingValues(8.dp)) {
                                    item {
                                        Text(session.current.identity.family.localizedLabel(language),
                                            Modifier.padding(12.dp), color = MutedInk, fontSize = 12.sp)
                                    }
                                    items(panels, key = { it.first.name }) { (group, _) ->
                                        Text(group.localizedLabel(language),
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp))
                                                .background(if (group == activePanel.first) Ink else Color.Transparent)
                                                .clickable { selectedGroup = group }
                                                .semantics { selected = group == activePanel.first }
                                                .padding(horizontal = 12.dp, vertical = 16.dp),
                                            fontSize = 13.sp, color = if (group == activePanel.first) Paper else Ink)
                                    }
                                }
                                VerticalDivider()
                                LazyColumn(Modifier.weight(if (wide) .42f else 1f).fillMaxHeight()
                                    .semantics { contentDescription = tr("Brush parameters", "笔刷参数") },
                                    contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    item {
                                        Text(activePanel.first.localizedLabel(language), style = MaterialTheme.typography.titleMedium)
                                        if (activePanel.first == BrushOptionGroup.STROKE) {
                                            Text(tr("Global stabilization: ${(settings.globalStrokeStabilization * 100).toInt()}%. The brush setting is applied after it.",
                                                "全局防抖：${(settings.globalStrokeStabilization * 100).toInt()}%。此笔刷设置在全局防抖之后生效。"),
                                                fontSize = 12.sp, color = MutedInk)
                                        }
                                    }
                                    if (activePanel.first in setOf(BrushOptionGroup.TIP, BrushOptionGroup.PAPER, BrushOptionGroup.DUAL_SHAPE, BrushOptionGroup.DUAL_GRAIN)) {
                                        item { BrushSourcePreviews(session.current, activePanel.first, language) }
                                    }
                                    if(activePanel.first == BrushOptionGroup.SOURCES) item {
                                        if(session.current.identity.family == BrushFamily.AIRBRUSH) Text(tr("Airbrush image sources are not supported.","喷枪暂不支持图片来源。")) else Column {
                                            TextButton(onClick={sourceRole=BrushSourceRole.SHAPE}) { Text(tr("Edit shape source","编辑笔尖来源")) }
                                            TextButton(onClick={sourceRole=BrushSourceRole.GRAIN}) { Text(tr("Edit grain source","编辑颗粒来源")) }
                                            TextButton(onClick={sourceRole=BrushSourceRole.SECONDARY_SHAPE}) { Text(tr("Edit secondary shape","编辑第二笔尖")) }
                                            if(session.current.dual!=null) TextButton(onClick={sourceRole=BrushSourceRole.SECONDARY_GRAIN}) { Text(tr("Edit secondary grain","编辑第二颗粒")) }
                                        }
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
                            if (!wide && !compactHeight) {
                                HorizontalDivider()
                                V5ScratchPad(session.current, settings, Modifier.fillMaxWidth().height(220.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    sourceRole?.let { role -> BrushSourceEditor(session.current,role,sources,language,
        onDismiss={sourceRole=null},onApply={session=session.withValue(it);sourceRole=null}) }
    if (saveAsOpen) AlertDialog(
        onDismissRequest = { saveAsOpen = false },
        title = { Text(tr("Save brush as", "笔刷另存为")) },
        text = { OutlinedTextField(saveAsName, { saveAsName = it },
            label = { Text(tr("Brush name", "笔刷名称")) }, singleLine = true) },
        confirmButton = { TextButton(enabled = saveAsName.isNotBlank(), onClick = {
            val result = if (source.isFactory) session.commit(saveAsName.trim()) else session.commitAsNew(saveAsName.trim())
            saveAs(result.preset)
        }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = { saveAsOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun BrushOptionRow(
    option: BrushOptionDescriptor,
    value: BrushPresetV5,
    language: UiLanguage,
    onValue: (BrushPresetV5) -> Unit,
) {
    val current = option.get(value)
    val label = option.localizedLabel(language)
    if (option.id in setOf("dualComposite", "stampTransfer")) {
        var expanded by remember { mutableStateOf(false) }
        val names=if(option.id=="stampTransfer") listOf("轻薄罩染","均匀罩染","强烈罩染","厚重罩染","均匀混合","强烈混合") else listOf("正常","正片叠底","添加发光","减去","变暗","变亮","滤色","叠加","颜色减淡","颜色加深","线性加深","实色混合","线性高度")
        val english=if(option.id=="stampTransfer") DabTransferMode.entries.map { it.name } else DualCompositeMode.entries.map { it.name }
        Text(label, fontSize=13.sp)
        Box {
            TextButton(onClick={expanded=true}) { Text(language.text(english[current.toInt()],names[current.toInt()])) }
            DropdownMenu(expanded,{expanded=false}) {
                english.forEachIndexed { ordinal, name ->
                    DropdownMenuItem(text={Text(language.text(name,names[ordinal]))},onClick={onValue(option.apply(value,ordinal.toFloat()));expanded=false})
                }
            }
        }
        return
    }
    if (option.id.endsWith("CurveShape")) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
    val displayScale = if (option.id in setOf("smoothing", "motionFiltering", "motionExpression")) 100f else 1f
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontSize = 13.sp)
            TextButton(onClick = { numeric = "%.3f".format(java.util.Locale.ROOT, current * displayScale).trimEnd('0').trimEnd('.'); numericOpen = true },
                contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(if (displayScale == 100f) "${(current * 100).toInt()}%" else "%.2f".format(java.util.Locale.ROOT, current), fontSize = 12.sp)
            }
        }
        BrushParameterSlider(current.coerceIn(option.valueRange.start, option.valueRange.endInclusive),
            { onValue(option.apply(value, it)) }, valueRange = option.valueRange,
            modifier = Modifier.semantics { contentDescription = language.text("$label slider", "$label 滑块") })
    }
    if (numericOpen) {
        val parsed = numeric.toFloatOrNull()?.div(displayScale)?.takeIf { it.isFinite() && it in option.valueRange }
        val apply = { if (parsed != null) { onValue(option.apply(value, parsed)); numericOpen = false } }
        AlertDialog(onDismissRequest = { numericOpen = false }, title = { Text(label) },
            text = { OutlinedTextField(numeric, { numeric = it }, singleLine = true,
                isError = parsed == null,
                supportingText = { Text("${option.valueRange.start * displayScale} – ${option.valueRange.endInclusive * displayScale}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { apply() })) },
            confirmButton = { TextButton(enabled = parsed != null, onClick = apply) { Text(language.text("Apply", "应用")) } },
            dismissButton = { TextButton(onClick = { numericOpen = false }) { Text(language.text("Cancel", "取消")) } })
    }
}

@Composable
private fun V5ScratchPad(preset: BrushPresetV5, settings: AppSettings, modifier: Modifier) {
    var reset by remember { mutableIntStateOf(0) }
    var optionsOpen by remember { mutableStateOf(false) }
    var previewScale by remember { mutableFloatStateOf(1f) }
    var previewColor by remember { mutableIntStateOf(AndroidColor.rgb(17, 17, 17)) }
    fun tr(en: String, zh: String) = settings.language.text(en, zh)
    Column(modifier.background(Paper).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { optionsOpen = true }) {
                    Icon(Icons.Outlined.Tune, tr("Drawing pad settings", "试笔板设置"))
                }
                DropdownMenu(optionsOpen, { optionsOpen = false }) {
                    Column(Modifier.width(260.dp).padding(12.dp)) {
                        Text(tr("Preview size", "预览大小") + " · ${(previewScale * 100).toInt()}%")
                        Slider(previewScale, { previewScale = it }, valueRange = .25f..4f,
                            modifier = Modifier.semantics { contentDescription = tr("Preview size slider", "预览大小滑块") })
                        Text(tr("Preview color", "预览颜色"), Modifier.padding(vertical = 8.dp))
                        val colors = listOf(0xFF111111.toInt(), 0xFFFFFFFF.toInt(), 0xFFD94141.toInt(), 0xFFE58A38.toInt(),
                            0xFFE4C442.toInt(), 0xFF41895B.toInt(), 0xFF377DCD.toInt(), 0xFF9764BC.toInt())
                        val names = listOf(tr("Black", "黑色"), tr("White", "白色"), tr("Red", "红色"), tr("Orange", "橙色"),
                            tr("Yellow", "黄色"), tr("Green", "绿色"), tr("Blue", "蓝色"), tr("Purple", "紫色"))
                        colors.chunked(4).forEachIndexed { row, choices ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                choices.forEachIndexed { column, color ->
                                    Box(Modifier.size(48.dp).clickable { previewColor = color }
                                        .semantics { contentDescription = tr("Preview color ", "预览颜色 ") + names[row * 4 + column] }
                                        .padding(8.dp).clip(CircleShape)
                                        .background(Color(color)).border(if (color == previewColor) 3.dp else 1.dp,
                                            if (color == previewColor) Ink else Color.LightGray, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            Text(settings.language.text("Drawing pad", "试笔板"), Modifier.weight(1f), fontSize = 14.sp)
            TextButton(onClick = { reset++ }) { Text(settings.language.text("Clear", "清空")) }
        }
        key(reset) {
            AndroidView(factory = { ctx ->
                BrushDrawingPadView(ctx)
            }, update = { it.configure(preset.copy(tip = preset.tip.copy(size = preset.tip.size * previewScale)), settings, previewColor) },
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color.White)
                    .semantics { contentDescription = settings.language.text("Brush test canvas", "笔刷测试画布") })
        }
    }
}
