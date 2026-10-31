package app.inkloom.ui

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.inkloom.document.*
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutCategory
import app.inkloom.settings.CspShortcutResolver
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutMatchMode
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.ShortcutBrushGroup
import app.inkloom.settings.category
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ToolbarDock
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.ToolboxMotionResolver
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.ToolboxQuickSlots
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

@Composable
internal fun SettingsDialog(
    settings: AppSettings,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    onChange: (AppSettings) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onExportShortcuts: () -> Unit,
    onImportShortcuts: () -> Unit,
    onDismiss: () -> Unit,
) {
    var settingsTab by rememberSaveable { mutableIntStateOf(0) }
    var shortcutEditorOpen by remember { mutableStateOf(false) }
    StudioDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
        title = { Text(tr("Settings", "设置")) },
        text = {
            Column(Modifier.dialogContentWidth(300.dp, 520.dp)) {
                val tabs = listOf(tr("General", "通用"), tr("Gestures", "手势"), tr("Pressure", "压感"), tr("Shortcuts", "快捷键"), tr("Backup", "备份"), tr("About", "关于"))
                LazyRow(Modifier.semantics { contentDescription = "Settings sections" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(tabs.size) { index ->
                        TextButton(onClick = { settingsTab = index }, modifier = Modifier.height(44.dp).background(
                            if (settingsTab == index) SageContainer else Color.Transparent, RoundedCornerShape(8.dp))) {
                            Text(tabs[index], fontSize = 12.sp, color = Ink)
                        }
                    }
                }
                HorizontalDivider(color = OutlineSoft)
                key(settingsTab) {
                LazyColumn(Modifier.heightIn(max = 560.dp).semantics { contentDescription = "Settings content" }, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 12.dp)) {
                if (settingsTab == 0) {
                item { Text(tr("Interface gray", "界面灰度"), color = MutedInk, fontSize = 12.sp) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    app.inkloom.settings.UiGrayTheme.entries.forEach { gray ->
                        val label = when (gray) {
                            app.inkloom.settings.UiGrayTheme.DARK -> tr("Dark", "深灰")
                            app.inkloom.settings.UiGrayTheme.MEDIUM -> tr("Medium", "中灰")
                            app.inkloom.settings.UiGrayTheme.SOFT -> tr("Soft", "柔灰")
                            app.inkloom.settings.UiGrayTheme.LIGHT -> tr("Light", "浅灰")
                        }
                        StudioFilterChip(selected = settings.uiGrayTheme == gray,
                            onClick = { onChange(settings.copy(uiGrayTheme = gray)) },
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Interface gray ${gray.name}" },
                            label = { Text(label, fontSize = 12.sp) })
                    }
                } }
                item { InterfaceSizeSetting(settings.interfaceScale, language = settings.language) { onChange(settings.copy(interfaceScale = it)) } }
                item { Text(tr("LANGUAGE", "语言"), color = MutedInk, fontSize = 12.sp) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFilterChip(selected = settings.language == UiLanguage.ENGLISH, onClick = { onChange(settings.copy(language = UiLanguage.ENGLISH)) }, label = { Text("English") })
                    StudioFilterChip(selected = settings.language == UiLanguage.SIMPLIFIED_CHINESE, onClick = { onChange(settings.copy(language = UiLanguage.SIMPLIFIED_CHINESE)) }, label = { Text("简体中文") })
                } }
                item { Text(tr("INTERFACE", "界面"), color = MutedInk, fontSize = 12.sp) }
                item { SettingSwitch(tr("Show floating toolbar", "显示浮动工具栏"), settings.showToolbar) { onChange(settings.copy(showToolbar = it)) } }
                if (settings.toolbarDock == ToolbarDock.FREE) item { SettingSwitch(tr("Vertical toolbar", "垂直工具栏"), settings.verticalToolbar) { onChange(settings.copy(verticalToolbar = it)) } }
                item { OutlinedButton(onClick = { onChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = 16f, toolbarOffsetYDp = 16f)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset toolbar position", "重置工具栏位置")) } }

                }
                if (settingsTab == 1) {
                item { Text(tr("INPUT", "输入"), color = MutedInk, fontSize = 12.sp) }
                item { SettingSwitch(tr("Draw with finger", "手指书写"), settings.fingerDrawing) { onChange(settings.copy(fingerDrawing = it)) } }
                item { SettingSwitch(tr("Draw and hold", "停笔规整"), settings.drawAndHoldEnabled) { onChange(settings.copy(drawAndHoldEnabled = it)) } }
                if (settings.drawAndHoldEnabled) item {
                    Text(tr("Hold delay · ${settings.drawAndHoldDelayMillis} ms", "停笔时间 · ${settings.drawAndHoldDelayMillis} 毫秒"), fontSize = 12.sp)
                    BrushParameterSlider(settings.drawAndHoldDelayMillis.toFloat(), { onChange(settings.copy(drawAndHoldDelayMillis = it.toLong())) },
                        valueRange = 300f..1500f, steps = 23, modifier = Modifier.semantics { contentDescription = "Draw and hold delay" })
                }
                item { SettingSwitch(tr("Single-finger double tap to undo", "单指双击撤销"), settings.singleFingerDoubleTapUndo) { onChange(settings.copy(singleFingerDoubleTapUndo = it)) } }
                item { SettingSwitch(tr("Swipe to turn pages", "滑动翻页"), settings.swipeToTurnPages) { onChange(settings.copy(swipeToTurnPages = it)) } }
                item { SettingSwitch(tr("Two-finger tap to undo", "双指轻点撤销"), settings.twoFingerDoubleTapUndo) { onChange(settings.copy(twoFingerDoubleTapUndo = it)) } }
                item { SettingSwitch(tr("Three-finger tap to redo", "三指轻点重做"), settings.threeFingerTapRedo) { onChange(settings.copy(threeFingerTapRedo = it)) } }
                item { SettingSwitch(tr("Two-finger canvas rotation", "双指旋转画布"), settings.twoFingerRotation) { onChange(settings.copy(twoFingerRotation = it)) } }
                item { Text(tr("GESTURES FROM PROCREATE", "Procreate 手势"), color = MutedInk, fontSize = 12.sp) }
                item { SettingSwitch(tr("Touch and hold for eyedropper", "按住取色"), settings.touchHoldEyedropper) { onChange(settings.copy(touchHoldEyedropper = it)) } }
                if (settings.touchHoldEyedropper) item {
                    Text(tr("Hold delay · ${settings.touchHoldEyedropperDelayMillis} ms", "按住时间 · ${settings.touchHoldEyedropperDelayMillis} 毫秒"), fontSize = 12.sp)
                    BrushParameterSlider(settings.touchHoldEyedropperDelayMillis.toFloat(), { onChange(settings.copy(touchHoldEyedropperDelayMillis = it.toLong())) },
                        valueRange = 100f..1500f, steps = 27, modifier = Modifier.semantics { contentDescription = "Eyedropper hold delay" })
                }
                item { SettingSwitch(tr("Hold two or three fingers to keep undoing or redoing", "双指／三指按住连续撤销／重做"), settings.holdToRepeatUndo) { onChange(settings.copy(holdToRepeatUndo = it)) } }
                item { SettingSwitch(tr("Quick pinch to fit", "快速捏合适合屏幕"), settings.quickPinchToFit) { onChange(settings.copy(quickPinchToFit = it)) } }
                item { SettingSwitch(tr("Four-finger tap for full screen", "四指轻点全屏"), settings.fourFingerTapFullScreen) { onChange(settings.copy(fourFingerTapFullScreen = it)) } }
                item { SettingSwitch(tr("Three-finger scrub to clear layer", "三指来回滑动清除图层"), settings.threeFingerScrubClear) { onChange(settings.copy(threeFingerScrubClear = it)) } }
                item { SettingSwitch(tr("Show input diagnostics", "显示输入诊断"), settings.showInputDiagnostics) { onChange(settings.copy(showInputDiagnostics = it)) } }
                if (app.inkloom.BuildConfig.PAINTING_DIAGNOSTICS) {
                    item { SettingSwitch(tr("Show V5 stroke diagnostics", "显示 V5 落笔诊断"), settings.showStrokeTraceOverlay) { onChange(settings.copy(showStrokeTraceOverlay = it)) } }
                }

                }
                if (settingsTab == 2) {
                item {
                    BrushSettingSlider(tr("Motion filtering (%)", "运动过滤（%）"), settings.globalMotionFiltering * 100f, 0f..100f) {
                        onChange(settings.copy(globalMotionFiltering = it / 100f))
                    }
                    BrushSettingSlider(tr("Expression (%)", "转折保留（%）"), settings.globalMotionExpression * 100f, 0f..100f) {
                        onChange(settings.copy(globalMotionExpression = it / 100f))
                    }
                    BrushSettingSlider(tr("Global stabilization (%)", "全局防抖强度（%）"), settings.globalStrokeStabilization * 100f, 0f..100f) {
                        onChange(settings.copy(globalStrokeStabilization = it / 100f))
                    }
                }
                item { Text("${tr("Pressure sensitivity", "压感灵敏度")}  ${"%.2f".format(settings.pressureSensitivity)}×") }
                item { BrushParameterSlider(settings.pressureSensitivity, { onChange(settings.copy(pressureSensitivity = it)) }, valueRange = .25f..2.5f) }
                item { Text("${tr("Pressure onset", "起始压力")}  ${(settings.pressureMinimumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { BrushParameterSlider(settings.pressureMinimumInput, { value -> onChange(settings.copy(pressureMinimumInput = value.coerceAtMost(settings.pressureMaximumInput - .05f))) }, valueRange = 0f..0.95f) }
                item { Text("${tr("Full pressure point", "满压位置")}  ${(settings.pressureMaximumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { BrushParameterSlider(settings.pressureMaximumInput, { value -> onChange(settings.copy(pressureMaximumInput = value.coerceAtLeast(settings.pressureMinimumInput + .05f))) }, valueRange = 0.05f..1f) }
                item { PressureCurveEditor(settings.pressureResponseCurve) { onChange(settings.copy(pressureResponseCurve = it)) } }
                item {
                    PressureScratchpad(settings)
                    TextButton(onClick = { onChange(settings.copy(pressureSensitivity = 1f, pressureMinimumInput = 0f, pressureMaximumInput = 1f, pressureResponseCurve = PressureResponseCurve.LINEAR)) }) {
                        Text(tr("Reset pressure settings", "重置压感设置"))
                    }
                }

                }
                if (settingsTab == 3) {
                item { Text(tr("PEN BUTTONS", "笔按键"), color = MutedInk, fontSize = 12.sp) }
                item { StylusActionRow(tr("Primary button", "主按键"), settings.stylusPrimaryButton, settings.stylusPrimaryTrigger, { onChange(settings.copy(stylusPrimaryButton = it)) }, { onChange(settings.copy(stylusPrimaryTrigger = it)) }) }
                item { StylusActionRow(tr("Secondary button", "副按键"), settings.stylusSecondaryButton, settings.stylusSecondaryTrigger, { onChange(settings.copy(stylusSecondaryButton = it)) }, { onChange(settings.copy(stylusSecondaryTrigger = it)) }) }
                item { Text(tr("TOOLBOX & KEYBOARD", "TOOLBOX 与键盘"), color = MutedInk, fontSize = 12.sp) }
                item { OutlinedButton(onClick = { shortcutEditorOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Keyboard, null); Spacer(Modifier.width(8.dp)); Text(tr("Customize shortcuts", "自定义快捷键")) } }

                }
                if (settingsTab == 5) aboutItems(settings.language)
                if (settingsTab == 4) {
                item { Text(tr("DATA & BACKUP", "数据与备份"), color = MutedInk, fontSize = 12.sp) }
                item { Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Backup, null); Spacer(Modifier.width(8.dp)); Text(tr("Export full library backup", "导出完整资料库备份")) } }
                item { OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text(tr("Restore library backup", "恢复资料库备份")) } }
                }
                }
                }
            }
        },
    )
    if (shortcutEditorOpen) ShortcutEditorDialog(settings, brushes, brushGroups, onChange, onExportShortcuts, onImportShortcuts, onDismiss = { shortcutEditorOpen = false })
}

@Composable
private fun ShortcutEditorDialog(settings: AppSettings, brushes: List<ImportedBrush>, brushGroups: List<String>, onChange: (AppSettings) -> Unit, onExportShortcuts: () -> Unit, onImportShortcuts: () -> Unit, onDismiss: () -> Unit) {
    var listeningFor by remember { mutableStateOf<ShortcutAction?>(null) }
    var captureMode by remember { mutableStateOf(ShortcutMatchMode.KEY_CODE) }
    var captureTrigger by remember { mutableStateOf(ShortcutTrigger.PRESS) }
    var deviceSpecific by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ShortcutCategory?>(null) }
    var quickSlotsOpen by remember { mutableStateOf(false) }
    val language = LocalUiLanguage.current
    val groupedActions = ShortcutCategory.entries.mapNotNull { category ->
        if (selectedCategory != null && selectedCategory != category) return@mapNotNull null
        val matches = ShortcutAction.entries.filter { action ->
            action.category() == category && (search.isBlank() || listOf(
                shortcutActionDisplayLabel(action, settings, language),
                shortcutCategoryLabel(category, language),
                action.name,
                settings.shortcutBindings.filter { it.action == action }.joinToString(" ") { CspShortcutResolver.display(it.chord) },
            ).any { it.contains(search.trim(), ignoreCase = true) })
        }
        matches.takeIf(List<ShortcutAction>::isNotEmpty)?.let { category to it }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(listeningFor) { if (listeningFor != null) focusRequester.requestFocus() }
    StudioDialog(
        properties = DialogProperties(decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text(tr("Toolbox shortcuts", "Toolbox 快捷键")) },
        text = {
            Column(Modifier.widthIn(max = 580.dp).heightIn(max = 540.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportShortcuts, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileOpen, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Import profile", "导入配置"), fontSize = 12.sp) }
                    OutlinedButton(onClick = onExportShortcuts, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.SaveAlt, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Export profile", "导出配置"), fontSize = 12.sp) }
                }
                HorizontalDivider(color = OutlineSoft)
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f)
                        .focusRequester(focusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val action = listeningFor ?: return@onPreviewKeyEvent false
                            val native = event.nativeKeyEvent
                            if (native.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                            if (native.keyCode in setOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_CTRL_RIGHT, android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_ALT_RIGHT, android.view.KeyEvent.KEYCODE_SHIFT_LEFT, android.view.KeyEvent.KEYCODE_SHIFT_RIGHT)) return@onPreviewKeyEvent true
                            if (native.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE || native.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                listeningFor = null
                                return@onPreviewKeyEvent true
                            }
                            val effectiveMode = if (captureMode == ShortcutMatchMode.SCAN_CODE && native.scanCode > 0) ShortcutMatchMode.SCAN_CODE else ShortcutMatchMode.KEY_CODE
                            val chord = ShortcutChord(native.keyCode, native.isCtrlPressed, native.isAltPressed, native.isShiftPressed, native.scanCode, effectiveMode)
                            val device = native.device.takeIf { deviceSpecific }
                            onChange(settings.copy(shortcutBindings = CspShortcutResolver.replace(settings.shortcutBindings, action, chord, captureTrigger, device?.descriptor, device?.name)))
                            listeningFor = null
                            true
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        StudioOutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            placeholder = { Text(tr("Search actions or keys", "搜索操作或按键")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { StudioFilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text(tr("All", "全部")) }) }
                            items(ShortcutCategory.entries, key = { it.name }) { category ->
                                StudioFilterChip(selected = selectedCategory == category, onClick = { selectedCategory = if (selectedCategory == category) null else category }, label = { Text(shortcutCategoryLabel(category, language)) })
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("Capture mode", "捕获方式"), color = MutedInk, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StudioFilterChip(selected = captureMode == ShortcutMatchMode.KEY_CODE, onClick = { captureMode = ShortcutMatchMode.KEY_CODE }, label = { Text(tr("Logical key", "逻辑键")) })
                                StudioFilterChip(selected = captureMode == ShortcutMatchMode.SCAN_CODE, onClick = { captureMode = ShortcutMatchMode.SCAN_CODE }, label = { Text(tr("Physical scan code", "物理扫描码")) })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StudioFilterChip(selected = captureTrigger == ShortcutTrigger.PRESS, onClick = { captureTrigger = ShortcutTrigger.PRESS }, label = { Text(tr("Tap to switch", "单击切换")) })
                                StudioFilterChip(selected = captureTrigger == ShortcutTrigger.HOLD, onClick = { captureTrigger = ShortcutTrigger.HOLD }, label = { Text(tr("Hold, release to return", "按住，松开返回")) })
                            }
                            SettingSwitch(tr("Only this input device", "仅此输入设备"), deviceSpecific) { deviceSpecific = it }
                        }
                    }
                    item { Text(tr("ROTARY & SCROLL", "旋钮与滚轮"), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                    ToolboxAxis.entries.forEach { axis ->
                        item("axis-${axis.name}") { ToolboxAxisBindingRow(axis, settings, onChange) }
                    }
                    item { Text(tr("POINTER / TOOLBOX BUTTONS", "鼠标／TOOLBOX 按钮"), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
                    listOf(android.view.MotionEvent.BUTTON_PRIMARY, android.view.MotionEvent.BUTTON_SECONDARY, android.view.MotionEvent.BUTTON_TERTIARY, android.view.MotionEvent.BUTTON_BACK, android.view.MotionEvent.BUTTON_FORWARD).forEachIndexed { index, button ->
                        item("motion-button-$button") { ToolboxButtonBindingRow(index + 1, button, settings, onChange) }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("BRUSH GROUP TARGETS", "笔刷组目标"), color = MutedInk, fontSize = 11.sp)
                            ShortcutBrushGroup.entries.forEach { group ->
                                ShortcutBrushGroupTargetRow(
                                    group = group,
                                    selectedPath = settings.shortcutBrushGroupTargets.forGroup(group),
                                    groupPaths = brushGroups,
                                    onSelect = { path ->
                                        onChange(settings.copy(shortcutBrushGroupTargets = settings.shortcutBrushGroupTargets.withGroup(group, path)))
                                    },
                                )
                            }
                            OutlinedButton(onClick = { quickSlotsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.DashboardCustomize, null)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Configure 8 quick slots", "配置 8 个快速槽"))
                            }
                        }
                    }
                    groupedActions.forEach { (category, actions) ->
                        item("category-${category.name}") { Text(shortcutCategoryLabel(category, language).uppercase(), color = MutedInk, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                        items(actions, key = { it.name }) { action ->
                            val actionBindings = settings.shortcutBindings.filter { it.action == action }
                            val defaultBindings = CspShortcutResolver.defaultBindings.filter { it.action == action }
                            val chords = actionBindings.map { binding ->
                                buildString {
                                    append(CspShortcutResolver.display(binding.chord))
                                    if (binding.trigger == ShortcutTrigger.HOLD) append(tr(" · hold", " · 按住"))
                                    binding.deviceName?.let { append(" · "); append(it) }
                                }
                            }
                            ListItem(
                                headlineContent = { Text(shortcutActionDisplayLabel(action, settings, language), fontSize = 13.sp) },
                                supportingContent = { Text(if (listeningFor == action) tr("Press a key combination…", "请按组合键…") else chords.joinToString(" / ").ifBlank { tr("Not assigned", "未分配") }, color = if (listeningFor == action) Ink else MutedInk) },
                                trailingContent = {
                                    Row {
                                        if (chords.isNotEmpty()) IconButton(onClick = { onChange(settings.copy(shortcutBindings = settings.shortcutBindings.filterNot { it.action == action })) }) { Icon(Icons.Outlined.LinkOff, tr("Remove shortcut", "移除快捷键")) }
                                        if (actionBindings != defaultBindings) IconButton(onClick = { onChange(settings.copy(shortcutBindings = CspShortcutResolver.restoreActionDefaults(settings.shortcutBindings, action))) }) { Icon(Icons.Outlined.RestartAlt, tr("Reset this action", "重置此操作")) }
                                        TextButton(onClick = {
                                            captureMode = ShortcutMatchMode.SCAN_CODE
                                            captureTrigger = ShortcutTrigger.PRESS
                                            deviceSpecific = true
                                            listeningFor = action
                                        }) { Text(tr("Add device", "添加设备")) }
                                        TextButton(onClick = {
                                            actionBindings.firstOrNull()?.let { existing ->
                                                captureMode = existing.chord.matchMode
                                                captureTrigger = existing.trigger
                                                deviceSpecific = existing.deviceDescriptor != null
                                            }
                                            listeningFor = action
                                        }) { Text(tr("Change", "更改")) }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = if (listeningFor == action) SageContainer else Color.Transparent),
                            )
                        }
                    }
                    item { OutlinedButton(onClick = { onChange(settings.copy(shortcutBindings = CspShortcutResolver.defaultBindings)); listeningFor = null }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Restore CSP defaults", "恢复 CSP 默认键位")) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
    if (quickSlotsOpen) ToolboxQuickSlotEditorDialog(settings, brushes, brushGroups, onChange, onDismiss = { quickSlotsOpen = false })
}

@Composable
private fun ToolboxAxisBindingRow(axis: ToolboxAxis, settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val binding = settings.shortcutAxisBindings.firstOrNull { it.axis == axis && it.deviceDescriptor == null }
    val deviceBindings = settings.shortcutAxisBindings.count { it.axis == axis && it.deviceDescriptor != null }
    val label = when (axis) {
        ToolboxAxis.ROTARY -> tr("Rotary / touch ring", "旋钮／触控环")
        ToolboxAxis.VERTICAL_SCROLL -> tr("Vertical wheel", "垂直滚轮")
        ToolboxAxis.HORIZONTAL_SCROLL -> tr("Horizontal wheel", "水平滚轮")
    }
    val modeLabel = when (binding?.mode) {
        ToolboxAxisMode.BRUSH_SIZE -> tr("Brush size", "笔刷大小")
        ToolboxAxisMode.OPACITY -> tr("Opacity", "不透明度")
        ToolboxAxisMode.ZOOM -> tr("Canvas zoom", "画布缩放")
        null -> tr("Not assigned", "未分配")
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolboxQuickSlotDropdown(
            label = label,
            selected = modeLabel,
            choices = listOf(
                "" to tr("Not assigned", "未分配"),
                ToolboxAxisMode.BRUSH_SIZE.name to tr("Brush size", "笔刷大小"),
                ToolboxAxisMode.OPACITY.name to tr("Opacity", "不透明度"),
                ToolboxAxisMode.ZOOM.name to tr("Canvas zoom", "画布缩放"),
            ),
        ) { selected ->
            val retained = settings.shortcutAxisBindings.filterNot { it.axis == axis && it.deviceDescriptor == null }
            val updated = selected.takeIf(String::isNotBlank)?.let { mode ->
                retained + AxisShortcutBinding(axis, enumValueOf(mode), reversed = binding?.reversed ?: false, sensitivity = binding?.sensitivity ?: 1f, deadZone = binding?.deadZone ?: .01f)
            } ?: retained
            onChange(settings.copy(shortcutAxisBindings = updated))
        }
        if (binding != null) {
            SettingSwitch(tr("Reverse direction", "反转方向"), binding.reversed) {
                onChange(settings.copy(shortcutAxisBindings = ToolboxMotionResolver.replaceAxis(settings.shortcutAxisBindings, binding.copy(reversed = it))))
            }
            BrushSettingSlider(tr("Axis sensitivity", "轴灵敏度"), binding.sensitivity, .25f..4f) {
                onChange(settings.copy(shortcutAxisBindings = ToolboxMotionResolver.replaceAxis(settings.shortcutAxisBindings, binding.copy(sensitivity = it))))
            }
        }
        if (deviceBindings > 0) Text(tr("$deviceBindings device-specific mapping(s) retained", "已保留 $deviceBindings 个设备专用映射"), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun ToolboxButtonBindingRow(index: Int, button: Int, settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val language = LocalUiLanguage.current
    val binding = settings.shortcutButtonBindings.firstOrNull { it.button == button && it.deviceDescriptor == null }
    val deviceBindings = settings.shortcutButtonBindings.count { it.button == button && it.deviceDescriptor != null }
    val label = tr("Button $index", "按钮 $index")
    val selected = binding?.let { shortcutActionDisplayLabel(it.action, settings, language) } ?: tr("Not assigned", "未分配")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolboxQuickSlotDropdown(
            label = label,
            selected = selected,
            choices = listOf("" to tr("Not assigned", "未分配")) + ShortcutAction.entries.map { action -> action.name to shortcutActionDisplayLabel(action, settings, language) },
        ) { selectedAction ->
            val retained = settings.shortcutButtonBindings.filterNot { it.button == button && it.deviceDescriptor == null }
            val updated = selectedAction.takeIf(String::isNotBlank)?.let { action ->
                retained + ButtonShortcutBinding(button, enumValueOf(action), binding?.trigger ?: ShortcutTrigger.PRESS)
            } ?: retained
            onChange(settings.copy(shortcutButtonBindings = updated))
        }
        if (binding != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioFilterChip(selected = binding.trigger == ShortcutTrigger.PRESS, onClick = { onChange(settings.copy(shortcutButtonBindings = ToolboxMotionResolver.replaceButton(settings.shortcutButtonBindings, binding.copy(trigger = ShortcutTrigger.PRESS)))) }, label = { Text(tr("Tap", "单击")) })
                StudioFilterChip(selected = binding.trigger == ShortcutTrigger.HOLD, onClick = { onChange(settings.copy(shortcutButtonBindings = ToolboxMotionResolver.replaceButton(settings.shortcutButtonBindings, binding.copy(trigger = ShortcutTrigger.HOLD)))) }, label = { Text(tr("Hold / release", "按住／松开")) })
            }
        }
        if (deviceBindings > 0) Text(tr("$deviceBindings device-specific mapping(s) retained", "已保留 $deviceBindings 个设备专用映射"), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun ShortcutBrushGroupTargetRow(group: ShortcutBrushGroup, selectedPath: String, groupPaths: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (group) {
        ShortcutBrushGroup.PEN -> tr("Pen", "钢笔")
        ShortcutBrushGroup.PENCIL -> tr("Pencil", "铅笔")
        ShortcutBrushGroup.BRUSH -> "Brushes"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(84.dp), fontSize = 12.sp)
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedPath.ifBlank { tr("Automatic", "自动") }, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                StudioMenuItem(
                    text = { Text(tr("Automatic", "自动")) },
                    onClick = { onSelect(""); expanded = false },
                )
                groupPaths.forEach { path ->
                    StudioMenuItem(
                        text = { Text(path) },
                        onClick = { onSelect(path); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolboxQuickSlotEditorDialog(
    settings: AppSettings,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    onChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    val slots = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)
    val slot = slots[selectedIndex]
    fun updateSlot(value: ToolboxQuickSlot) {
        val updated = slots.toMutableList().also { it[selectedIndex] = ToolboxQuickSlots.normalize(value) }
        onChange(settings.copy(toolboxQuickSlots = updated))
    }
    var colorText by remember(selectedIndex, slot.colorArgb) { mutableStateOf("#%06X".format(slot.colorArgb and 0xFFFFFF)) }
    StudioDialog(
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text(tr("Toolbox quick slots", "Toolbox 快速槽")) },
        text = {
            LazyColumn(Modifier.dialogContentWidth(380.dp, 560.dp).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToolboxQuickSlots.COUNT) { index ->
                            StudioFilterChip(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                label = { Text(tr("Slot ${index + 1}", "槽 ${index + 1}")) },
                            )
                        }
                    }
                }
                item {
                    StudioOutlinedTextField(
                        value = slot.label,
                        onValueChange = { updateSlot(slot.copy(label = it)) },
                        label = { Text(tr("Slot label", "快速槽名称")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(tr("RESTORE", "恢复内容"), color = MutedInk, fontSize = 11.sp)
                    // Four choices wrap instead of scrolling sideways, so all stay visible at large fonts.
                    androidx.compose.foundation.layout.FlowRow {
                        ToolboxQuickSlotKind.entries.forEach { kind ->
                            StudioFilterChip(
                                selected = slot.kind == kind,
                                onClick = { updateSlot(slot.copy(kind = kind)) },
                                label = { Text(toolboxQuickSlotKindLabel(kind)) },
                            )
                        }
                    }
                }
                when (slot.kind) {
                    ToolboxQuickSlotKind.EMPTY -> Unit
                    ToolboxQuickSlotKind.BRUSH -> item {
                        ToolboxQuickSlotDropdown(
                            label = tr("Brush", "笔刷"),
                            selected = brushes.firstOrNull { it.id == slot.brushId }?.name.orEmpty(),
                            choices = brushes.filterNot(ImportedBrush::hidden).map { it.id to "${it.groupName.ifBlank { tr("Ungrouped", "未分组") }} / ${it.name}" },
                            onSelect = { updateSlot(slot.copy(brushId = it)) },
                        )
                    }
                    ToolboxQuickSlotKind.BRUSH_GROUP -> item {
                        ToolboxQuickSlotDropdown(
                            label = tr("Brush group", "笔刷组"),
                            selected = slot.groupPath,
                            choices = brushGroups.map { it to it },
                            onSelect = { updateSlot(slot.copy(groupPath = it)) },
                        )
                    }
                    ToolboxQuickSlotKind.TOOL_PRESET -> {
                        item {
                            ToolboxQuickSlotDropdown(
                                label = tr("Brush", "笔刷"),
                                selected = brushes.firstOrNull { it.id == slot.brushId }?.name ?: tr("System pen", "系统钢笔"),
                                choices = listOf("" to tr("System pen", "系统钢笔")) + brushes.filterNot(ImportedBrush::hidden).map { it.id to "${it.groupName.ifBlank { tr("Ungrouped", "未分组") }} / ${it.name}" },
                                onSelect = { updateSlot(slot.copy(brushId = it)) },
                            )
                        }
                        item { BrushSettingSlider(tr("Size", "大小"), slot.size, .5f..400f) { updateSlot(slot.copy(size = it)) } }
                        item { BrushSettingSlider(tr("Opacity", "不透明度"), slot.opacity, .05f..1f) { updateSlot(slot.copy(opacity = it)) } }
                        item {
                            StudioOutlinedTextField(
                                value = colorText,
                                onValueChange = { raw ->
                                    colorText = raw.take(9)
                                    val hex = raw.removePrefix("#")
                                    if (hex.length == 6) hex.toLongOrNull(16)?.let { rgb -> updateSlot(slot.copy(colorArgb = (0xFF000000L or rgb).toInt())) }
                                    if (hex.length == 8) hex.toLongOrNull(16)?.let { argb -> updateSlot(slot.copy(colorArgb = argb.toInt())) }
                                },
                                label = { Text(tr("Color (#RRGGBB)", "颜色（#RRGGBB）")) },
                                singleLine = true,
                                leadingIcon = { Box(Modifier.size(20.dp).background(Color(slot.colorArgb), CircleShape).border(1.dp, OutlineSoft, CircleShape)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { updateSlot(ToolboxQuickSlot()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Clear, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Clear this slot", "清空此快速槽"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

@Composable
private fun ToolboxQuickSlotDropdown(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = choices.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label quick slot value" },
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = MutedInk, fontSize = 10.sp)
                Text(selected.ifBlank { tr("Choose…", "请选择…") }, maxLines = 1)
            }
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, name) ->
                StudioMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun toolboxQuickSlotKindLabel(kind: ToolboxQuickSlotKind): String = when (kind) {
    ToolboxQuickSlotKind.EMPTY -> tr("Empty", "空")
    ToolboxQuickSlotKind.BRUSH -> tr("Brush", "笔刷")
    ToolboxQuickSlotKind.BRUSH_GROUP -> tr("Group", "笔刷组")
    ToolboxQuickSlotKind.TOOL_PRESET -> tr("Tool preset", "工具预设")
}

private fun shortcutCategoryLabel(category: ShortcutCategory, language: UiLanguage): String = when (category) {
    ShortcutCategory.QUICK_ACCESS -> language.text("Quick access", "快速访问")
    ShortcutCategory.TOOLS -> language.text("Tools", "工具")
    ShortcutCategory.BRUSH_GROUPS -> language.text("Brush groups", "笔刷组")
    ShortcutCategory.BRUSH_ADJUSTMENTS -> language.text("Brush adjustments", "笔刷调节")
    ShortcutCategory.EDITING -> language.text("Editing", "编辑")
    ShortcutCategory.CANVAS -> language.text("Canvas", "画布")
    ShortcutCategory.PAGES -> language.text("Pages", "页面")
    ShortcutCategory.LAYERS -> language.text("Layers", "图层")
    ShortcutCategory.PANELS -> language.text("Panels", "面板")
    ShortcutCategory.FILE -> language.text("File", "文件")
}

private fun shortcutActionLabel(action: ShortcutAction, language: UiLanguage): String = when (action) {
    ShortcutAction.PEN_OR_PENCIL_GROUP -> language.text("Switch pen / pencil group", "切换钢笔／铅笔组")
    ShortcutAction.PEN_GROUP -> language.text("Switch directly to pen group", "直接切换到钢笔组")
    ShortcutAction.PENCIL_GROUP -> language.text("Switch directly to pencil group", "直接切换到铅笔组")
    ShortcutAction.BRUSH_GROUP -> language.text("Switch brush group", "切换 Brushes 组")
    ShortcutAction.TOGGLE_ERASER -> language.text("Toggle eraser and previous tool", "橡皮与上一工具切换")
    ShortcutAction.HIGHLIGHTER_TOOL -> language.text("Marker / highlighter", "标记笔／荧光笔")
    ShortcutAction.SELECT_TOOL -> language.text("Selection tool", "选择工具")
    ShortcutAction.HAND_TOOL -> language.text("Hand / canvas navigation", "抓手／画布导航")
    ShortcutAction.ROTATE_VIEW_TOOL -> language.text("Rotate canvas view", "旋转画布视图")
    ShortcutAction.EYEDROPPER_TOOL -> language.text("Eyedropper", "吸管")
    ShortcutAction.SHAPE_TOOL -> language.text("Shape tool", "形状工具")
    ShortcutAction.TEXT_TOOL -> language.text("Text tool", "文字工具")
    ShortcutAction.NOTE_TOOL -> language.text("Sticky note", "便笺")
    ShortcutAction.LINK_TOOL -> language.text("Link", "链接")
    ShortcutAction.IMAGE_TOOL -> language.text("Insert image", "插入图片")
    ShortcutAction.LASER_TOOL -> language.text("Laser pointer", "激光笔")
    ShortcutAction.BRUSH_SIZE_DECREASE -> language.text("Decrease brush size", "减小笔刷大小")
    ShortcutAction.BRUSH_SIZE_INCREASE -> language.text("Increase brush size", "增大笔刷大小")
    ShortcutAction.OPACITY_DECREASE -> language.text("Decrease opacity", "降低不透明度")
    ShortcutAction.OPACITY_INCREASE -> language.text("Increase opacity", "提高不透明度")
    ShortcutAction.PREVIOUS_BRUSH -> language.text("Previous brush", "上一个笔刷")
    ShortcutAction.NEXT_BRUSH -> language.text("Next brush", "下一个笔刷")
    ShortcutAction.CUT_SELECTION -> language.text("Cut selection", "剪切选区")
    ShortcutAction.COPY_SELECTION -> language.text("Copy selection", "复制选区")
    ShortcutAction.PASTE_SELECTION -> language.text("Paste selection", "粘贴选区")
    ShortcutAction.PASTE_SELECTION_AT_VIEW -> language.text("Paste to shown position", "粘贴至显示位置")
    ShortcutAction.DELETE_SELECTION -> language.text("Delete selection", "删除选区")
    ShortcutAction.SELECT_ALL -> language.text("Select all ink strokes", "选择全部墨迹")
    ShortcutAction.DESELECT -> language.text("Deselect", "取消选择")
    ShortcutAction.UNDO -> language.text("Undo", "撤销")
    ShortcutAction.REDO -> language.text("Redo", "重做")
    ShortcutAction.NEW_LAYER -> language.text("New raster layer", "新建栅格图层")
    ShortcutAction.MERGE_LAYER_DOWN -> language.text("Merge with layer below", "与下一图层合并")
    ShortcutAction.SELECT_LAYER_ABOVE -> language.text("Select layer above", "选择上一图层")
    ShortcutAction.SELECT_LAYER_BELOW -> language.text("Select layer below", "选择下一图层")
    ShortcutAction.ZOOM_OUT -> language.text("Zoom out", "缩小")
    ShortcutAction.ZOOM_IN -> language.text("Zoom in", "放大")
    ShortcutAction.ROTATE_VIEW_LEFT -> language.text("Rotate view left 5°", "视图向左旋转 5°")
    ShortcutAction.ROTATE_VIEW_RIGHT -> language.text("Rotate view right 5°", "视图向右旋转 5°")
    ShortcutAction.FIT_PAGE -> language.text("Fit page", "适合页面")
    ShortcutAction.ACTUAL_SIZE -> language.text("Actual size (100%)", "实际大小（100%）")
    ShortcutAction.FIT_WIDTH -> language.text("Fit width", "适合宽度")
    ShortcutAction.RESET_VIEW_ROTATION -> language.text("Reset view rotation", "重置视图旋转")
    ShortcutAction.FLIP_VIEW_HORIZONTAL -> language.text("Flip view horizontally", "水平翻转视图")
    ShortcutAction.OBTAIN_SCREEN_COLOR -> language.text("Obtain screen color", "屏幕取色")
    ShortcutAction.TOGGLE_RULER -> language.text("Show / hide ruler", "显示／隐藏直尺")
    ShortcutAction.TOGGLE_GRID_SNAP -> language.text("Show / hide snap grid", "显示／隐藏吸附网格")
    ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> language.text("Show / hide alignment guides", "显示／隐藏对齐参考线")
    ShortcutAction.PREVIOUS_PAGE -> language.text("Previous page", "上一页")
    ShortcutAction.NEXT_PAGE -> language.text("Next page", "下一页")
    ShortcutAction.PAGE_BACK -> language.text("Back to previous view", "返回上一视图")
    ShortcutAction.PAGE_FORWARD -> language.text("Forward to next view", "前进到下一视图")
    ShortcutAction.TOGGLE_BRUSH_PANEL -> language.text("Show / hide brush panel", "显示／隐藏笔刷栏")
    ShortcutAction.TOGGLE_LAYER_PANEL -> language.text("Show / hide layer panel", "显示／隐藏图层栏")
    ShortcutAction.TOGGLE_PANELS -> language.text("Hide / show panels", "隐藏／显示面板")
    ShortcutAction.SAVE_CHECKPOINT -> language.text("Save checkpoint", "保存检查点")
    ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
    ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8 -> {
        val number = app.inkloom.settings.ToolboxQuickSlots.indexOf(action)?.plus(1) ?: 1
        language.text("Quick slot $number", "快速槽 $number")
    }
}

private fun shortcutActionDisplayLabel(action: ShortcutAction, settings: AppSettings, language: UiLanguage): String {
    val base = shortcutActionLabel(action, language)
    val index = ToolboxQuickSlots.indexOf(action) ?: return base
    val custom = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)[index].label
    return if (custom.isBlank()) base else "$base · $custom"
}

@Composable
private fun PressureCurveEditor(values: List<Float>, onChange: (List<Float>) -> Unit) {
    val curve = PressureResponseCurve.normalize(values)
    var draft by remember { mutableStateOf<List<Float>?>(null) }
    var selected by remember { mutableIntStateOf(-1) }
    val shown = draft ?: curve
    val currentCurve by rememberUpdatedState(curve)
    val currentOnChange by rememberUpdatedState(onChange)
    val graphDescription = tr("Editable pressure curve", "可编辑压感曲线")
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Pressure response curve", "压感响应曲线"), color = MutedInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { currentOnChange(PressureResponseCurve.SOFT) }) { Text(tr("Soft", "柔和"), fontSize = 11.sp) }
            TextButton(onClick = { currentOnChange(PressureResponseCurve.LINEAR) }) { Text(tr("Linear", "线性"), fontSize = 11.sp) }
            TextButton(onClick = { currentOnChange(PressureResponseCurve.FIRM) }) { Text(tr("Firm", "硬朗"), fontSize = 11.sp) }
        }
        Canvas(
            Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(10.dp)).background(Workspace).padding(22.dp)
                .semantics { contentDescription = graphDescription }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var working = currentCurve
                        val pointCount = working.size / 2
                        var index = (0 until pointCount).minBy { i ->
                            val dx = down.position.x - size.width * working[i * 2]
                            val dy = down.position.y - size.height * (1f - working[i * 2 + 1])
                            dx * dx + dy * dy
                        }
                        val nearest = Offset(size.width * working[index * 2], size.height * (1f - working[index * 2 + 1]))
                        if ((down.position - nearest).getDistance() > 24.dp.toPx()) {
                            if (pointCount >= 8) return@awaitEachGesture
                            val x = (down.position.x / size.width.coerceAtLeast(1)).coerceIn(.015f, .985f)
                            index = (1 until pointCount).first { working[it * 2] >= x }
                            if (x - working[(index - 1) * 2] < .015f || working[index * 2] - x < .015f) return@awaitEachGesture
                            val y = (1f - down.position.y / size.height.coerceAtLeast(1))
                                .coerceIn(working[index * 2 - 1], working[index * 2 + 1])
                            working = working.toMutableList().apply { addAll(index * 2, listOf(x, y)) }
                        }
                        if (index == 0 || index == working.size / 2 - 1) return@awaitEachGesture
                        selected = index
                        down.consume()
                        val graphWidth = size.width.coerceAtLeast(1)
                        val graphHeight = size.height.coerceAtLeast(1)
                        fun update(position: Offset) {
                            working = working.toMutableList().apply {
                                val lowerX = this[(index - 1) * 2] + .015f
                                val upperX = maxOf(lowerX, this[(index + 1) * 2] - .015f)
                                this[index * 2] = (position.x / graphWidth).coerceIn(lowerX, upperX)
                                this[index * 2 + 1] = (1f - position.y / graphHeight)
                                    .coerceIn(this[index * 2 - 1], this[index * 2 + 3])
                            }
                            draft = working
                        }
                        update(down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                update(change.position)
                                if (!change.pressed) {
                                    val margin = 20.dp.toPx()
                                    val p = change.position
                                    if (p.x < -margin || p.y < -margin || p.x > size.width + margin || p.y > size.height + margin) {
                                        working = working.toMutableList().apply { removeAt(index * 2); removeAt(index * 2) }
                                    }
                                    currentOnChange(working)
                                    break
                                }
                            }
                        } finally { draft = null; selected = -1 }
                    }
                },
        ) {
            for (i in 0..4) {
                val x = size.width * i / 4f; val y = size.height * i / 4f
                drawLine(OutlineSoft.copy(alpha = .6f), Offset(x, 0f), Offset(x, size.height), 1f)
                drawLine(OutlineSoft.copy(alpha = .6f), Offset(0f, y), Offset(size.width, y), 1f)
            }
            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, 0f), 1f)
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..240) {
                val x = i / 240f
                val y = size.height * (1f - PressureResponseCurve.apply(x, shown))
                if (i == 0) path.moveTo(0f, y) else path.lineTo(x * size.width, y)
            }
            drawPath(path, Sage, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            for (i in 0 until shown.size / 2) {
                val point = Offset(shown[i * 2] * size.width, (1f - shown[i * 2 + 1]) * size.height)
                drawCircle(Color.White, if (i == selected) 10.dp.toPx() else 7.dp.toPx(), point)
                drawCircle(Sage, if (i == selected) 7.dp.toPx() else 4.5.dp.toPx(), point)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("Input pressure →", "输入压力 →"), fontSize = 10.sp, color = MutedInk)
            if (selected >= 0 && selected * 2 + 1 < shown.size) Text(
                "${(shown[selected * 2] * 100).toInt()}% → ${(shown[selected * 2 + 1] * 100).toInt()}%",
                fontSize = 11.sp, color = MutedInk,
            ) else Text(tr("Output ↑", "输出 ↑"), fontSize = 10.sp, color = MutedInk)
        }
    }
}

@Composable
internal fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(localizedBrushLabel(title), Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChecked, modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun StylusActionRow(title: String, selected: StylusButtonAction, trigger: ShortcutTrigger, onSelected: (StylusButtonAction) -> Unit, onTrigger: (ShortcutTrigger) -> Unit) {
    Column {
        Text(title, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(StylusButtonAction.entries, key = { it.name }) { action ->
                StudioFilterChip(selected = selected == action, onClick = { onSelected(action) }, label = { Text(when (action) {
                    StylusButtonAction.ERASER -> tr("Eraser", "橡皮")
                    StylusButtonAction.LASSO -> tr("Lasso", "套索")
                    StylusButtonAction.HAND -> tr("Hand", "抓手")
                    StylusButtonAction.EYEDROPPER -> tr("Eyedropper", "吸管")
                    StylusButtonAction.UNDO -> tr("Undo", "撤销")
                    StylusButtonAction.REDO -> tr("Redo", "重做")
                    StylusButtonAction.NONE -> tr("None", "无")
                }) })
            }
        }
        if (selected !in setOf(StylusButtonAction.UNDO, StylusButtonAction.REDO, StylusButtonAction.NONE)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioFilterChip(selected = trigger == ShortcutTrigger.HOLD, onClick = { onTrigger(ShortcutTrigger.HOLD) }, label = { Text(tr("Hold temporarily", "按住临时切换")) })
                StudioFilterChip(selected = trigger == ShortcutTrigger.PRESS, onClick = { onTrigger(ShortcutTrigger.PRESS) }, label = { Text(tr("Tap to switch", "单击切换")) })
            }
        }
    }
}
