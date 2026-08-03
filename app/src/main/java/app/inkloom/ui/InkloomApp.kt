package app.inkloom.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inkloom.document.*
import app.inkloom.importer.BrushImportRepository
import app.inkloom.importer.BrushGroupOperations
import app.inkloom.importer.BrushGroupStore
import app.inkloom.importer.BrushMetadataStore
import app.inkloom.importer.BrushRoleClassifier
import app.inkloom.importer.BrushVersion
import app.inkloom.importer.BrushVersionHistoryStore
import app.inkloom.model.*
import app.inkloom.settings.AppSettings
import app.inkloom.settings.AppSettingsBackupCodec
import app.inkloom.settings.AppSettingsStore
import app.inkloom.settings.AxisShortcutBinding
import app.inkloom.settings.BuiltInWorkspace
import app.inkloom.settings.BuiltInWorkspaces
import app.inkloom.settings.ButtonShortcutBinding
import app.inkloom.settings.ColorPaletteStore
import app.inkloom.settings.CustomWorkspaceCodec
import app.inkloom.settings.CustomWorkspaceProfile
import app.inkloom.settings.CustomWorkspaces
import app.inkloom.settings.EraserPreset
import app.inkloom.settings.FloatingToolbarPlacement
import app.inkloom.settings.SelectionFilter
import app.inkloom.settings.SelectionMode
import app.inkloom.settings.SelectionShape
import app.inkloom.settings.ShortcutAction
import app.inkloom.settings.ShortcutActivation
import app.inkloom.settings.ShortcutCategory
import app.inkloom.settings.CspShortcutResolver
import app.inkloom.settings.KeyInputDiagnostic
import app.inkloom.settings.MotionInputDiagnostic
import app.inkloom.settings.ShortcutChord
import app.inkloom.settings.ShortcutMatchMode
import app.inkloom.settings.ShortcutPhase
import app.inkloom.settings.ShortcutProfile
import app.inkloom.settings.ShortcutProfileCodec
import app.inkloom.settings.ShortcutTrigger
import app.inkloom.settings.ShortcutBrushGroup
import app.inkloom.settings.ShortcutBrushSelection
import app.inkloom.settings.category
import app.inkloom.settings.StylusButtonAction
import app.inkloom.settings.ToolbarDock
import app.inkloom.settings.ToolboxAxis
import app.inkloom.settings.ToolboxAxisMode
import app.inkloom.settings.ToolboxMotionResolver
import app.inkloom.settings.ToolboxQuickSlot
import app.inkloom.settings.ToolboxQuickSlotKind
import app.inkloom.settings.ToolboxQuickSlots
import app.inkloom.settings.applyToolboxWorkspace
import app.inkloom.settings.captureToolboxWorkspace
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.WorkspacePanelDock
import app.inkloom.settings.WorkspacePanelDocking
import app.inkloom.settings.WorkspacePanelGrouping
import app.inkloom.settings.WorkspacePanelKind
import app.inkloom.settings.WorkspacePanelMode
import app.inkloom.settings.WorkspacePanelTabs
import app.inkloom.settings.WorkspacePanelBounds
import app.inkloom.settings.WorkspacePanelPlacement
import app.inkloom.settings.WorkspaceOrientation
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private enum class AppDestination { LIBRARY, EDITOR }
private enum class BrushBatchGroupOperation { MOVE, COPY }
private const val UNGROUPED_BRUSH_FILTER = "\u0000ungrouped"
private val LocalUiLanguage = staticCompositionLocalOf { UiLanguage.ENGLISH }

@Composable
private fun tr(english: String, simplifiedChinese: String): String = LocalUiLanguage.current.text(english, simplifiedChinese)

@Composable
private fun localizedShapeKind(kind: ShapeKind): String = when (kind) {
    ShapeKind.ELLIPSE -> tr("Ellipse", "椭圆")
    ShapeKind.RECTANGLE -> tr("Rectangle", "矩形")
    ShapeKind.TRIANGLE -> tr("Triangle", "三角形")
    ShapeKind.DIAMOND -> tr("Diamond", "菱形")
    ShapeKind.LINE -> tr("Line", "直线")
    ShapeKind.ARROW -> tr("Arrow", "箭头")
    ShapeKind.FREEFORM -> tr("Freeform", "自由形状")
}

@Composable
private fun localizedTextAlignment(alignment: InkTextAlignment): String = when (alignment) {
    InkTextAlignment.START -> tr("Start", "左对齐")
    InkTextAlignment.CENTER -> tr("Center", "居中")
    InkTextAlignment.END -> tr("End", "右对齐")
}

@Composable
private fun localizedListStyle(style: TextListStyle): String = when (style) {
    TextListStyle.NONE -> tr("None", "无")
    TextListStyle.BULLET -> tr("Bullet", "项目符号")
    TextListStyle.NUMBERED -> tr("Numbered", "编号")
    TextListStyle.CHECKBOX -> tr("Checkbox", "复选框")
}

@Composable
private fun localizedImageMask(mask: ImageMask): String = when (mask) {
    ImageMask.RECTANGLE -> tr("Rectangle", "矩形")
    ImageMask.ROUNDED -> tr("Rounded", "圆角")
    ImageMask.OVAL -> tr("Oval", "椭圆")
}

@Composable
private fun localizedTaperMode(mode: BrushTaperMode): String = when (mode) {
    BrushTaperMode.DISTANCE -> tr("Distance", "距离")
    BrushTaperMode.PERCENTAGE -> tr("Percentage", "百分比")
    BrushTaperMode.FADE -> tr("Fade", "淡出")
}

@Composable
private fun localizedBrushLabel(raw: String): String {
    if (LocalUiLanguage.current == UiLanguage.ENGLISH) return raw
    return when (raw) {
        "Size" -> "大小"
        "Stabilization" -> "防抖"
        "Adjust stabilization by speed" -> "根据速度调整防抖"
        "Uses the imported CSP correction behavior when available." -> "可用时采用导入的 CSP 修正行为。"
        "Default opacity" -> "默认不透明度"
        "Use as eraser" -> "用作橡皮擦"
        "Selects the eraser tool and uses this tip shape to remove ink." -> "选择橡皮擦工具，并使用此笔尖形状擦除墨迹。"
        "Pressure controls size" -> "压力控制大小"
        "Uses the imported CSP curve when available." -> "可用时采用导入的 CSP 曲线。"
        "Minimum size" -> "最小大小"
        "Size pressure" -> "大小压力"
        "Pressure controls opacity" -> "压力控制不透明度"
        "Minimum opacity" -> "最小不透明度"
        "Opacity pressure" -> "不透明度压力"
        "Opacity" -> "不透明度"
        "Pressure controls flow" -> "压力控制流量"
        "Minimum flow" -> "最小流量"
        "Flow pressure" -> "流量压力"
        "Flow" -> "流量"
        "Pressure controls spacing" -> "压力控制间距"
        "Imported from CSP Interval dynamics when available." -> "可用时从 CSP 间隔动态导入。"
        "Minimum spacing" -> "最小间距"
        "Spacing pressure" -> "间距压力"
        "Spacing" -> "间距"
        "Pressure controls thickness" -> "压力控制厚度"
        "Minimum thickness" -> "最小厚度"
        "Thickness pressure" -> "厚度压力"
        "Thickness" -> "厚度"
        "Brush blur" -> "笔刷模糊"
        "Pressure controls blur" -> "压力控制模糊"
        "Minimum blur" -> "最小模糊"
        "Blur pressure" -> "模糊压力"
        "Blur" -> "模糊"
        "Pressure controls particle size" -> "压力控制粒子大小"
        "Minimum particle size" -> "最小粒子大小"
        "Particle size pressure" -> "粒子大小压力"
        "Pressure controls particle density" -> "压力控制粒子密度"
        "Minimum particle density" -> "最小粒子密度"
        "Particle density pressure" -> "粒子密度压力"
        "Link blur to brush size" -> "模糊跟随笔刷大小"
        "Scales the imported CSP blur when brush size changes." -> "笔刷大小变化时同比缩放导入的 CSP 模糊。"
        "Starting taper" -> "起笔收尖"
        "Gradually grows from the configured minimum over the start of the stroke." -> "笔画起始处从设定的最小值逐渐增粗。"
        "Start length" -> "起笔长度"
        "Start minimum size" -> "起笔最小大小"
        "Fade" -> "淡出"
        "Ending taper" -> "收笔收尖"
        "Fades from full size toward the configured minimum." -> "从完整大小逐渐淡出到设定的最小值。"
        "Gradually narrows toward the configured minimum at stroke end." -> "笔画末端逐渐收窄到设定的最小值。"
        "Fade length" -> "淡出长度"
        "End length" -> "收笔长度"
        "End minimum size" -> "收笔最小大小"
        "Hardness" -> "硬度"
        "Anti-alias" -> "抗锯齿"
        "Secondary anti-alias" -> "辅助笔尖抗锯齿"
        "Paper texture density" -> "纸张纹理密度"
        "Paper texture scale" -> "纸张纹理缩放"
        "Paper texture rotation" -> "纸张纹理旋转"
        "Paper texture brightness" -> "纸张纹理亮度"
        "Paper texture contrast" -> "纸张纹理对比度"
        "Invert paper texture" -> "反转纸张纹理"
        "Emphasize paper texture" -> "增强纸张纹理"
        "Jitter grain origin per stroke" -> "每笔抖动纹理起点"
        "Keeps each stroke deterministic while avoiding an identical repeated grain start." -> "保持每笔结果稳定，同时避免纹理总从相同位置开始。"
        "Vertical thickness" -> "垂直厚度"
        "Applies thickness along the imported tip's vertical axis." -> "沿导入笔尖的垂直轴应用厚度。"
        "Ribbon stroke" -> "带状笔触"
        "Preserves the tip aspect ratio and continuously follows the stroke direction." -> "保持笔尖宽高比并持续跟随笔画方向。"
        "Rotation" -> "旋转"
        "Follow stylus orientation" -> "跟随触控笔方向"
        "Uses the pen direction reported by Android." -> "使用 Android 报告的触控笔方向。"
        "Follow stroke direction" -> "跟随笔画方向"
        "Rotates the tip along the drawn path." -> "让笔尖沿绘制路径旋转。"
        "Random rotation" -> "随机旋转"
        "Random start rotation" -> "随机起始旋转"
        "Stamp count" -> "笔印数量"
        "Stamp count jitter" -> "笔印数量抖动"
        "Color mix" -> "颜色混合"
        "Pressure controls color mix" -> "压力控制颜色混合"
        "Minimum color mix" -> "最小颜色混合"
        "Color mix pressure" -> "颜色混合压力"
        "Paint mix" -> "颜料混合"
        "Pressure controls paint load" -> "压力控制颜料量"
        "Minimum paint load" -> "最小颜料量"
        "Paint load pressure" -> "颜料量压力"
        "Paint load" -> "颜料量"
        "Watercolor approximation" -> "水彩近似"
        "Uses translucent layered stamps." -> "使用半透明的分层笔印。"
        "Watercolor edge" -> "水彩边缘"
        "Adds the CSP-style translucent edge pass before the main stamp." -> "在主笔印前添加 CSP 风格的半透明边缘。"
        "Edge radius" -> "边缘半径"
        "Edge opacity" -> "边缘不透明度"
        "Edge darkness" -> "边缘深度"
        "Edge blur" -> "边缘模糊"
        "Spray/scatter" -> "喷枪／散布"
        "Particle size" -> "粒子大小"
        "Particle size follows brush" -> "粒子大小跟随笔刷"
        "When enabled, particle size is a percentage of the current brush size." -> "开启后，粒子大小按当前笔刷大小的百分比计算。"
        "Particle density" -> "粒子密度"
        "Spray center bias" -> "喷枪中心偏移"
        "Scatter spread" -> "散布范围"
        "Particle rotation" -> "粒子旋转"
        "Particles follow stylus" -> "粒子跟随触控笔"
        "Uses the independent CSP particle-direction setting." -> "使用独立的 CSP 粒子方向设置。"
        "Particles follow stroke" -> "粒子跟随笔画"
        "Aligns particles with the local path direction." -> "让粒子与局部路径方向对齐。"
        "Particle random rotation" -> "粒子随机旋转"
        "Dual brush" -> "双重笔刷"
        "Uses the imported secondary tip as an independent stamp stream." -> "将导入的辅助笔尖作为独立笔印流。"
        "Secondary size" -> "辅助笔刷大小"
        "Secondary spacing" -> "辅助笔刷间距"
        "Secondary flow" -> "辅助笔刷流量"
        "Secondary hardness" -> "辅助笔刷硬度"
        "Secondary thickness" -> "辅助笔刷厚度"
        "Secondary vertical thickness" -> "辅助笔刷垂直厚度"
        "Secondary rotation" -> "辅助笔刷旋转"
        "Secondary follows stylus" -> "辅助笔刷跟随触控笔"
        "Secondary follows stroke" -> "辅助笔刷跟随笔画"
        "Secondary random rotation" -> "辅助笔刷随机旋转"
        "Secondary random start rotation" -> "辅助笔刷随机起始旋转"
        "Secondary stamp count" -> "辅助笔印数量"
        "Secondary count jitter" -> "辅助笔印数量抖动"
        "Secondary Ribbon" -> "辅助带状笔触"
        "Secondary spray/scatter" -> "辅助喷枪／散布"
        "Secondary particle size" -> "辅助粒子大小"
        "Secondary particles follow brush" -> "辅助粒子大小跟随笔刷"
        "Secondary particle density" -> "辅助粒子密度"
        "Secondary center bias" -> "辅助中心偏移"
        "Secondary scatter spread" -> "辅助散布范围"
        "Secondary particle rotation" -> "辅助粒子旋转"
        "Secondary particles follow stylus" -> "辅助粒子跟随触控笔"
        "Secondary particles follow stroke" -> "辅助粒子跟随笔画"
        "Secondary particle random rotation" -> "辅助粒子随机旋转"
        "Tilt" -> "倾斜"
        "Tilt minimum" -> "最小倾斜响应"
        "Velocity" -> "速度"
        "Velocity minimum" -> "最小速度响应"
        "Velocity response" -> "速度响应"
        "Random" -> "随机"
        "Deterministic per stamp, so export matches the canvas." -> "每个笔印都可重复计算，使导出结果与画布一致。"
        "Random minimum" -> "最小随机响应"
        "Text size" -> "文本大小"
        "Line spacing" -> "行距"
        "Collapsed" -> "折叠"
        "Show only the first line." -> "只显示第一行。"
        "Stroke width" -> "描边宽度"
        "Fill" -> "填充"
        "Adds a translucent shape fill." -> "添加半透明形状填充。"
        "Corner radius" -> "圆角半径"
        "Left" -> "左侧"
        "Top" -> "顶部"
        "Right" -> "右侧"
        "Bottom" -> "底部"
        "Width" -> "宽度"
        "Height" -> "高度"
        else -> raw
    }
}

@Composable
fun InkloomApp(initialIntent: Intent?) {
    var destination by rememberSaveable { mutableStateOf(AppDestination.LIBRARY) }
    var importError by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val viewModel: InkloomViewModel = viewModel()
    val libraryState by viewModel.state.collectAsState()
    val pageTemplates by viewModel.pageTemplates.collectAsState()
    val repository = remember { BrushImportRepository(context) }
    var pendingBrushExport by remember { mutableStateOf<ImportedBrush?>(null) }
    var pendingWorkspaceExport by remember { mutableStateOf<CustomWorkspaceProfile?>(null) }
    val brushStore = remember { BrushMetadataStore(context) }
    val brushGroupStore = remember { BrushGroupStore(context) }
    val settingsStore = remember { AppSettingsStore(context) }
    val colorPaletteStore = remember { ColorPaletteStore(context) }
    var settings by remember { mutableStateOf(settingsStore.load()) }
    var brushesOpen by remember { mutableStateOf(settings.showBrushPanel) }
    fun updateSettings(value: AppSettings) { settings = settingsStore.save(value) }
    var importedBrushes by remember { mutableStateOf(brushStore.load()) }
    var brushGroups by remember { mutableStateOf(BrushGroupOperations.reconcileRegisteredGroups(brushGroupStore.load(), importedBrushes)) }
    var pendingBrushImportGroup by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val refreshed = withContext(Dispatchers.IO) { importedBrushes.flatMap(repository::refreshMetadataAll) }.distinctBy(ImportedBrush::id)
        if (refreshed != importedBrushes) { importedBrushes = refreshed; brushStore.save(refreshed) }
        val reconciledGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, refreshed)
        if (reconciledGroups != brushGroups) { brushGroups = reconciledGroups; brushGroupStore.save(reconciledGroups) }
    }

    val brushPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val results = uris.map { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            repository.importAll(uri)
        }
        val additions = results.flatMap { it.getOrNull().orEmpty() }.let { brushes ->
            pendingBrushImportGroup?.let { group -> brushes.map { BrushGroupOperations.assign(it, group) } } ?: brushes
        }
        pendingBrushImportGroup = null
        importedBrushes = BrushGroupOperations.mergeImports(importedBrushes, additions)
        brushStore.save(importedBrushes)
        brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
        brushGroupStore.save(brushGroups)
        importError = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: settings.language.text("Imported document", "导入的文档")
            when {
                name.endsWith(".inkloom.zip", true) || name.endsWith(".inkloom", true) -> viewModel.importBundle(uri)
                name.endsWith(".pdf", true) -> viewModel.importPdf(uri, name)
                else -> viewModel.importImage(uri, name)
            }
            destination = AppDestination.EDITOR
        }.onFailure { importError = it.message }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching { viewModel.exportCurrent(uri) }.onFailure { importError = it.message }
    }
    val imageExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri -> if (uri != null) runCatching { viewModel.exportCurrentPage(uri) }.onFailure { importError = it.message } }
    val bundleExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) runCatching { viewModel.exportCurrentBundle(uri) }.onFailure { importError = it.message } }
    val libraryBackupExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val preferences = runCatching { AppSettingsBackupCodec.encode(settings, colorPaletteStore.load()) }
                .getOrElse { error -> importError = error.message; return@rememberLauncherForActivityResult }
            viewModel.exportLibraryBackup(uri, importedBrushes, brushGroups, preferences) { result ->
                result.onSuccess { Toast.makeText(context, settings.language.text("Library backup saved.", "资料库备份已保存。"), Toast.LENGTH_LONG).show() }
                    .onFailure { importError = it.message }
            }
        }
    }
    val libraryBackupImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreLibraryBackup(uri) { result ->
            result.onSuccess { restored ->
                importedBrushes = (importedBrushes + restored.brushes).distinctBy(ImportedBrush::id)
                brushStore.save(importedBrushes)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups + restored.brushGroups, importedBrushes)
                brushGroupStore.save(brushGroups)
                restored.preferences?.let { bytes ->
                    AppSettingsBackupCodec.decode(bytes).onSuccess { preferences ->
                        val restoredSettings = preferences.settings.copy(
                            toolboxQuickSlots = ToolboxQuickSlots.remapBrushIds(preferences.settings.toolboxQuickSlots, restored.brushIdMappings),
                        )
                        brushesOpen = restoredSettings.showBrushPanel
                        updateSettings(restoredSettings)
                        colorPaletteStore.replace(preferences.colorPalette)
                    }.onFailure { error ->
                        importError = settings.language.text(
                            "Notes and brushes were restored, but app settings were ignored: ${error.message}",
                            "笔记和笔刷已恢复，但应用设置因损坏而被忽略：${error.message}",
                        )
                    }
                }
                Toast.makeText(
                    context,
                    settings.language.text(
                        "Restored ${restored.documents.size} notes and ${restored.brushes.size} brushes.",
                        "已恢复 ${restored.documents.size} 份笔记和 ${restored.brushes.size} 支笔刷。",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { importError = it.message }
        }
    }
    val shortcutProfileExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ShortcutProfileCodec.MIME_TYPE)) { uri ->
        if (uri != null) runCatching {
            val profile = ShortcutProfile(settings.shortcutBindings, settings.shortcutBrushGroupTargets, settings.toolboxQuickSlots, settings.shortcutAxisBindings, settings.shortcutButtonBindings)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> output.write(ShortcutProfileCodec.encode(profile)) }
                ?: error("Unable to open the shortcut profile destination.")
            Toast.makeText(context, settings.language.text("Shortcut profile saved.", "快捷键配置已保存。"), Toast.LENGTH_LONG).show()
        }.onFailure { importError = it.message }
    }
    val shortcutProfileImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val imported = context.contentResolver.openInputStream(uri)?.use { ShortcutProfileCodec.decodeProfile(it).getOrThrow() }
                ?: error("Unable to open the shortcut profile.")
            updateSettings(settings.copy(shortcutBindings = imported.bindings, shortcutAxisBindings = imported.axisBindings, shortcutButtonBindings = imported.buttonBindings, shortcutBrushGroupTargets = imported.brushGroupTargets, toolboxQuickSlots = imported.quickSlots))
            Toast.makeText(context, settings.language.text("Shortcut profile restored.", "快捷键配置已恢复。"), Toast.LENGTH_LONG).show()
        }.onFailure { importError = it.message }
    }
    val workspaceProfileExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CustomWorkspaceCodec.MIME_TYPE)) { uri ->
        val profile = pendingWorkspaceExport
        pendingWorkspaceExport = null
        if (uri != null && profile != null) runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output -> output.write(CustomWorkspaceCodec.encodeProfile(profile)) }
                ?: error("Unable to open the workspace profile destination.")
            Toast.makeText(context, settings.language.text("Workspace exported.", "工作区已导出。"), Toast.LENGTH_LONG).show()
        }.onFailure { importError = it.message }
    }
    val workspaceProfileImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            require(settings.customWorkspaces.size < CustomWorkspaces.MAX_COUNT) {
                settings.language.text("The custom workspace library is full.", "自定义工作区数量已达上限。")
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { CustomWorkspaceCodec.decodeProfile(it).getOrThrow() }
                ?: error("Unable to open the workspace profile.")
            val imported = decoded.copy(
                id = UUID.randomUUID().toString(),
                name = CustomWorkspaces.uniqueName(decoded.name, settings.customWorkspaces),
            )
            updateSettings(settings.copy(customWorkspaces = CustomWorkspaces.normalize(settings.customWorkspaces + imported)))
            Toast.makeText(context, settings.language.text("Workspace imported.", "工作区已导入。"), Toast.LENGTH_LONG).show()
        }.onFailure { importError = it.message }
    }
    val brushExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.inkloom.brush+zip")) { uri ->
        val brush = pendingBrushExport
        pendingBrushExport = null
        if (uri != null && brush != null) repository.export(brush, uri).onFailure { importError = it.message }
    }
    val pageImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.insertImage(uri)
        }.onFailure { importError = it.message }
    }
    val pageTemplatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: settings.language.text("Imported template", "导入的模板")
            val imported = viewModel.importPageTemplates(uri, name)
            Toast.makeText(
                context,
                settings.language.text("Imported ${imported.size} page template(s).", "已导入 ${imported.size} 个页面模板。"),
                Toast.LENGTH_SHORT,
            ).show()
        }.onFailure { importError = it.message }
    }
    var pendingCoverTitle by remember { mutableStateOf("Untitled Note") }
    val coverImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            viewModel.createNoteWithCover(uri, pendingCoverTitle)
            destination = AppDestination.EDITOR
        }.onFailure { importError = it.message }
    }

    LaunchedEffect(initialIntent) {
        val uris = buildList<Uri> {
            initialIntent?.data?.let(::add)
            initialIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
        }
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                val type = context.contentResolver.getType(uri).orEmpty()
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: uri.lastPathSegment.orEmpty()
                when {
                    name.endsWith(".inkloom.zip", true) || name.endsWith(".inkloom", true) -> viewModel.importBundle(uri)
                    type == "application/pdf" || name.endsWith(".pdf", true) -> viewModel.importPdf(uri, name)
                    type.startsWith("image/") -> viewModel.importImage(uri, name)
                    else -> repository.importAll(uri).getOrNull()?.let { brushes -> importedBrushes = BrushGroupOperations.mergeImports(importedBrushes, brushes) }
                }
            }
            brushStore.save(importedBrushes)
            brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
            brushGroupStore.save(brushGroups)
            brushesOpen = true
            updateSettings(settings.copy(showBrushPanel = true))
            destination = AppDestination.EDITOR
        }
    }

    CompositionLocalProvider(LocalUiLanguage provides settings.language) {
    when (destination) {
        AppDestination.LIBRARY -> LibraryScreen(
            documents = libraryState.documents,
            folders = libraryState.folders,
            onOpen = { viewModel.select(it.id); destination = AppDestination.EDITOR },
            onNewNote = { title, style -> viewModel.createNote(title, style); destination = AppDestination.EDITOR },
            onImportCover = { title -> pendingCoverTitle = title; coverImagePicker.launch(arrayOf("image/*")) },
            onImport = { documentPicker.launch(arrayOf("application/pdf", "image/*", "application/zip")) },
            onFavorite = viewModel::toggleFavorite,
            onArchive = viewModel::toggleArchive,
            onTags = viewModel::setDocumentTags,
            onTrash = viewModel::trash,
            onRestore = viewModel::restore,
            onDelete = viewModel::deletePermanently,
            onRename = viewModel::rename,
            onDuplicate = viewModel::duplicateDocument,
            onCreateFolder = viewModel::createFolder,
            onMoveToFolder = viewModel::moveToFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onSettings = { settingsOpen = true },
        )
        AppDestination.EDITOR -> EditorScreen(
            document = libraryState.documents.firstOrNull { it.id == libraryState.selectedDocumentId } ?: viewModel.currentDocument(),
            documents = libraryState.documents,
            selectedPageId = libraryState.selectedPageId,
            brushesOpen = brushesOpen,
            brushes = importedBrushes,
            brushGroups = brushGroups,
            importError = importError,
            onBack = { destination = AppDestination.LIBRARY },
            onToggleBrushes = {
                val open = !brushesOpen
                brushesOpen = open
                updateSettings(settings.copy(showBrushPanel = open))
            },
            onImportBrushes = { group ->
                pendingBrushImportGroup = group?.let(BrushGroupOperations::normalize)?.takeIf(String::isNotBlank)
                brushPicker.launch(arrayOf("application/octet-stream", "application/zip", "application/vnd.inkloom.brush+zip", "image/*"))
            },
            onExportBrush = { brush ->
                pendingBrushExport = brush
                val safeName = brush.name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "Inkloom-Brush" }
                brushExportPicker.launch("$safeName.inkbrush")
            },
            onUpdateBrush = { updated ->
                importedBrushes = importedBrushes.map { if (it.id == updated.id) updated else it }
                brushStore.save(importedBrushes)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, importedBrushes)
                brushGroupStore.save(brushGroups)
            },
            onReplaceBrushes = { updated ->
                importedBrushes = updated
                brushStore.save(updated)
                brushGroups = BrushGroupOperations.reconcileRegisteredGroups(brushGroups, updated)
                brushGroupStore.save(brushGroups)
            },
            onReplaceBrushGroups = { updated -> brushGroups = updated; brushGroupStore.save(updated) },
            onDeleteBrush = { brushId -> importedBrushes = importedBrushes.filterNot { it.id == brushId }; brushStore.save(importedBrushes) },
            onReorderBrush = { brushId, delta -> importedBrushes = BrushGroupOperations.move(importedBrushes, brushId, delta); brushStore.save(importedBrushes) },
            onDismissError = { importError = null },
            onSelectPage = viewModel::selectPage,
            pageTemplates = pageTemplates,
            onAddPage = { templateId -> libraryState.selectedDocumentId?.let { viewModel.addPage(it, templateId) } },
            onDuplicatePage = { documentId, pageId -> viewModel.duplicatePage(documentId, pageId) },
            onDeletePage = { documentId, pageId -> viewModel.deletePage(documentId, pageId) },
            onMovePage = viewModel::movePage,
            onTransferPage = viewModel::transferPage,
            onApplyPageTemplate = viewModel::applyPageTemplate,
            onImportPageTemplates = { pageTemplatePicker.launch(arrayOf("application/pdf", "image/*")) },
            onTogglePageTemplateFavorite = viewModel::togglePageTemplateFavorite,
            onSetNotebookDefaultTemplate = viewModel::setNotebookDefaultTemplate,
            onSavePageAsTemplate = { documentId, pageId, name -> viewModel.savePageAsTemplate(documentId, pageId, name) },
            onDeletePageTemplate = viewModel::deletePageTemplate,
            onToggleBookmark = viewModel::toggleBookmark,
            onChangePageOutlineLevel = viewModel::changePageOutlineLevel,
            onTogglePageOutlineCollapsed = viewModel::togglePageOutlineCollapsed,
            onRenamePage = viewModel::renamePage,
            onSetPageMetadata = viewModel::setPageMetadata,
            onClearPageInk = viewModel::clearPageInk,
            onSetPageCrop = viewModel::setPageCrop,
            onRotatePage = viewModel::rotatePageClockwise,
            onAddLayer = viewModel::addLayer,
            onAddLayerFolder = viewModel::addLayerFolder,
            onRenameLayer = viewModel::renameLayer,
            onToggleLayerVisibility = viewModel::toggleLayerVisibility,
            onToggleLayerLock = viewModel::toggleLayerLock,
            onSetLayerOpacity = viewModel::setLayerOpacity,
            onSetLayerBlendMode = viewModel::setLayerBlendMode,
            onMoveLayerToFolder = viewModel::moveLayerToFolder,
            onToggleLayerFolderCollapsed = viewModel::toggleLayerFolderCollapsed,
            onMoveLayer = viewModel::moveLayer,
            onDuplicateLayer = viewModel::duplicateLayer,
            onMergeLayerDown = viewModel::mergeLayerDown,
            onDeleteLayer = viewModel::deleteLayer,
            onStroke = viewModel::addStroke,
            onRemoveStroke = viewModel::removeStroke,
            onStrokesReplaced = viewModel::replaceStrokes,
            onElement = viewModel::addElement,
            onRemoveElement = viewModel::removeElement,
            onDuplicateElement = viewModel::duplicateElement,
            onInsertImage = { pageImagePicker.launch(arrayOf("image/*")) },
            onExport = { exportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}.pdf") },
            onExportPage = { imageExportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}-page.png") },
            onExportBundle = { bundleExportPicker.launch("${viewModel.currentDocument()?.title ?: "Inkloom"}.inkloom.zip") },
            onExportWorkspace = { profile ->
                pendingWorkspaceExport = profile
                val safeName = profile.name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "Inkloom-Workspace" }
                workspaceProfileExportPicker.launch("$safeName.${CustomWorkspaceCodec.FILE_EXTENSION}")
            },
            onImportWorkspace = { workspaceProfileImportPicker.launch(arrayOf(CustomWorkspaceCodec.MIME_TYPE, "application/json", "text/plain", "application/octet-stream")) },
            assets = viewModel.assets,
            settings = settings,
            pdfIndexing = libraryState.selectedDocumentId in libraryState.pdfIndexingDocumentIds,
            onIndexPdfText = viewModel::indexPdfText,
            onSettings = { settingsOpen = true },
            onSettingsChange = ::updateSettings,
        )
    }
    if (settingsOpen) SettingsDialog(
        settings,
        brushes = importedBrushes,
        brushGroups = brushGroups,
        onChange = ::updateSettings,
        onExportBackup = { libraryBackupExportPicker.launch("Inkloom-library-backup.inkloom-backup.zip") },
        onRestoreBackup = { libraryBackupImportPicker.launch(arrayOf("application/zip", "application/octet-stream")) },
        onExportShortcuts = { shortcutProfileExportPicker.launch("Inkloom-toolbox.${ShortcutProfileCodec.FILE_EXTENSION}") },
        onImportShortcuts = { shortcutProfileImportPicker.launch(arrayOf(ShortcutProfileCodec.MIME_TYPE, "text/plain", "application/octet-stream")) },
        onDismiss = { settingsOpen = false },
    )
    }
}

private enum class LibraryFilter { ALL, FAVORITES, ARCHIVED, TRASH, PDF }

@Composable
private fun LibraryScreen(documents: List<InkDocument>, folders: List<InkFolder>, onOpen: (InkDocument) -> Unit, onNewNote: (String, CoverStyle) -> Unit, onImportCover: (String) -> Unit, onImport: () -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, onCreateFolder: (String, String?) -> Unit, onMoveToFolder: (String, String?) -> Unit, onDeleteFolder: (String) -> Unit, onSettings: () -> Unit) {
    var filter by remember { mutableStateOf(LibraryFilter.ALL) }
    var search by remember { mutableStateOf("") }
    var folderId by remember { mutableStateOf<String?>(null) }
    var newNoteOpen by remember { mutableStateOf(false) }
    val untitledNoteLabel = tr("Untitled Note", "未命名笔记")
    var newNoteTitle by remember { mutableStateOf(untitledNoteLabel) }
    var newNoteCover by remember { mutableStateOf(CoverStyle.BLACK) }
    val visible = documents.filter { document ->
        val matchesSearch = search.isBlank() || document.title.contains(search, true) || document.tags.any { it.contains(search, true) } || document.pages.any { PageSearch.matches(it, search) }
        matchesSearch && (folderId == null || document.folderId == folderId) && when (filter) {
            LibraryFilter.ALL -> document.trashedAt == null && document.archivedAt == null
            LibraryFilter.FAVORITES -> document.favorite && document.trashedAt == null && document.archivedAt == null
            LibraryFilter.ARCHIVED -> document.archivedAt != null && document.trashedAt == null
            LibraryFilter.TRASH -> document.trashedAt != null
            LibraryFilter.PDF -> document.pages.any { it.background is PageBackground.Pdf } && document.trashedAt == null && document.archivedAt == null
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val compact = maxWidth < 700.dp
        Row(Modifier.fillMaxSize()) {
            if (!compact) {
        LibraryRail(filter, { filter = it; folderId = null }, folders, folderId, { folderId = it; filter = LibraryFilter.ALL }, onCreateFolder, onDeleteFolder, onSettings)
                VerticalDivider(color = OutlineSoft)
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = if (compact) 16.dp else 32.dp, vertical = 22.dp)) {
            if (compact) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text(tr("Library", "资料库")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text(tr("Favorites", "收藏")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.ARCHIVED, onClick = { filter = LibraryFilter.ARCHIVED }, label = { Text(tr("Archive", "归档")) }) }
                    item { FilterChip(selected = filter == LibraryFilter.TRASH, onClick = { filter = LibraryFilter.TRASH }, label = { Text(tr("Trash", "废纸篓")) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Your notebooks", "你的笔记本"), fontSize = 32.sp, color = Ink)
                    Text(tr("Continue a note or start with a fresh page.", "继续书写，或从空白页开始。"), color = MutedInk)
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("Settings", "设置")) }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onImport, shape = RoundedCornerShape(50)) { Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text(tr("Import", "导入")) }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { newNoteOpen = true }, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("New Note", "新建笔记"))
                }
            }
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(value = search, onValueChange = { search = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Search notes", "搜索笔记")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Text(tr("Recents", "最近使用"), fontSize = 22.sp)
            Spacer(Modifier.height(14.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(visible.take(4)) { NotebookCard(it, Modifier.width(230.dp).height(215.dp), onOpen, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, folders, onMoveToFolder) }
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("All Notes", "全部笔记"), fontSize = 22.sp, modifier = Modifier.weight(1f))
                FilterChip(selected = filter == LibraryFilter.ALL, onClick = { filter = LibraryFilter.ALL }, label = { Text(tr("All", "全部")) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = filter == LibraryFilter.FAVORITES, onClick = { filter = LibraryFilter.FAVORITES }, label = { Text(tr("Favorites", "收藏")) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = filter == LibraryFilter.PDF, onClick = { filter = LibraryFilter.PDF }, label = { Text("PDF") })
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(visible, key = { it.id }) { NotebookCard(it, Modifier.height(250.dp), onOpen, onFavorite, onArchive, onTags, onTrash, onRestore, onDelete, onRename, onDuplicate, folders, onMoveToFolder) }
            }
        }
        }
    }
    if (newNoteOpen) AlertDialog(
        onDismissRequest = { newNoteOpen = false },
        title = { Text(tr("Create notebook", "新建笔记本")) },
        text = {
            Column(Modifier.widthIn(min = 380.dp, max = 560.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(newNoteTitle, { newNoteTitle = it.take(120) }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(tr("Cover template", "封面模板"), color = MutedInk, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CoverStyle.entries.filterNot { it == CoverStyle.CUSTOM_IMAGE }) { style ->
                        Card(
                            Modifier.size(92.dp, 120.dp).clickable { newNoteCover = style },
                            border = if (newNoteCover == style) androidx.compose.foundation.BorderStroke(2.dp, Ink) else CardDefaults.outlinedCardBorder(),
                        ) { NotebookCover(InkDocument(title = newNoteTitle, coverStyle = style, coverArgb = if (style in setOf(CoverStyle.WHITE, CoverStyle.WHITE_GRID)) 0xFFF4F4F4 else 0xFF202020), Modifier.fillMaxSize()) }
                    }
                }
                OutlinedButton(onClick = { newNoteOpen = false; onImportCover(newNoteTitle) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Import image as cover", "导入图片作为封面"))
                }
            }
        },
        confirmButton = { Button(onClick = { newNoteOpen = false; onNewNote(newNoteTitle, newNoteCover) }) { Text(tr("Create", "创建")) } },
        dismissButton = { TextButton(onClick = { newNoteOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun LibraryRail(filter: LibraryFilter, onFilter: (LibraryFilter) -> Unit, folders: List<InkFolder>, folderId: String?, onFolder: (String?) -> Unit, onCreateFolder: (String, String?) -> Unit, onDeleteFolder: (String) -> Unit, onSettings: () -> Unit) {
    var newFolderOpen by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    Column(Modifier.width(198.dp).fillMaxHeight().padding(16.dp)) {
        BrandMark(tr("Library", "资料库"))
        Spacer(Modifier.height(34.dp))
        RailItem(Icons.Outlined.Book, tr("Library", "资料库"), filter == LibraryFilter.ALL) { onFilter(LibraryFilter.ALL) }
        RailItem(Icons.Outlined.History, tr("Recents", "最近使用"), filter == LibraryFilter.ALL) { onFilter(LibraryFilter.ALL) }
        RailItem(Icons.Outlined.StarBorder, tr("Favorites", "收藏"), filter == LibraryFilter.FAVORITES) { onFilter(LibraryFilter.FAVORITES) }
        RailItem(Icons.Outlined.Archive, tr("Archive", "归档"), filter == LibraryFilter.ARCHIVED) { onFilter(LibraryFilter.ARCHIVED) }
        RailItem(Icons.Outlined.Delete, tr("Trash", "废纸篓"), filter == LibraryFilter.TRASH) { onFilter(LibraryFilter.TRASH) }
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = OutlineSoft)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("FOLDERS", "文件夹"), fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f)); IconButton(onClick = { newFolderOpen = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Add, tr("New folder", "新建文件夹")) } }
        Spacer(Modifier.height(10.dp))
        folders.filter { it.parentId == null }.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { RailItem(Icons.Outlined.Folder, folder.name, folderId == folder.id) { onFolder(folder.id) } }; IconButton(onClick = { onDeleteFolder(folder.id) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, tr("Delete folder", "删除文件夹"), modifier = Modifier.size(14.dp)) } }
            folders.filter { it.parentId == folder.id }.forEach { child -> Box(Modifier.padding(start = 16.dp)) { RailItem(Icons.Outlined.FolderOpen, child.name, folderId == child.id) { onFolder(child.id) } } }
        }
        Spacer(Modifier.weight(1f))
        RailItem(Icons.Outlined.Settings, tr("Settings", "设置"), false, onSettings)
    }
    if (newFolderOpen) AlertDialog(onDismissRequest = { newFolderOpen = false }, title = { Text(tr("New folder", "新建文件夹")) }, text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onCreateFolder(folderName, folderId); folderName = ""; newFolderOpen = false }) { Text(tr("Create", "创建")) } }, dismissButton = { TextButton(onClick = { newFolderOpen = false }) { Text(tr("Cancel", "取消")) } })
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selected) Workspace else Color.Transparent).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) Sage else Ink); Spacer(Modifier.width(12.dp)); Text(label, color = if (selected) Sage else Ink)
    }
}

@Composable
private fun BrandMark(subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Sage, shape = RoundedCornerShape(6.dp), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = Color.White) } }
        Spacer(Modifier.width(12.dp))
        Column { Text("Inkloom", color = Sage, fontSize = 25.sp); if (subtitle != null) Text(subtitle, color = MutedInk, fontSize = 11.sp) }
    }
}

@Composable
private fun NotebookCard(document: InkDocument, modifier: Modifier, onOpen: (InkDocument) -> Unit, onFavorite: (String) -> Unit, onArchive: (String) -> Unit, onTags: (String, List<String>) -> Unit, onTrash: (String) -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onRename: (String, String) -> Unit, onDuplicate: (String) -> Unit, folders: List<InkFolder>, onMoveToFolder: (String, String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var tagsOpen by remember { mutableStateOf(false) }
    var title by remember(document.title) { mutableStateOf(document.title) }
    var tags by remember(document.tags) { mutableStateOf(document.tags.joinToString(", ")) }
    Card(modifier.clickable { onOpen(document) }, colors = CardDefaults.cardColors(containerColor = Color.White), border = CardDefaults.outlinedCardBorder()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            NotebookCover(document, Modifier.fillMaxSize())
            if (document.pages.any { it.background is PageBackground.Pdf }) Surface(Modifier.padding(12.dp), shape = RoundedCornerShape(4.dp), color = Color.White) { Text("PDF", Modifier.padding(7.dp, 4.dp), fontSize = 10.sp) }
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, tr("Document actions", "笔记操作")) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (document.trashedAt == null) {
                        DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameOpen = true })
                        DropdownMenuItem(text = { Text(tr("Edit tags", "编辑标签")) }, onClick = { menu = false; tagsOpen = true })
                        DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(document.id) })
                        folders.forEach { folder -> DropdownMenuItem(text = { Text("${tr("Move to", "移动到")} ${folder.name}") }, onClick = { menu = false; onMoveToFolder(document.id, folder.id) }) }
                        if (document.folderId != null) DropdownMenuItem(text = { Text(tr("Remove from folder", "移出文件夹")) }, onClick = { menu = false; onMoveToFolder(document.id, null) })
                        DropdownMenuItem(text = { Text(if (document.favorite) tr("Remove favorite", "取消收藏") else tr("Favorite", "收藏")) }, onClick = { menu = false; onFavorite(document.id) })
                        DropdownMenuItem(text = { Text(if (document.archivedAt == null) tr("Archive", "归档") else tr("Unarchive", "取消归档")) }, onClick = { menu = false; onArchive(document.id) })
                        DropdownMenuItem(text = { Text(tr("Move to Trash", "移到废纸篓")) }, onClick = { menu = false; onTrash(document.id) })
                    } else {
                        DropdownMenuItem(text = { Text(tr("Restore", "恢复")) }, onClick = { menu = false; onRestore(document.id) })
                        DropdownMenuItem(text = { Text(tr("Delete permanently", "永久删除")) }, onClick = { menu = false; onDelete(document.id) })
                    }
                }
            }
        }
        Column(Modifier.padding(14.dp)) {
            Row { Text(document.title, fontSize = 17.sp, maxLines = 2, modifier = Modifier.weight(1f)); if (document.favorite) Icon(Icons.Default.Star, null, tint = Sage, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(6.dp)); Text("${document.pages.size} ${tr("pages · saved locally", "页 · 已保存到本机")}", color = MutedInk, fontSize = 11.sp)
            if (document.tags.isNotEmpty()) Text(document.tags.take(3).joinToString("  ") { "#$it" }, color = Sage, fontSize = 10.sp, maxLines = 1)
        }
    }
    if (renameOpen) AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text(tr("Rename note", "重命名笔记")) }, text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, title); renameOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(tr("Cancel", "取消")) } })
    if (tagsOpen) AlertDialog(onDismissRequest = { tagsOpen = false }, title = { Text(tr("Note tags", "笔记标签")) }, text = { OutlinedTextField(value = tags, onValueChange = { tags = it }, supportingText = { Text(tr("Separate tags with commas", "使用逗号分隔标签")) }) }, confirmButton = { TextButton(onClick = { onTags(document.id, tags.split(',')); tagsOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { tagsOpen = false }) { Text(tr("Cancel", "取消")) } })
}

@Composable
private fun NotebookCover(document: InkDocument, modifier: Modifier = Modifier) {
    val coverBitmap = remember(document.coverImagePath) { document.coverImagePath?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() } }
    DisposableEffect(coverBitmap) { onDispose { coverBitmap?.recycle() } }
    val light = document.coverStyle in setOf(CoverStyle.WHITE, CoverStyle.WHITE_GRID)
    Box(modifier.background(if (light) Color(0xFFF4F4F4) else Color(document.coverArgb))) {
        if (document.coverStyle == CoverStyle.CUSTOM_IMAGE && coverBitmap != null) {
            Image(coverBitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val line = if (light) Color.Black.copy(alpha = .42f) else Color.White.copy(alpha = .7f)
            when (document.coverStyle) {
                CoverStyle.BLACK, CoverStyle.WHITE -> repeat(3) { index -> drawLine(line, Offset(0f, 18f + index * 12f), Offset(size.width * (.9f - index * .08f), 18f + index * 12f), strokeWidth = 2f, cap = StrokeCap.Round) }
                CoverStyle.BLACK_BAND -> { drawRect(Color.White, Offset(0f, size.height * .58f), androidx.compose.ui.geometry.Size(size.width, size.height * .18f)); drawLine(Color.Black, Offset(12f, size.height * .67f), Offset(size.width - 12f, size.height * .67f), strokeWidth = 2f) }
                CoverStyle.WHITE_GRID -> { var x = 0f; while (x < size.width) { drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f); x += 18f }; var y = 0f; while (y < size.height) { drawLine(line, Offset(0f, y), Offset(size.width, y), 1f); y += 18f } }
                CoverStyle.CUSTOM_IMAGE -> Unit
            }
        }
    }
}

@Composable
private fun EditorScreen(
    document: InkDocument?,
    documents: List<InkDocument>,
    selectedPageId: String?,
    brushesOpen: Boolean,
    brushes: List<ImportedBrush>,
    brushGroups: List<String>,
    importError: String?,
    onBack: () -> Unit,
    onToggleBrushes: () -> Unit,
    onImportBrushes: (String?) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceBrushGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    onDismissError: () -> Unit,
    onSelectPage: (String) -> Unit,
    pageTemplates: PageTemplateLibrary,
    onAddPage: (String) -> Unit,
    onDuplicatePage: (String, String) -> Unit,
    onDeletePage: (String, String) -> Unit,
    onMovePage: (String, String, Int) -> Unit,
    onTransferPage: (String, String, String, Boolean) -> Unit,
    onApplyPageTemplate: (String, String, String) -> Unit,
    onImportPageTemplates: () -> Unit,
    onTogglePageTemplateFavorite: (String) -> Unit,
    onSetNotebookDefaultTemplate: (String, String?) -> Unit,
    onSavePageAsTemplate: (String, String, String) -> PageTemplate?,
    onDeletePageTemplate: (String) -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onChangePageOutlineLevel: (String, String, Int) -> Unit,
    onTogglePageOutlineCollapsed: (String, String) -> Unit,
    onRenamePage: (String, String, String) -> Unit,
    onSetPageMetadata: (String, String, String?, List<String>) -> Unit,
    onClearPageInk: (String, String) -> Unit,
    onSetPageCrop: (String, String, PageCrop) -> Unit,
    onRotatePage: (String, String) -> Unit,
    onAddLayer: (String, String, String) -> Unit,
    onAddLayerFolder: (String, String) -> Unit,
    onRenameLayer: (String, String, String, String) -> Unit,
    onToggleLayerVisibility: (String, String, String) -> Unit,
    onToggleLayerLock: (String, String, String) -> Unit,
    onSetLayerOpacity: (String, String, String, Float) -> Unit,
    onSetLayerBlendMode: (String, String, String, LayerBlendMode) -> Unit,
    onMoveLayerToFolder: (String, String, String, String?) -> Unit,
    onToggleLayerFolderCollapsed: (String, String, String) -> Unit,
    onMoveLayer: (String, String, String, Int) -> Unit,
    onDuplicateLayer: (String, String, String) -> String?,
    onMergeLayerDown: (String, String, String) -> String?,
    onDeleteLayer: (String, String, String) -> Unit,
    onStroke: (String, String, InkStroke) -> Unit,
    onRemoveStroke: (String, String, String) -> Unit,
    onStrokesReplaced: (String, String, List<String>, List<InkStroke>, List<Int>) -> Unit,
    onElement: (String, String, PageElement) -> Unit,
    onRemoveElement: (String, String, String) -> Unit,
    onDuplicateElement: (String, String, String) -> Unit,
    onInsertImage: () -> Unit,
    onExport: () -> Unit,
    onExportPage: () -> Unit,
    onExportBundle: () -> Unit,
    onExportWorkspace: (CustomWorkspaceProfile) -> Unit,
    onImportWorkspace: () -> Unit,
    assets: DocumentAssetService,
    settings: AppSettings,
    pdfIndexing: Boolean,
    onIndexPdfText: (String) -> Unit,
    onSettings: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    if (document == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val context = LocalContext.current
    val workspaceOrientation = WorkspaceOrientation.from(LocalConfiguration.current)
    val audioRecorder = remember { AudioNoteRecorder(context) }
    val selectedPage = document.pages.firstOrNull { it.id == selectedPageId } ?: document.pages.first()
    var tool by remember { mutableStateOf(EditorTool.PEN) }
    var canvasView by remember { mutableStateOf<InkCanvasView?>(null) }
    var selectedBrushId by remember { mutableStateOf<String?>(null) }
    var activeBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var brushSize by remember { mutableFloatStateOf(settings.penSize) }
    var brushOpacity by remember { mutableFloatStateOf(settings.penOpacity) }
    var brushColor by remember { mutableIntStateOf(AndroidColor.rgb(17, 17, 17)) }
    var previousDrawingTool by remember { mutableStateOf(EditorTool.PEN) }
    var previousDrawingBrushId by remember { mutableStateOf<String?>(null) }
    var previousDrawingSize by remember { mutableFloatStateOf(settings.penSize) }
    var previousDrawingOpacity by remember { mutableFloatStateOf(settings.penOpacity) }
    var previousDrawingColor by remember { mutableIntStateOf(AndroidColor.rgb(17, 17, 17)) }
    var quickSlotWorkspaceBeforeHold by remember { mutableStateOf<app.inkloom.settings.ToolboxWorkspaceSnapshot?>(null) }
    var handToolEnabled by remember { mutableStateOf(false) }
    var handToolHeld by remember { mutableStateOf(false) }
    var rotateToolEnabled by remember { mutableStateOf(false) }
    var rotateToolHeld by remember { mutableStateOf(false) }
    fun exitCanvasNavigation() { handToolEnabled = false; handToolHeld = false; rotateToolEnabled = false; rotateToolHeld = false }
    var exportMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var workspaceMenu by remember { mutableStateOf(false) }
    var saveWorkspaceOpen by remember { mutableStateOf(false) }
    var manageWorkspacesOpen by remember { mutableStateOf(false) }
    var newWorkspaceName by remember { mutableStateOf("") }
    var zoom by remember(document.id) { mutableFloatStateOf(1f) }
    var panX by remember(document.id) { mutableFloatStateOf(0f) }
    var panY by remember(document.id) { mutableFloatStateOf(0f) }
    var viewRotation by remember(document.id) { mutableFloatStateOf(0f) }
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var viewportHeightPx by remember { mutableFloatStateOf(0f) }
    fun currentViewportGeometry(): ViewportGeometry? {
        val view = canvasView ?: return null
        if (viewportWidthPx <= 0f || viewportHeightPx <= 0f || view.width <= 0 || view.height <= 0) return null
        return ViewportGeometry(viewportWidthPx, viewportHeightPx, view.width.toFloat(), view.height.toFloat())
    }
    fun applyViewport(next: ViewportState) { zoom = next.scale; panX = next.panX; panY = next.panY; viewRotation = next.rotationDegrees }
    val pageViewportStore = remember(document.id) { PageViewportStore() }
    var viewportPageId by remember(document.id) { mutableStateOf(selectedPage.id) }
    fun selectPageWithViewport(pageId: String) {
        if (pageId == viewportPageId) { onSelectPage(pageId); return }
        pageViewportStore.save(viewportPageId, ViewportState(zoom, panX, panY, viewRotation))
        applyViewport(pageViewportStore.restore(pageId))
        viewportPageId = pageId
        onSelectPage(pageId)
    }
    LaunchedEffect(selectedPage.id) {
        if (selectedPage.id != viewportPageId) {
            pageViewportStore.save(viewportPageId, ViewportState(zoom, panX, panY, viewRotation))
            applyViewport(pageViewportStore.restore(selectedPage.id))
            viewportPageId = selectedPage.id
        }
    }
    LaunchedEffect(document.pages.map(InkPage::id)) { pageViewportStore.retainPages(document.pages.map(InkPage::id).toSet()) }
    fun fitPage() { applyViewport(ViewportState(1f, 0f, 0f, 0f)); exitCanvasNavigation() }
    fun actualSize() {
        val view = canvasView ?: return
        if (view.width <= 0) return
        applyViewport(ViewportTransform.actualSize(ViewportState(zoom, panX, panY, viewRotation), PageCropTransform.visibleWidth(selectedPage), view.width.toFloat(), currentViewportGeometry()))
    }
    fun fitWidth() {
        val view = canvasView ?: return
        if (view.width <= 0 || viewportWidthPx <= 0f) return
        applyViewport(ViewportTransform.fitWidth(ViewportState(zoom, panX, panY, viewRotation), viewportWidthPx, view.width.toFloat(), currentViewportGeometry()))
    }
    fun resetViewRotation() { applyViewport(ViewportTransform.resetRotation(ViewportState(zoom, panX, panY, viewRotation), currentViewportGeometry())); exitCanvasNavigation() }
    fun rotateViewByShortcut(degrees: Float) {
        applyViewport(ViewportTransform.rotateBy(ViewportState(zoom, panX, panY, viewRotation), degrees, currentViewportGeometry()))
        exitCanvasNavigation()
    }
    var toolbarX by remember { mutableFloatStateOf(settings.toolbarOffsetXDp) }
    var toolbarY by remember { mutableFloatStateOf(settings.toolbarOffsetYDp) }
    var pagePanelBounds by remember {
        mutableStateOf(WorkspacePanelBounds(settings.pagePanelXDp, settings.pagePanelYDp, settings.pagePanelWidthDp, settings.pagePanelHeightDp))
    }
    var brushPanelBounds by remember {
        mutableStateOf(WorkspacePanelBounds(settings.brushPanelXDp, settings.brushPanelYDp, settings.brushPanelWidthDp, settings.brushPanelHeightDp))
    }
    var layerPanelBounds by remember {
        mutableStateOf(WorkspacePanelBounds(settings.layerPanelXDp, settings.layerPanelYDp, settings.layerPanelWidthDp, settings.layerPanelHeightDp))
    }
    var activeFloatingPanel by remember { mutableStateOf("brushes") }
    var draggingPanel by remember { mutableStateOf<WorkspacePanelKind?>(null) }
    var panelDragOrigin by remember { mutableStateOf<WorkspacePanelBounds?>(null) }
    var panelDockPreview by remember { mutableStateOf<WorkspacePanelDock?>(null) }
    var panelGroupPreviewTarget by remember { mutableStateOf<WorkspacePanelKind?>(null) }
    var panelGroupDragOrigins by remember { mutableStateOf<Map<WorkspacePanelKind, WorkspacePanelBounds>>(emptyMap()) }
    var dockedPanelOriginDock by remember { mutableStateOf<WorkspacePanelDock?>(null) }
    var dockedPanelPreviewBounds by remember { mutableStateOf<WorkspacePanelBounds?>(null) }
    var dockedPanelTargetDock by remember { mutableStateOf<WorkspacePanelDock?>(null) }
    var panelDragDeltaXDp by remember { mutableFloatStateOf(0f) }
    var panelDragDeltaYDp by remember { mutableFloatStateOf(0f) }
    val editorDensity = LocalDensity.current
    LaunchedEffect(settings.toolbarOffsetXDp, settings.toolbarOffsetYDp, settings.toolbarDock) {
        toolbarX = settings.toolbarOffsetXDp
        toolbarY = settings.toolbarOffsetYDp
    }
    LaunchedEffect(settings.pagePanelXDp, settings.pagePanelYDp, settings.pagePanelWidthDp, settings.pagePanelHeightDp) {
        pagePanelBounds = WorkspacePanelBounds(settings.pagePanelXDp, settings.pagePanelYDp, settings.pagePanelWidthDp, settings.pagePanelHeightDp)
    }
    LaunchedEffect(settings.brushPanelXDp, settings.brushPanelYDp, settings.brushPanelWidthDp, settings.brushPanelHeightDp) {
        brushPanelBounds = WorkspacePanelBounds(settings.brushPanelXDp, settings.brushPanelYDp, settings.brushPanelWidthDp, settings.brushPanelHeightDp)
    }
    LaunchedEffect(settings.layerPanelXDp, settings.layerPanelYDp, settings.layerPanelWidthDp, settings.layerPanelHeightDp) {
        layerPanelBounds = WorkspacePanelBounds(settings.layerPanelXDp, settings.layerPanelYDp, settings.layerPanelWidthDp, settings.layerPanelHeightDp)
    }
    fun persistPagePanel(bounds: WorkspacePanelBounds) = onSettingsChange(settings.copy(
        pagePanelXDp = bounds.xDp,
        pagePanelYDp = bounds.yDp,
        pagePanelWidthDp = bounds.widthDp,
        pagePanelHeightDp = bounds.heightDp,
    ))
    fun persistBrushPanel(bounds: WorkspacePanelBounds) = onSettingsChange(settings.copy(
        brushPanelXDp = bounds.xDp,
        brushPanelYDp = bounds.yDp,
        brushPanelWidthDp = bounds.widthDp,
        brushPanelHeightDp = bounds.heightDp,
    ))
    fun persistLayerPanel(bounds: WorkspacePanelBounds) = onSettingsChange(settings.copy(
        layerPanelXDp = bounds.xDp,
        layerPanelYDp = bounds.yDp,
        layerPanelWidthDp = bounds.widthDp,
        layerPanelHeightDp = bounds.heightDp,
    ))
    val waitingForInputText = tr("Waiting for input", "等待输入")
    val unknownInputDeviceText = tr("Unknown device", "未知设备")
    val unmappedShortcutText = tr("unmapped", "未映射")
    var inputDiagnostic by remember { mutableStateOf(waitingForInputText) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchFocus by remember(document.id) { mutableStateOf<PageSearchHit?>(null) }
    var pdfLinkFocus by remember(document.id) { mutableStateOf<Pair<String, PdfTextBounds>?>(null) }
    var outlineOpen by remember { mutableStateOf(false) }
    var documentQuery by remember { mutableStateOf("") }
    var textInsertOpen by remember { mutableStateOf(false) }
    var stickyInsertOpen by remember { mutableStateOf(false) }
    var linkInsertOpen by remember { mutableStateOf(false) }
    var shapeInsertOpen by remember { mutableStateOf(false) }
    var insertText by remember { mutableStateOf("") }
    var insertLinkTitle by remember { mutableStateOf("") }
    var insertLinkUrl by remember { mutableStateOf("") }
    var insertShape by remember { mutableStateOf(ShapeKind.RECTANGLE) }
    var selectedElementId by remember(selectedPage.id) { mutableStateOf<String?>(null) }
    var audioOpen by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    val defaultAudioTitle = tr("Audio note", "音频笔记")
    val microphonePermissionError = tr("Microphone permission is required to record an audio note.", "录制音频笔记需要麦克风权限。")
    var audioTitle by remember { mutableStateOf(defaultAudioTitle) }
    var audioError by remember { mutableStateOf<String?>(null) }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) audioOpen = true else audioError = microphonePermissionError }
    DisposableEffect(audioRecorder) { onDispose { audioRecorder.cancel() } }
    var layersOpen by remember { mutableStateOf(settings.showLayerPanel) }
    LaunchedEffect(settings.showLayerPanel) { layersOpen = settings.showLayerPanel }
    var activeLayerId by remember(selectedPage.id) { mutableStateOf(LayerIds.INK) }
    var panelsHiddenByShortcut by remember { mutableStateOf(false) }
    var pageRailBeforeShortcut by remember { mutableStateOf(settings.showPageRail) }
    var toolbarBeforeShortcut by remember { mutableStateOf(settings.showToolbar) }
    var brushesBeforeShortcut by remember { mutableStateOf(false) }
    var layersBeforeShortcut by remember { mutableStateOf(false) }
    var rulerBeforeShortcut by remember { mutableStateOf(false) }
    var gridBeforeShortcut by remember { mutableStateOf(false) }
    var guidesBeforeShortcut by remember { mutableStateOf(false) }
    LaunchedEffect(selectedPage.layers) {
        val active = selectedPage.layers.firstOrNull { it.id == activeLayerId }
        if (active == null || LayerHierarchy.effectiveLocked(selectedPage.layers, active) || !LayerHierarchy.effectiveVisible(selectedPage.layers, active) || active.role in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) activeLayerId = selectedPage.layers.firstOrNull { LayerHierarchy.effectiveVisible(selectedPage.layers, it) && !LayerHierarchy.effectiveLocked(selectedPage.layers, it) && it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) }?.id ?: LayerIds.INK
    }
    fun applyWorkspaceSettings(restored: AppSettings) {
        if (brushesOpen != restored.showBrushPanel) onToggleBrushes()
        layersOpen = restored.showLayerPanel
        onSettingsChange(restored)
    }
    fun applyWorkspaceSnapshot(snapshot: app.inkloom.settings.ToolboxWorkspaceSnapshot) =
        applyWorkspaceSettings(settings.applyToolboxWorkspace(snapshot))
    fun applyBuiltInWorkspace(workspace: BuiltInWorkspace) {
        workspaceMenu = false
        applyWorkspaceSnapshot(BuiltInWorkspaces.snapshot(workspace))
    }
    fun applyCustomWorkspace(profile: CustomWorkspaceProfile) {
        workspaceMenu = false
        manageWorkspacesOpen = false
        applyWorkspaceSettings(CustomWorkspaces.apply(settings, profile, workspaceOrientation))
    }
    fun replaceCustomWorkspaces(values: List<CustomWorkspaceProfile>) {
        onSettingsChange(settings.copy(customWorkspaces = CustomWorkspaces.normalize(values)))
    }
    val allChangesSavedMessage = tr("All changes are saved.", "所有更改均已保存。")
    val inkOnlyClipboardMessage = tr("Clipboard actions currently apply to ink selections.", "剪贴板操作目前仅适用于墨迹选区。")
    val selectBeforeCutMessage = tr("Select ink before cutting.", "请先选择墨迹再剪切。")
    val selectBeforeCopyMessage = tr("Select ink before copying.", "请先选择墨迹再复制。")
    val noInkClipboardMessage = tr("The clipboard does not contain Inkloom ink.", "剪贴板中没有 Inkloom 墨迹。")
    val defaultCustomWorkspaceName = tr("My workspace", "我的工作区")
    val workspaceCopySuffix = tr("copy", "副本")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.height(50.dp).fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(); VerticalDivider(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)); Text(document.title, Modifier.weight(1f))
            IconButton(onClick = { searchOpen = true }) { Icon(Icons.Outlined.Search, tr("Search this note", "搜索此笔记")) }
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, tr("Settings", "设置")) }
            Surface(shape = CircleShape, color = Workspace) { Text("J", Modifier.padding(12.dp, 8.dp)) }
        }
        HorizontalDivider(color = OutlineSoft)
        Row(Modifier.height(44.dp).fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, tr("Back", "返回")) }
            Text("${document.title} · ${selectedPage.title}", Modifier.weight(1f))
            IconButton(onClick = onToggleBrushes) { Icon(Icons.Outlined.Brush, tr("Brushes", "笔刷")) }
            IconButton(onClick = {
                val open = !layersOpen
                layersOpen = open
                onSettingsChange(settings.copy(showLayerPanel = open))
            }) { Icon(Icons.Outlined.Layers, tr("Layers", "图层")) }
            Box {
                IconButton(onClick = { workspaceMenu = true }) { Icon(Icons.Outlined.DashboardCustomize, tr("Workspaces", "工作区")) }
                DropdownMenu(expanded = workspaceMenu, onDismissRequest = { workspaceMenu = false }) {
                    DropdownMenuItem(
                        text = { Column { Text(tr("Note workspace", "笔记工作区")); Text(tr("Pages and writing tools", "页面与书写工具"), fontSize = 10.sp, color = MutedInk) } },
                        leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.NOTE) },
                    )
                    DropdownMenuItem(
                        text = { Column { Text(tr("Drawing workspace", "绘画工作区")); Text(tr("Brushes, layers, and vertical tools", "笔刷、图层与竖向工具"), fontSize = 10.sp, color = MutedInk) } },
                        leadingIcon = { Icon(Icons.Outlined.Palette, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.DRAWING) },
                    )
                    DropdownMenuItem(
                        text = { Column { Text(tr("Reading workspace", "阅读工作区")); Text(tr("Pages with drawing controls hidden", "保留页面并隐藏绘画控制"), fontSize = 10.sp, color = MutedInk) } },
                        leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.READING) },
                    )
                    DropdownMenuItem(
                        text = { Column { Text(tr("Minimal workspace", "极简工作区")); Text(tr("Canvas and compact toolbar only", "仅保留画布与紧凑工具条"), fontSize = 10.sp, color = MutedInk) } },
                        leadingIcon = { Icon(Icons.Outlined.CropFree, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.MINIMAL) },
                    )
                    HorizontalDivider()
                    settings.customWorkspaces.take(5).forEach { profile ->
                        DropdownMenuItem(
                            text = { Column { Text(profile.name); Text(tr("Custom workspace", "自定义工作区"), fontSize = 10.sp, color = MutedInk) } },
                            leadingIcon = { Icon(Icons.Outlined.SpaceDashboard, null) },
                            onClick = { applyCustomWorkspace(profile) },
                        )
                    }
                    if (settings.customWorkspaces.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(tr("Save current workspace…", "保存当前工作区…")) },
                        leadingIcon = { Icon(Icons.Outlined.AddBox, null) },
                        onClick = {
                            workspaceMenu = false
                            newWorkspaceName = CustomWorkspaces.uniqueName(defaultCustomWorkspaceName, settings.customWorkspaces)
                            saveWorkspaceOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Manage custom workspaces…", "管理自定义工作区…")) },
                        leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                        onClick = { workspaceMenu = false; manageWorkspacesOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Import workspace…", "导入工作区…")) },
                        leadingIcon = { Icon(Icons.Outlined.FileOpen, null) },
                        onClick = { workspaceMenu = false; onImportWorkspace() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(tr("Reset current workspace", "重置当前工作区")) },
                        leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                        onClick = { applyBuiltInWorkspace(BuiltInWorkspace.RESET) },
                    )
                }
            }
            IconButton(onClick = { outlineOpen = true }) { Icon(Icons.Outlined.FormatListBulleted, tr("Page outline", "页面大纲")) }
            IconButton(onClick = { onSettingsChange(settings.copy(showPageRail = !settings.showPageRail)) }) { Icon(if (settings.showPageRail) Icons.Outlined.ViewSidebar else Icons.Outlined.TableRows, tr("Toggle pages", "显示或隐藏页面栏")) }
            IconButton(onClick = { onSettingsChange(settings.copy(showToolbar = !settings.showToolbar)) }) { Icon(if (settings.showToolbar) Icons.Outlined.VisibilityOff else Icons.Outlined.Build, tr("Toggle toolbar", "显示或隐藏工具栏")) }
            IconButton(onClick = { val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), .9f, 0f, 0f, geometry = currentViewportGeometry()); zoom = next.scale; panX = next.panX; panY = next.panY }) { Icon(Icons.Outlined.ZoomOut, tr("Zoom out", "缩小")) }
            IconButton(onClick = { val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), 1.1f, 0f, 0f, geometry = currentViewportGeometry()); zoom = next.scale; panX = next.panX; panY = next.panY }) { Icon(Icons.Outlined.ZoomIn, tr("Zoom in", "放大")) }
            IconButton(onClick = ::fitPage) { Icon(Icons.Outlined.CenterFocusStrong, tr("Fit page", "适合页面")) }
            Box {
                TextButton(onClick = { viewMenu = true }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("${(zoom * 100f).toInt()}%", fontSize = 11.sp) }
                DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                    DropdownMenuItem(text = { Text(tr("Fit page · Ctrl+0", "适合页面 · Ctrl+0")) }, onClick = { viewMenu = false; fitPage() })
                    DropdownMenuItem(text = { Text(tr("Actual size · Ctrl+Alt+0", "实际大小 · Ctrl+Alt+0")) }, onClick = { viewMenu = false; actualSize() })
                    DropdownMenuItem(text = { Text(tr("Fit width", "适合宽度")) }, onClick = { viewMenu = false; fitWidth() })
                    DropdownMenuItem(text = { Text(tr("Reset rotation", "重置旋转")) }, onClick = { viewMenu = false; resetViewRotation() })
                }
            }
            IconButton(onClick = onExport) { Icon(Icons.Outlined.Share, tr("Export PDF", "导出 PDF")) }
            Box {
                IconButton(onClick = { exportMenu = true }) { Icon(Icons.Outlined.MoreHoriz, tr("More exports", "更多导出选项")) }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    document.pages.forEachIndexed { index, page -> DropdownMenuItem(text = { Text(tr("Go to page ${index + 1}", "前往第 ${index + 1} 页")) }, onClick = { exportMenu = false; selectPageWithViewport(page.id) }) }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(tr("Export current page PNG", "将当前页导出为 PNG")) }, onClick = { exportMenu = false; onExportPage() })
                    DropdownMenuItem(text = { Text(tr("Export editable Inkloom package", "导出可编辑 Inkloom 文件")) }, onClick = { exportMenu = false; onExportBundle() })
                }
            }
        }
        HorizontalDivider(color = OutlineSoft)
        BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
        val editorWidth = maxWidth
        val workspaceWidthDp = maxWidth.value
        val workspaceHeightDp = maxHeight.value
        val bottomNavigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val usableWorkspaceHeightDp = (workspaceHeightDp - bottomNavigationPadding.value).coerceAtLeast(1f)
        val visiblePagePanelBounds = WorkspacePanelPlacement.clamp(pagePanelBounds, workspaceWidthDp, usableWorkspaceHeightDp, minimumWidthDp = 140f)
        val visibleBrushPanelBounds = WorkspacePanelPlacement.clamp(brushPanelBounds, workspaceWidthDp, usableWorkspaceHeightDp, minimumWidthDp = 280f)
        val visibleLayerPanelBounds = WorkspacePanelPlacement.clamp(layerPanelBounds, workspaceWidthDp, usableWorkspaceHeightDp, minimumWidthDp = 260f)
        LaunchedEffect(maxWidth, maxHeight) {
            viewportWidthPx = with(editorDensity) { maxWidth.toPx() }
            viewportHeightPx = with(editorDensity) { maxHeight.toPx() }
        }
        LaunchedEffect(visiblePagePanelBounds) { if (pagePanelBounds != visiblePagePanelBounds) pagePanelBounds = visiblePagePanelBounds }
        LaunchedEffect(visibleBrushPanelBounds) { if (brushPanelBounds != visibleBrushPanelBounds) brushPanelBounds = visibleBrushPanelBounds }
        LaunchedEffect(visibleLayerPanelBounds) { if (layerPanelBounds != visibleLayerPanelBounds) layerPanelBounds = visibleLayerPanelBounds }
        val orderedPanelTabs = WorkspacePanelTabs.normalizeOrder(settings.panelTabOrder)
        fun visibleTabsAt(dock: WorkspacePanelDock, includePages: Boolean = true) = orderedPanelTabs.filter { panel ->
            when (panel) {
                WorkspacePanelKind.PAGES -> includePages && settings.showPageRail && settings.pagePanelDock == dock
                WorkspacePanelKind.BRUSHES -> brushesOpen && settings.brushPanelDock == dock
                WorkspacePanelKind.LAYERS -> layersOpen && settings.layerPanelDock == dock
            }
        }
        val leftPanelTabs = visibleTabsAt(WorkspacePanelDock.LEFT, editorWidth >= 700.dp)
        val rightPanelTabs = visibleTabsAt(WorkspacePanelDock.RIGHT, editorWidth >= 700.dp)
        val bottomPanelTabs = visibleTabsAt(WorkspacePanelDock.BOTTOM)
        val freePanelTabs = visibleTabsAt(WorkspacePanelDock.FREE)
        fun resolvedActive(dock: WorkspacePanelDock, tabs: List<WorkspacePanelKind>): WorkspacePanelKind? =
            WorkspacePanelTabs.active(settings, dock).takeIf(tabs::contains) ?: tabs.firstOrNull()
        val leftActivePanel = resolvedActive(WorkspacePanelDock.LEFT, leftPanelTabs)
        val rightActivePanel = resolvedActive(WorkspacePanelDock.RIGHT, rightPanelTabs)
        val bottomActivePanel = resolvedActive(WorkspacePanelDock.BOTTOM, bottomPanelTabs)
        val freeActivePanel = resolvedActive(WorkspacePanelDock.FREE, freePanelTabs)
        val leftPanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.LEFT) && leftPanelTabs.size > 1
        val rightPanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.RIGHT) && rightPanelTabs.size > 1
        val bottomPanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.BOTTOM) && bottomPanelTabs.size > 1
        val freePanelsStacked = WorkspacePanelTabs.isStacked(settings, WorkspacePanelDock.FREE) && freePanelTabs.size > 1
        fun showPanel(panel: WorkspacePanelKind, dock: WorkspacePanelDock): Boolean = when (dock) {
            WorkspacePanelDock.LEFT -> !leftPanelsStacked || leftActivePanel == panel
            WorkspacePanelDock.RIGHT -> !rightPanelsStacked || rightActivePanel == panel
            WorkspacePanelDock.BOTTOM -> !bottomPanelsStacked || bottomActivePanel == panel
            WorkspacePanelDock.FREE -> !freePanelsStacked || freeActivePanel == panel
        }
        fun setDockStacked(dock: WorkspacePanelDock, stacked: Boolean, tabs: List<WorkspacePanelKind>) {
            var next = WorkspacePanelTabs.withStacked(settings, dock, stacked)
            if (stacked && WorkspacePanelTabs.active(next, dock) !in tabs) tabs.firstOrNull()?.let { next = WorkspacePanelTabs.withActive(next, dock, it) }
            onSettingsChange(next)
        }
        fun setActivePanel(dock: WorkspacePanelDock, panel: WorkspacePanelKind) =
            onSettingsChange(WorkspacePanelTabs.withActive(settings, dock, panel))
        fun movePanelTab(panel: WorkspacePanelKind, delta: Int) =
            onSettingsChange(settings.copy(panelTabOrder = WorkspacePanelTabs.move(settings.panelTabOrder, panel, delta)))
        fun minimumPanelWidth(panel: WorkspacePanelKind): Float = when (panel) {
            WorkspacePanelKind.PAGES -> 140f
            WorkspacePanelKind.BRUSHES -> 280f
            WorkspacePanelKind.LAYERS -> 260f
        }
        fun panelName(panel: WorkspacePanelKind): String = when (panel) {
            WorkspacePanelKind.PAGES -> "pages"
            WorkspacePanelKind.BRUSHES -> "brushes"
            WorkspacePanelKind.LAYERS -> "layers"
        }
        fun setPanelBounds(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds) {
            when (panel) {
                WorkspacePanelKind.PAGES -> pagePanelBounds = bounds
                WorkspacePanelKind.BRUSHES -> brushPanelBounds = bounds
                WorkspacePanelKind.LAYERS -> layerPanelBounds = bounds
            }
        }
        fun panelBounds(panel: WorkspacePanelKind): WorkspacePanelBounds = when (panel) {
            WorkspacePanelKind.PAGES -> pagePanelBounds
            WorkspacePanelKind.BRUSHES -> brushPanelBounds
            WorkspacePanelKind.LAYERS -> layerPanelBounds
        }
        fun settingsWithPanelBounds(base: AppSettings, panel: WorkspacePanelKind, bounds: WorkspacePanelBounds): AppSettings = when (panel) {
            WorkspacePanelKind.PAGES -> base.copy(pagePanelXDp = bounds.xDp, pagePanelYDp = bounds.yDp, pagePanelWidthDp = bounds.widthDp, pagePanelHeightDp = bounds.heightDp)
            WorkspacePanelKind.BRUSHES -> base.copy(brushPanelXDp = bounds.xDp, brushPanelYDp = bounds.yDp, brushPanelWidthDp = bounds.widthDp, brushPanelHeightDp = bounds.heightDp)
            WorkspacePanelKind.LAYERS -> base.copy(layerPanelXDp = bounds.xDp, layerPanelYDp = bounds.yDp, layerPanelWidthDp = bounds.widthDp, layerPanelHeightDp = bounds.heightDp)
        }
        fun settingsWithPanelDock(base: AppSettings, panel: WorkspacePanelKind, dock: WorkspacePanelDock): AppSettings = when (panel) {
            WorkspacePanelKind.PAGES -> base.copy(pagePanelDock = dock)
            WorkspacePanelKind.BRUSHES -> base.copy(brushPanelDock = dock)
            WorkspacePanelKind.LAYERS -> base.copy(layerPanelDock = dock)
        }
        fun changePanelDock(panel: WorkspacePanelKind, dock: WorkspacePanelDock) {
            var next = settingsWithPanelDock(settings, panel, dock)
            if (dock == WorkspacePanelDock.FREE && panel !in freePanelTabs && settings.freeDockStacked && freePanelTabs.isNotEmpty()) {
                val members = (freePanelTabs + panel).distinct()
                val groupMinimum = members.maxOf(::minimumPanelWidth)
                val anchor = panelBounds(freeActivePanel ?: freePanelTabs.first())
                val shared = WorkspacePanelPlacement.clamp(anchor.copy(widthDp = anchor.widthDp.coerceAtLeast(groupMinimum)), workspaceWidthDp, usableWorkspaceHeightDp, groupMinimum)
                members.forEach { member ->
                    setPanelBounds(member, shared)
                    next = settingsWithPanelBounds(next, member, shared)
                }
                next = WorkspacePanelTabs.withActive(next, WorkspacePanelDock.FREE, panel)
                activeFloatingPanel = panelName(panel)
            } else if (dock != WorkspacePanelDock.FREE) {
                if (WorkspacePanelTabs.isStacked(next, dock)) next = WorkspacePanelTabs.withActive(next, dock, panel)
                if (settings.freeDockActivePanel == panel) freePanelTabs.firstOrNull { it != panel }?.let {
                    next = WorkspacePanelTabs.withActive(next, WorkspacePanelDock.FREE, it)
                }
            }
            onSettingsChange(next)
        }
        fun persistFloatingPanels(panels: List<WorkspacePanelKind>) {
            var next = settings
            panels.distinct().forEach { member -> next = settingsWithPanelBounds(next, member, panelBounds(member)) }
            onSettingsChange(next)
        }
        fun beginPanelDrag(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds) {
            draggingPanel = panel
            panelDragOrigin = bounds
            panelGroupDragOrigins = if (freePanelsStacked && panel in freePanelTabs) freePanelTabs.associateWith(::panelBounds) else mapOf(panel to bounds)
            panelDockPreview = null
            panelGroupPreviewTarget = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun moveFloatingPanel(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, deltaXDp: Float, deltaYDp: Float): WorkspacePanelBounds {
            val next = WorkspacePanelPlacement.move(bounds, deltaXDp, deltaYDp, workspaceWidthDp, usableWorkspaceHeightDp, minimumPanelWidth(panel))
            if (freePanelsStacked && panel in freePanelTabs) {
                val actualX = next.xDp - bounds.xDp
                val actualY = next.yDp - bounds.yDp
                freePanelTabs.forEach { member ->
                    val memberBounds = panelBounds(member)
                    setPanelBounds(member, WorkspacePanelPlacement.move(memberBounds, actualX, actualY, workspaceWidthDp, usableWorkspaceHeightDp, minimumPanelWidth(member)))
                }
            } else {
                setPanelBounds(panel, next)
            }
            return next
        }
        fun resizeFloatingPanel(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, deltaWidthDp: Float, deltaHeightDp: Float): WorkspacePanelBounds {
            val groupMinimum = if (freePanelsStacked && panel in freePanelTabs) freePanelTabs.maxOf(::minimumPanelWidth) else minimumPanelWidth(panel)
            val next = WorkspacePanelPlacement.resize(bounds, deltaWidthDp, deltaHeightDp, workspaceWidthDp, usableWorkspaceHeightDp, groupMinimum)
            if (freePanelsStacked && panel in freePanelTabs) freePanelTabs.forEach { setPanelBounds(it, next) } else setPanelBounds(panel, next)
            return next
        }
        fun updatePanelDockPreview(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, deltaXDp: Float, deltaYDp: Float) {
            panelDragDeltaXDp += deltaXDp
            panelDragDeltaYDp += deltaYDp
            panelDockPreview = WorkspacePanelDocking.target(bounds, workspaceWidthDp, usableWorkspaceHeightDp, panelDragDeltaXDp, panelDragDeltaYDp)
            panelGroupPreviewTarget = if (panelDockPreview == null && !freePanelsStacked) {
                WorkspacePanelGrouping.target(panel, bounds, freePanelTabs.associateWith(::panelBounds))
            } else null
        }
        fun finishPanelDrag(panel: WorkspacePanelKind, bounds: WorkspacePanelBounds, persist: (WorkspacePanelBounds) -> Unit) {
            val target = panelDockPreview
            val groupTarget = panelGroupPreviewTarget
            val movingGroup = freePanelsStacked && panel in freePanelTabs
            draggingPanel = null
            panelDragOrigin = null
            panelGroupDragOrigins = emptyMap()
            panelDockPreview = null
            panelGroupPreviewTarget = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
            if (target != null) {
                val members = if (movingGroup) freePanelTabs else listOf(panel)
                var next = settings
                members.forEach { member -> next = settingsWithPanelDock(next, member, target) }
                if (movingGroup) next = WorkspacePanelTabs.withStacked(next.copy(freeDockStacked = false), target, true)
                if (WorkspacePanelTabs.isStacked(next, target)) next = WorkspacePanelTabs.withActive(next, target, panel)
                onSettingsChange(next)
                return
            }
            if (groupTarget != null) {
                val members = (freePanelTabs + panel).distinct()
                val groupMinimum = members.maxOf(::minimumPanelWidth)
                val anchor = panelBounds(groupTarget)
                val shared = WorkspacePanelPlacement.clamp(
                    anchor.copy(widthDp = anchor.widthDp.coerceAtLeast(groupMinimum)),
                    workspaceWidthDp,
                    usableWorkspaceHeightDp,
                    groupMinimum,
                )
                members.forEach { setPanelBounds(it, shared) }
                var next = settings.copy(freeDockStacked = true, freeDockActivePanel = panel)
                members.forEach { member -> next = settingsWithPanelBounds(next, member, shared) }
                activeFloatingPanel = panelName(panel)
                onSettingsChange(next)
                return
            }
            if (movingGroup) persistFloatingPanels(freePanelTabs) else persist(bounds)
        }
        fun cancelPanelDrag(panel: WorkspacePanelKind, restore: (WorkspacePanelBounds) -> Unit) {
            if (draggingPanel == panel) {
                if (panelGroupDragOrigins.isNotEmpty()) panelGroupDragOrigins.forEach(::setPanelBounds)
                else panelDragOrigin?.let(restore)
            }
            draggingPanel = null
            panelDragOrigin = null
            panelGroupDragOrigins = emptyMap()
            panelDockPreview = null
            panelGroupPreviewTarget = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun selectFloatingPanel(panel: WorkspacePanelKind) {
            activeFloatingPanel = panelName(panel)
            onSettingsChange(WorkspacePanelTabs.withActive(settings, WorkspacePanelDock.FREE, panel))
        }
        fun separateFloatingPanels() {
            var next = settings.copy(freeDockStacked = false)
            freePanelTabs.forEachIndexed { index, panel ->
                val base = panelBounds(panel)
                val separated = WorkspacePanelPlacement.move(base, index * 24f, index * 24f, workspaceWidthDp, usableWorkspaceHeightDp, minimumPanelWidth(panel))
                setPanelBounds(panel, separated)
                next = settingsWithPanelBounds(next, panel, separated)
            }
            onSettingsChange(next)
        }
        fun clearDockedPanelDrag() {
            draggingPanel = null
            panelDragOrigin = null
            dockedPanelOriginDock = null
            dockedPanelPreviewBounds = null
            dockedPanelTargetDock = null
            panelGroupPreviewTarget = null
            panelGroupDragOrigins = emptyMap()
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun beginDockedPanelDrag(panel: WorkspacePanelKind, dock: WorkspacePanelDock, savedBounds: WorkspacePanelBounds) {
            if (dock == WorkspacePanelDock.FREE) return
            val initialBounds = WorkspacePanelDocking.initialFloatingBounds(
                dock,
                savedBounds,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                minimumPanelWidth(panel),
            )
            draggingPanel = panel
            panelDragOrigin = initialBounds
            dockedPanelOriginDock = dock
            dockedPanelPreviewBounds = initialBounds
            dockedPanelTargetDock = null
            panelDragDeltaXDp = 0f
            panelDragDeltaYDp = 0f
        }
        fun updateDockedPanelDrag(deltaXPx: Float, deltaYPx: Float) {
            val panel = draggingPanel ?: return
            if (dockedPanelOriginDock == null) return
            val deltaXDp = deltaXPx / editorDensity.density
            val deltaYDp = deltaYPx / editorDensity.density
            panelDragDeltaXDp += deltaXDp
            panelDragDeltaYDp += deltaYDp
            val current = dockedPanelPreviewBounds ?: panelDragOrigin ?: return
            val next = WorkspacePanelPlacement.move(
                current,
                deltaXDp,
                deltaYDp,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                minimumPanelWidth(panel),
            )
            dockedPanelPreviewBounds = next
            dockedPanelTargetDock = WorkspacePanelDocking.target(
                next,
                workspaceWidthDp,
                usableWorkspaceHeightDp,
                panelDragDeltaXDp,
                panelDragDeltaYDp,
            )?.takeIf { it != dockedPanelOriginDock }
        }
        fun finishDockedPanelDrag(panel: WorkspacePanelKind, originDock: WorkspacePanelDock) {
            val preview = dockedPanelPreviewBounds
            val target = dockedPanelTargetDock
            val detached = draggingPanel == panel && dockedPanelOriginDock == originDock &&
                WorkspacePanelDocking.shouldDetach(originDock, panelDragDeltaXDp, panelDragDeltaYDp)
            clearDockedPanelDrag()
            if (!detached || preview == null) return
            activeFloatingPanel = panelName(panel)
            if (target != null) {
                var next = when (panel) {
                    WorkspacePanelKind.PAGES -> settings.copy(pagePanelDock = target)
                    WorkspacePanelKind.BRUSHES -> settings.copy(brushPanelDock = target)
                    WorkspacePanelKind.LAYERS -> settings.copy(layerPanelDock = target)
                }
                if (WorkspacePanelTabs.isStacked(next, target)) next = WorkspacePanelTabs.withActive(next, target, panel)
                onSettingsChange(next)
                return
            }
            val joinsFloatingGroup = settings.freeDockStacked && freePanelTabs.isNotEmpty()
            val floatingMembers = if (joinsFloatingGroup) (freePanelTabs + panel).distinct() else listOf(panel)
            val floatingBounds = if (joinsFloatingGroup) {
                val groupMinimum = floatingMembers.maxOf(::minimumPanelWidth)
                val anchor = panelBounds(freeActivePanel ?: freePanelTabs.first())
                WorkspacePanelPlacement.clamp(anchor.copy(widthDp = anchor.widthDp.coerceAtLeast(groupMinimum)), workspaceWidthDp, usableWorkspaceHeightDp, groupMinimum)
            } else preview
            floatingMembers.forEach { setPanelBounds(it, floatingBounds) }
            var next = when (panel) {
                WorkspacePanelKind.PAGES -> settings.copy(
                    pagePanelDock = WorkspacePanelDock.FREE,
                    pagePanelXDp = floatingBounds.xDp,
                    pagePanelYDp = floatingBounds.yDp,
                    pagePanelWidthDp = floatingBounds.widthDp,
                    pagePanelHeightDp = floatingBounds.heightDp,
                    freeDockActivePanel = if (joinsFloatingGroup) panel else settings.freeDockActivePanel,
                )
                WorkspacePanelKind.BRUSHES -> settings.copy(
                    brushPanelDock = WorkspacePanelDock.FREE,
                    brushPanelXDp = floatingBounds.xDp,
                    brushPanelYDp = floatingBounds.yDp,
                    brushPanelWidthDp = floatingBounds.widthDp,
                    brushPanelHeightDp = floatingBounds.heightDp,
                    freeDockActivePanel = if (joinsFloatingGroup) panel else settings.freeDockActivePanel,
                )
                WorkspacePanelKind.LAYERS -> settings.copy(
                    layerPanelDock = WorkspacePanelDock.FREE,
                    layerPanelXDp = floatingBounds.xDp,
                    layerPanelYDp = floatingBounds.yDp,
                    layerPanelWidthDp = floatingBounds.widthDp,
                    layerPanelHeightDp = floatingBounds.heightDp,
                    freeDockActivePanel = if (joinsFloatingGroup) panel else settings.freeDockActivePanel,
                )
            }
            if (joinsFloatingGroup) floatingMembers.forEach { member -> next = settingsWithPanelBounds(next, member, floatingBounds) }
            onSettingsChange(next)
        }
        val bottomPanelCount = listOf(
            settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.BOTTOM,
            brushesOpen && settings.brushPanelDock == WorkspacePanelDock.BOTTOM,
            layersOpen && settings.layerPanelDock == WorkspacePanelDock.BOTTOM,
        ).count { it }
        val bottomHasExpandedPanel =
            (WorkspacePanelKind.PAGES in bottomPanelTabs && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM) && settings.pagePanelMode == WorkspacePanelMode.EXPANDED) ||
            (WorkspacePanelKind.BRUSHES in bottomPanelTabs && showPanel(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.BOTTOM) && settings.brushPanelMode == WorkspacePanelMode.EXPANDED) ||
            (WorkspacePanelKind.LAYERS in bottomPanelTabs && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM) && settings.layerPanelMode == WorkspacePanelMode.EXPANDED)
        val bottomPanelContentHeight = if (bottomHasExpandedPanel) (workspaceHeightDp * .38f).coerceIn(180f, 320f).dp else 82.dp
        val bottomPanelHeight = bottomPanelContentHeight + bottomNavigationPadding
        val bottomPanelsDescription = tr("Bottom workspace panels", "底部工作区面板")
        Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (leftPanelTabs.size > 1) DockedPanelTabStrip(
                dock = WorkspacePanelDock.LEFT,
                panels = leftPanelTabs,
                active = leftActivePanel,
                stacked = leftPanelsStacked,
                onSelect = { setActivePanel(WorkspacePanelDock.LEFT, it) },
                onMove = ::movePanelTab,
                onStacked = { setDockStacked(WorkspacePanelDock.LEFT, it, leftPanelTabs) },
            )
            if (editorWidth >= 700.dp && settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT)) PageRail(
                document, documents, selectedPage.id, settings.pagePanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                pageTemplates = pageTemplates,
                onSelect = ::selectPageWithViewport, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onTransfer = onTransferPage,
                onApplyTemplate = onApplyPageTemplate, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                panelMode = settings.pagePanelMode,
                panelLocked = settings.pagePanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT, pagePanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.LEFT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
            )
            if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT)) LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                dock = settings.layerPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                onColor = { brushColor = it },
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                panelMode = settings.layerPanelMode,
                panelLocked = settings.layerPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT, layerPanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.LEFT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
            )
            if (brushesOpen && settings.brushPanelDock == WorkspacePanelDock.LEFT && showPanel(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.LEFT)) BrushPanel(
                brushes = brushes,
                registeredGroups = brushGroups,
                selectedBrushId = selectedBrushId,
                settings = settings,
                dock = settings.brushPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.BRUSHES, it) },
                onClose = { if (brushesOpen) onToggleBrushes() },
                onBrushSelected = { brush ->
                    exitCanvasNavigation()
                    val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
                    onUpdateBrush(usedBrush)
                    selectedBrushId = usedBrush.id
                    activeBrush = usedBrush
                    brushSize = usedBrush.defaultSize
                    brushOpacity = usedBrush.defaultOpacity
                    brushColor = AndroidColor.rgb(17, 17, 17)
                    val chosenTool = if (BrushRoleClassifier.isEraser(usedBrush)) EditorTool.ERASER else EditorTool.PEN
                    tool = chosenTool
                    canvasView?.apply { this.tool = chosenTool; inkOpacity = 1f; brushId = usedBrush.id; baseWidth = usedBrush.defaultSize; inkColor = brushColor }
                },
                onImportBrushes = onImportBrushes,
                onExportBrush = onExportBrush,
                onUpdateBrush = { updated -> onUpdateBrush(updated); if (activeBrush?.id == updated.id) { activeBrush = updated; brushSize = updated.defaultSize } },
                onReplaceBrushes = { updated ->
                    onReplaceBrushes(updated)
                    activeBrush = activeBrush?.let { current -> updated.firstOrNull { it.id == current.id } }
                    if (activeBrush == null) selectedBrushId = null
                },
                onReplaceGroups = onReplaceBrushGroups,
                onDeleteBrush = { brushId -> onDeleteBrush(brushId); if (activeBrush?.id == brushId) { activeBrush = null; selectedBrushId = null } },
                onReorderBrush = onReorderBrush,
                panelMode = settings.brushPanelMode,
                panelLocked = settings.brushPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(brushPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(brushPanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.LEFT, brushPanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.LEFT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
            )
            val hasSideDockedToolPanel = (brushesOpen && settings.brushPanelMode == WorkspacePanelMode.EXPANDED && settings.brushPanelDock in setOf(WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT) && showPanel(WorkspacePanelKind.BRUSHES, settings.brushPanelDock)) ||
                (layersOpen && settings.layerPanelMode == WorkspacePanelMode.EXPANDED && settings.layerPanelDock in setOf(WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT) && showPanel(WorkspacePanelKind.LAYERS, settings.layerPanelDock))
            if (!hasSideDockedToolPanel || editorWidth >= 900.dp) BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(Workspace), contentAlignment = Alignment.Center) {
                val toolbarAreaWidth = maxWidth
                val toolbarAreaHeight = maxHeight
                PaperCanvas(
                    modifier = Modifier.fillMaxHeight(.88f).aspectRatio((PageCropTransform.visibleWidth(selectedPage) / PageCropTransform.visibleHeight(selectedPage).coerceAtLeast(1f)).coerceIn(.35f, 2.8f)).graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = panX, translationY = panY, rotationZ = viewRotation),
                    tool = tool,
                    onView = { canvasView = it },
                    page = selectedPage,
                    documentId = document.id,
                    onStroke = onStroke,
                    onRemoveStroke = onRemoveStroke,
                    onStrokesReplaced = onStrokesReplaced,
                    onElement = onElement,
                    onRemoveElement = onRemoveElement,
                    onDuplicateElement = onDuplicateElement,
                    selectedElementId = selectedElementId,
                    onSelectElement = { selectedElementId = it },
                    assets = assets,
                    settings = settings,
                    onZoom = { factor, focusX, focusY ->
                        val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), factor, focusX, focusY, geometry = currentViewportGeometry())
                        zoom = next.scale; panX = next.panX; panY = next.panY
                    },
                    onPan = { dx, dy -> val next = ViewportTransform.panBy(ViewportState(zoom, panX, panY, viewRotation), dx, dy, geometry = currentViewportGeometry()); panX = next.panX; panY = next.panY },
                    onRotate = { delta ->
                        val next = ViewportTransform.rotateBy(ViewportState(zoom, panX, panY, viewRotation), delta, currentViewportGeometry())
                        viewRotation = next.rotationDegrees; panX = next.panX; panY = next.panY
                    },
                    onUndoGesture = { canvasView?.undo() },
                    onRedoGesture = { canvasView?.redo() },
                    activeBrush = activeBrush,
                    brushSize = brushSize,
                    brushOpacity = brushOpacity,
                    brushColor = brushColor,
                    shapeKind = insertShape,
                    activeLayerId = activeLayerId,
                    eraserPreset = settings.eraserPreset,
                    forceNavigation = handToolEnabled || handToolHeld || rotateToolEnabled || rotateToolHeld,
                    rotateNavigation = rotateToolEnabled || rotateToolHeld,
                    searchBounds = searchFocus?.takeIf { it.pageId == selectedPage.id }?.bounds ?: pdfLinkFocus?.takeIf { it.first == selectedPage.id }?.second,
                    onPdfGotoLink = { link ->
                        document.pages.firstOrNull { it.id == link.destinationPageId }?.let { target ->
                            searchFocus = null
                            pdfLinkFocus = target.id to PdfGotoLinkNavigation.destinationBounds(link)
                            selectPageWithViewport(target.id)
                            if (link.destinationZoom > 0f) zoom = link.destinationZoom.coerceIn(1f, 8f)
                        }
                    },
                    onInputDiagnostic = { kind, pressure, tilt, buttons -> inputDiagnostic = "$kind  pressure ${"%.3f".format(pressure)}  tilt ${"%.2f".format(Math.toDegrees(tilt.toDouble()))}°  buttons $buttons" },
                    onColorSampled = { brushColor = it },
                    onBrushSizeAdjusted = { raw ->
                        val value = raw.coerceIn(.5f, 400f)
                        brushSize = value
                        when (tool) {
                            EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penSize = value))
                            EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterSize = value))
                            EditorTool.ERASER -> if (activeBrush == null) onSettingsChange(settings.copy(eraserSize = value))
                            else -> Unit
                        }
                    },
                    onKeyInputDiagnostic = { diagnostic ->
                        val keyName = KeyEvent.keyCodeToString(diagnostic.keyCode).removePrefix("KEYCODE_")
                        val device = diagnostic.deviceName ?: unknownInputDeviceText
                        val descriptor = diagnostic.deviceDescriptor ?: "—"
                        val action = diagnostic.matchedAction?.name?.lowercase()?.replace('_', ' ') ?: unmappedShortcutText
                        inputDiagnostic = "$device · $keyName · scan ${diagnostic.scanCode} · meta 0x${diagnostic.metaState.toString(16)} · source 0x${diagnostic.source.toString(16)} · id ${diagnostic.deviceId} · $descriptor · $action · ${diagnostic.phase.name.lowercase()}"
                    },
                    onMotionInputDiagnostic = { diagnostic ->
                        val device = diagnostic.deviceName ?: unknownInputDeviceText
                        val descriptor = diagnostic.deviceDescriptor ?: "—"
                        val action = diagnostic.matchedAction?.name?.lowercase()?.replace('_', ' ') ?: unmappedShortcutText
                        val axis = diagnostic.axisCode?.let { "axis $it ${"%.3f".format(diagnostic.axisValue)}" } ?: "button ${diagnostic.actionButton}"
                        inputDiagnostic = "$device · motion ${diagnostic.action} · $axis · state ${diagnostic.buttonState} · source 0x${diagnostic.source.toString(16)} · id ${diagnostic.deviceId} · $descriptor · $action"
                    },
                    onShortcutAction = { activation ->
                        val action = activation.action
                        fun selectImportedBrush(brush: ImportedBrush) {
                            val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
                            onUpdateBrush(usedBrush)
                            selectedBrushId = usedBrush.id
                            activeBrush = usedBrush
                            brushSize = usedBrush.defaultSize
                            brushOpacity = usedBrush.defaultOpacity
                            brushColor = AndroidColor.rgb(17, 17, 17)
                            tool = if (BrushRoleClassifier.isEraser(usedBrush)) EditorTool.ERASER else EditorTool.PEN
                            if (activeLayerId == LayerIds.HIGHLIGHT || activeLayerId == LayerIds.OBJECTS) activeLayerId = LayerIds.INK
                        }

                        fun updateBrushSize(multiplier: Float) {
                            val value = (brushSize * multiplier).coerceIn(.5f, 400f)
                            brushSize = value
                            when (tool) {
                                EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penSize = value))
                                EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterSize = value))
                                EditorTool.ERASER -> if (activeBrush == null) onSettingsChange(settings.copy(eraserSize = value))
                                else -> Unit
                            }
                        }

                        fun updateBrushOpacity(delta: Float) {
                            val value = (brushOpacity + delta).coerceIn(.05f, 1f)
                            brushOpacity = value
                            when (tool) {
                                EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penOpacity = value))
                                EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterOpacity = value))
                                else -> Unit
                            }
                        }

                        fun rememberDrawingSelection() {
                            previousDrawingTool = tool
                            previousDrawingBrushId = activeBrush?.id
                            previousDrawingSize = brushSize
                            previousDrawingOpacity = brushOpacity
                            previousDrawingColor = brushColor
                        }

                        fun restorePreviousDrawingTool() {
                            tool = previousDrawingTool
                            val restoredBrush = previousDrawingBrushId?.let { id -> brushes.firstOrNull { it.id == id && !it.hidden } }
                            activeBrush = restoredBrush
                            selectedBrushId = restoredBrush?.id
                            brushSize = previousDrawingSize
                            brushOpacity = previousDrawingOpacity
                            brushColor = previousDrawingColor
                        }

                        fun enterEraser() {
                            rememberDrawingSelection()
                            tool = EditorTool.ERASER
                            brushSize = settings.eraserSize
                            brushOpacity = 1f
                        }

                        fun selectBrushGroup(group: ShortcutBrushGroup, fallbackToSystemPen: Boolean = false) {
                            val targetPath = settings.shortcutBrushGroupTargets.forGroup(group)
                            ShortcutBrushSelection.preferred(brushes, group, targetPath)?.let(::selectImportedBrush) ?: if (fallbackToSystemPen) {
                                selectedBrushId = null
                                activeBrush = null
                                tool = EditorTool.PEN
                                brushSize = settings.penSize
                                brushOpacity = settings.penOpacity
                            } else {
                                brushes.filterNot { it.hidden }.maxByOrNull { it.lastUsedAt }?.let(::selectImportedBrush)
                            }
                        }

                        fun applyQuickSlot(index: Int, temporary: Boolean) {
                            val slot = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)[index]
                            when (slot.kind) {
                                ToolboxQuickSlotKind.EMPTY -> Unit
                                ToolboxQuickSlotKind.BRUSH, ToolboxQuickSlotKind.BRUSH_GROUP -> {
                                    if (temporary) rememberDrawingSelection()
                                    ToolboxQuickSlots.preferredBrush(slot, brushes)?.let(::selectImportedBrush)
                                }
                                ToolboxQuickSlotKind.TOOL_PRESET -> {
                                    if (temporary) rememberDrawingSelection()
                                    val selected = ToolboxQuickSlots.preferredBrush(slot, brushes)
                                    if (slot.brushId.isBlank()) {
                                        selectedBrushId = null
                                        activeBrush = null
                                        tool = EditorTool.PEN
                                        activeLayerId = LayerIds.INK
                                    } else selected?.let(::selectImportedBrush) ?: return
                                    brushSize = slot.size
                                    brushOpacity = slot.opacity
                                    brushColor = slot.colorArgb
                                }
                                ToolboxQuickSlotKind.WORKSPACE -> slot.workspace?.let { workspace ->
                                    if (temporary) quickSlotWorkspaceBeforeHold = settings.captureToolboxWorkspace()
                                    applyWorkspaceSnapshot(workspace)
                                }
                            }
                        }

                        if (activation.phase == ShortcutPhase.RELEASE) {
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_RULER) onSettingsChange(settings.copy(rulerEnabled = rulerBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_GRID_SNAP) onSettingsChange(settings.copy(gridSnapEnabled = gridBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_ALIGNMENT_GUIDES) onSettingsChange(settings.copy(alignmentGuidesEnabled = guidesBeforeShortcut))
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.HAND_TOOL) handToolHeld = false
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.ROTATE_VIEW_TOOL) rotateToolHeld = false
                            if (activation.trigger == ShortcutTrigger.HOLD && action == ShortcutAction.TOGGLE_ERASER && tool == EditorTool.ERASER) restorePreviousDrawingTool()
                            if (activation.trigger == ShortcutTrigger.HOLD && action in setOf(ShortcutAction.PEN_OR_PENCIL_GROUP, ShortcutAction.PEN_GROUP, ShortcutAction.PENCIL_GROUP, ShortcutAction.BRUSH_GROUP, ShortcutAction.HIGHLIGHTER_TOOL, ShortcutAction.SELECT_TOOL, ShortcutAction.EYEDROPPER_TOOL, ShortcutAction.LASER_TOOL)) restorePreviousDrawingTool()
                            if (activation.trigger == ShortcutTrigger.HOLD) ToolboxQuickSlots.indexOf(action)?.let { index ->
                                val slot = ToolboxQuickSlots.normalize(settings.toolboxQuickSlots)[index]
                                when (slot.kind) {
                                    ToolboxQuickSlotKind.BRUSH, ToolboxQuickSlotKind.BRUSH_GROUP, ToolboxQuickSlotKind.TOOL_PRESET -> restorePreviousDrawingTool()
                                    ToolboxQuickSlotKind.WORKSPACE -> quickSlotWorkspaceBeforeHold?.let(::applyWorkspaceSnapshot).also { quickSlotWorkspaceBeforeHold = null }
                                    ToolboxQuickSlotKind.EMPTY -> Unit
                                }
                            }
                        } else when (action) {
                            ShortcutAction.PEN_OR_PENCIL_GROUP -> {
                                exitCanvasNavigation()
                                if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection()
                                val target = if (ShortcutBrushSelection.matches(activeBrush, ShortcutBrushGroup.PEN)) ShortcutBrushGroup.PENCIL else ShortcutBrushGroup.PEN
                                selectBrushGroup(target, fallbackToSystemPen = true)
                            }
                            ShortcutAction.PEN_GROUP -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); selectBrushGroup(ShortcutBrushGroup.PEN, fallbackToSystemPen = true) }
                            ShortcutAction.PENCIL_GROUP -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); selectBrushGroup(ShortcutBrushGroup.PENCIL, fallbackToSystemPen = true) }
                            ShortcutAction.BRUSH_GROUP -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); selectBrushGroup(ShortcutBrushGroup.BRUSH) }
                            ShortcutAction.TOGGLE_ERASER -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                exitCanvasNavigation()
                                if (tool != EditorTool.ERASER) enterEraser()
                            } else { exitCanvasNavigation(); if (tool == EditorTool.ERASER) restorePreviousDrawingTool() else enterEraser() }
                            ShortcutAction.HIGHLIGHTER_TOOL -> {
                                exitCanvasNavigation()
                                if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection()
                                selectedBrushId = null
                                activeBrush = null
                                tool = EditorTool.HIGHLIGHTER
                                brushSize = settings.highlighterSize
                                brushOpacity = settings.highlighterOpacity
                                activeLayerId = LayerIds.HIGHLIGHT
                            }
                            ShortcutAction.SELECT_TOOL -> { exitCanvasNavigation(); if (activation.trigger == ShortcutTrigger.HOLD) rememberDrawingSelection(); tool = EditorTool.SELECT }
                            ShortcutAction.HAND_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) { rotateToolEnabled = false; rotateToolHeld = false; handToolHeld = true } else { val enable = !handToolEnabled; exitCanvasNavigation(); handToolEnabled = enable }
                            ShortcutAction.ROTATE_VIEW_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) { handToolEnabled = false; handToolHeld = false; rotateToolHeld = true } else { val enable = !rotateToolEnabled; exitCanvasNavigation(); rotateToolEnabled = enable }
                            ShortcutAction.EYEDROPPER_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                exitCanvasNavigation()
                                rememberDrawingSelection()
                                tool = EditorTool.EYEDROPPER
                            } else {
                                exitCanvasNavigation()
                                if (tool == EditorTool.EYEDROPPER) restorePreviousDrawingTool() else { rememberDrawingSelection(); tool = EditorTool.EYEDROPPER }
                            }
                            ShortcutAction.SHAPE_TOOL -> { exitCanvasNavigation(); tool = EditorTool.SHAPE; shapeInsertOpen = true }
                            ShortcutAction.TEXT_TOOL -> { exitCanvasNavigation(); tool = EditorTool.TEXT; insertText = ""; textInsertOpen = true }
                            ShortcutAction.NOTE_TOOL -> { exitCanvasNavigation(); tool = EditorTool.NOTE; insertText = ""; stickyInsertOpen = true }
                            ShortcutAction.LINK_TOOL -> { exitCanvasNavigation(); tool = EditorTool.LINK; insertLinkTitle = ""; insertLinkUrl = ""; linkInsertOpen = true }
                            ShortcutAction.IMAGE_TOOL -> { exitCanvasNavigation(); onInsertImage(); tool = EditorTool.SELECT }
                            ShortcutAction.AUDIO_TOOL -> { exitCanvasNavigation(); if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) audioOpen = true else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                            ShortcutAction.LASER_TOOL -> if (activation.trigger == ShortcutTrigger.HOLD) {
                                rememberDrawingSelection()
                                exitCanvasNavigation()
                                tool = EditorTool.LASER
                            } else if (tool == EditorTool.LASER) restorePreviousDrawingTool() else {
                                rememberDrawingSelection()
                                exitCanvasNavigation()
                                tool = EditorTool.LASER
                            }
                            ShortcutAction.BRUSH_SIZE_DECREASE -> updateBrushSize(1f / 1.1f)
                            ShortcutAction.BRUSH_SIZE_INCREASE -> updateBrushSize(1.1f)
                            ShortcutAction.OPACITY_DECREASE -> updateBrushOpacity(-.05f)
                            ShortcutAction.OPACITY_INCREASE -> updateBrushOpacity(.05f)
                            ShortcutAction.PREVIOUS_BRUSH -> ShortcutBrushSelection.adjacent(brushes, activeBrush?.id, -1)?.let(::selectImportedBrush)
                            ShortcutAction.NEXT_BRUSH -> ShortcutBrushSelection.adjacent(brushes, activeBrush?.id, 1)?.let(::selectImportedBrush)
                            ShortcutAction.CUT_SELECTION -> if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.cutSelectionToClipboard() != true) Toast.makeText(context, selectBeforeCutMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.COPY_SELECTION -> if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.copySelectionToClipboard() != true) Toast.makeText(context, selectBeforeCopyMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.PASTE_SELECTION -> { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard() != true) Toast.makeText(context, noInkClipboardMessage, Toast.LENGTH_SHORT).show() }
                            ShortcutAction.DELETE_SELECTION -> selectedElementId?.let { onRemoveElement(document.id, selectedPage.id, it); selectedElementId = null } ?: canvasView?.deleteSelection()
                            ShortcutAction.SELECT_ALL -> { selectedElementId = null; tool = EditorTool.SELECT; canvasView?.selectAllEditableStrokes() }
                            ShortcutAction.DESELECT -> { selectedElementId = null; canvasView?.clearSelection() }
                            ShortcutAction.UNDO -> canvasView?.undo()
                            ShortcutAction.REDO -> canvasView?.redo()
                            ShortcutAction.NEW_LAYER -> onAddLayer(document.id, selectedPage.id, "Layer")
                            ShortcutAction.MERGE_LAYER_DOWN -> onMergeLayerDown(document.id, selectedPage.id, activeLayerId)?.let { activeLayerId = it }
                            ShortcutAction.SELECT_LAYER_ABOVE -> LayerShortcutSelection.adjacentEditable(selectedPage.layers, activeLayerId, 1)?.let { activeLayerId = it }
                            ShortcutAction.SELECT_LAYER_BELOW -> LayerShortcutSelection.adjacentEditable(selectedPage.layers, activeLayerId, -1)?.let { activeLayerId = it }
                            ShortcutAction.ZOOM_OUT -> { val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), .9f, 0f, 0f, geometry = currentViewportGeometry()); zoom = next.scale; panX = next.panX; panY = next.panY }
                            ShortcutAction.ZOOM_IN -> { val next = ViewportTransform.zoomAround(ViewportState(zoom, panX, panY, viewRotation), 1.1f, 0f, 0f, geometry = currentViewportGeometry()); zoom = next.scale; panX = next.panX; panY = next.panY }
                            ShortcutAction.ROTATE_VIEW_LEFT -> rotateViewByShortcut(-5f)
                            ShortcutAction.ROTATE_VIEW_RIGHT -> rotateViewByShortcut(5f)
                            ShortcutAction.FIT_PAGE -> fitPage()
                            ShortcutAction.ACTUAL_SIZE -> actualSize()
                            ShortcutAction.FIT_WIDTH -> fitWidth()
                            ShortcutAction.RESET_VIEW_ROTATION -> resetViewRotation()
                            ShortcutAction.TOGGLE_RULER -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    rulerBeforeShortcut = settings.rulerEnabled
                                    onSettingsChange(settings.copy(rulerEnabled = true))
                                } else onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled))
                            }
                            ShortcutAction.TOGGLE_GRID_SNAP -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    gridBeforeShortcut = settings.gridSnapEnabled
                                    onSettingsChange(settings.copy(gridSnapEnabled = true))
                                } else onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled))
                            }
                            ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> {
                                if (activation.trigger == ShortcutTrigger.HOLD) {
                                    guidesBeforeShortcut = settings.alignmentGuidesEnabled
                                    onSettingsChange(settings.copy(alignmentGuidesEnabled = true))
                                } else onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled))
                            }
                            ShortcutAction.PREVIOUS_PAGE -> document.pages.indexOfFirst { it.id == selectedPage.id }.takeIf { it > 0 }?.let { selectPageWithViewport(document.pages[it - 1].id) }
                            ShortcutAction.NEXT_PAGE -> document.pages.indexOfFirst { it.id == selectedPage.id }.takeIf { it in 0 until document.pages.lastIndex }?.let { selectPageWithViewport(document.pages[it + 1].id) }
                            ShortcutAction.TOGGLE_BRUSH_PANEL -> onToggleBrushes()
                            ShortcutAction.TOGGLE_LAYER_PANEL -> {
                                val open = !layersOpen
                                layersOpen = open
                                onSettingsChange(settings.copy(showLayerPanel = open))
                            }
                            ShortcutAction.TOGGLE_PANELS -> if (!panelsHiddenByShortcut) {
                                pageRailBeforeShortcut = settings.showPageRail
                                toolbarBeforeShortcut = settings.showToolbar
                                brushesBeforeShortcut = brushesOpen
                                layersBeforeShortcut = layersOpen
                                onSettingsChange(settings.copy(showPageRail = false, showToolbar = false))
                                if (brushesOpen) onToggleBrushes()
                                layersOpen = false
                                panelsHiddenByShortcut = true
                            } else {
                                onSettingsChange(settings.copy(showPageRail = pageRailBeforeShortcut, showToolbar = toolbarBeforeShortcut))
                                if (brushesBeforeShortcut && !brushesOpen) onToggleBrushes()
                                layersOpen = layersBeforeShortcut
                                panelsHiddenByShortcut = false
                            }
                            ShortcutAction.SAVE_CHECKPOINT -> Toast.makeText(context, allChangesSavedMessage, Toast.LENGTH_SHORT).show()
                            ShortcutAction.QUICK_SLOT_1, ShortcutAction.QUICK_SLOT_2, ShortcutAction.QUICK_SLOT_3, ShortcutAction.QUICK_SLOT_4,
                            ShortcutAction.QUICK_SLOT_5, ShortcutAction.QUICK_SLOT_6, ShortcutAction.QUICK_SLOT_7, ShortcutAction.QUICK_SLOT_8 -> ToolboxQuickSlots.indexOf(action)?.let { applyQuickSlot(it, activation.trigger == ShortcutTrigger.HOLD) }
                        }
                    },
                    onRulerGuideChange = { guide ->
                        onSettingsChange(settings.copy(
                            rulerCenterX = guide.centerX,
                            rulerCenterY = guide.centerY,
                            rulerAngleDegrees = guide.angleDegrees,
                            rulerSnapDegrees = guide.snapDegrees,
                        ))
                    },
                    onCanvasGuideChange = { guide ->
                        onSettingsChange(settings.copy(
                            gridSnapEnabled = guide.gridSnapEnabled,
                            gridSpacing = guide.gridSpacing,
                            alignmentGuidesEnabled = guide.alignmentGuidesEnabled,
                            verticalGuideX = guide.verticalGuideX,
                            horizontalGuideY = guide.horizontalGuideY,
                        ))
                    },
                )
                val dockVertical = settings.toolbarDock in setOf(ToolbarDock.LEFT, ToolbarDock.RIGHT) || (settings.toolbarDock == ToolbarDock.FREE && settings.verticalToolbar)
                val dockAlignment = when (settings.toolbarDock) {
                    ToolbarDock.TOP -> Alignment.TopCenter
                    ToolbarDock.BOTTOM -> Alignment.BottomCenter
                    ToolbarDock.LEFT -> Alignment.CenterStart
                    ToolbarDock.RIGHT -> Alignment.CenterEnd
                    ToolbarDock.FREE -> Alignment.TopStart
                }
                val dockPadding = when (settings.toolbarDock) {
                    ToolbarDock.TOP -> PaddingValues(top = 16.dp)
                    ToolbarDock.BOTTOM -> PaddingValues(bottom = 16.dp)
                    ToolbarDock.LEFT -> PaddingValues(start = 16.dp)
                    ToolbarDock.RIGHT -> PaddingValues(end = 16.dp)
                    ToolbarDock.FREE -> PaddingValues(0.dp)
                }
                val boundedToolbarPosition = FloatingToolbarPlacement.clamp(toolbarX, toolbarY, toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                LaunchedEffect(toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical, settings.toolbarDock) {
                    if (settings.toolbarDock == ToolbarDock.FREE && (toolbarX != boundedToolbarPosition.xDp || toolbarY != boundedToolbarPosition.yDp)) {
                        toolbarX = boundedToolbarPosition.xDp
                        toolbarY = boundedToolbarPosition.yDp
                        onSettingsChange(settings.copy(toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY))
                    }
                }
                if (settings.showToolbar) ToolDock(tool = tool, onTool = { chosen ->
                    exitCanvasNavigation()
                    if (chosen == EditorTool.EYEDROPPER && tool != EditorTool.EYEDROPPER) {
                        previousDrawingTool = tool
                        previousDrawingBrushId = activeBrush?.id
                        previousDrawingSize = brushSize
                        previousDrawingOpacity = brushOpacity
                    }
                    tool = chosen; canvasView?.tool = chosen
                    when (chosen) {
                        EditorTool.PEN -> { activeBrush = null; brushOpacity = settings.penOpacity; brushSize = settings.penSize; if (activeLayerId == LayerIds.HIGHLIGHT || activeLayerId == LayerIds.OBJECTS) activeLayerId = LayerIds.INK }
                        EditorTool.HIGHLIGHTER -> { activeBrush = null; brushOpacity = settings.highlighterOpacity; brushSize = settings.highlighterSize; activeLayerId = LayerIds.HIGHLIGHT }
                        EditorTool.ERASER -> { brushOpacity = 1f; brushSize = settings.eraserSize }
                        EditorTool.SHAPE -> shapeInsertOpen = true
                        EditorTool.TEXT -> { insertText = ""; textInsertOpen = true }
                        EditorTool.NOTE -> { insertText = ""; stickyInsertOpen = true }
                        EditorTool.LINK -> { insertLinkTitle = ""; insertLinkUrl = ""; linkInsertOpen = true }
                        EditorTool.IMAGE -> { onInsertImage(); tool = EditorTool.SELECT }
                        EditorTool.AUDIO -> if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) audioOpen = true else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> Unit
                    }
                }, onUndo = { canvasView?.undo() }, onRedo = { canvasView?.redo() }, onScale = { selectedElementId?.let { id -> selectedPage.elements.firstOrNull { it.id == id }?.let { onElement(document.id, selectedPage.id, resizeElement(it, 1.15f)) } } ?: canvasView?.scaleSelection(1.15f) }, onRotate = { selectedElementId?.let { id -> selectedPage.elements.firstOrNull { it.id == id }?.let { onElement(document.id, selectedPage.id, rotateElement(it, 15f)) } } ?: canvasView?.rotateSelection(15f) }, onCopy = { selectedElementId?.let { onDuplicateElement(document.id, selectedPage.id, it) } ?: canvasView?.copySelection() }, onCut = { if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.cutSelectionToClipboard() != true) Toast.makeText(context, selectBeforeCutMessage, Toast.LENGTH_SHORT).show() }, onClipboardCopy = { if (selectedElementId != null) Toast.makeText(context, inkOnlyClipboardMessage, Toast.LENGTH_SHORT).show() else if (canvasView?.copySelectionToClipboard() != true) Toast.makeText(context, selectBeforeCopyMessage, Toast.LENGTH_SHORT).show() }, onPaste = { selectedElementId = null; if (canvasView?.pasteSelectionFromClipboard() != true) Toast.makeText(context, noInkClipboardMessage, Toast.LENGTH_SHORT).show() }, onDelete = { selectedElementId?.let { onRemoveElement(document.id, selectedPage.id, it); selectedElementId = null } ?: canvasView?.deleteSelection() }, selectionShape = settings.selectionShape, selectionMode = settings.selectionMode, selectionFilter = settings.selectionFilter, onSelectionShape = { onSettingsChange(settings.copy(selectionShape = it)) }, onSelectionMode = { onSettingsChange(settings.copy(selectionMode = it)) }, onSelectionFilter = { onSettingsChange(settings.copy(selectionFilter = it)) }, eraserPreset = settings.eraserPreset, onEraserPreset = { preset -> onSettingsChange(settings.copy(eraserPreset = preset, nonDestructiveEraser = preset.usesMask)) }, rulerEnabled = settings.rulerEnabled, rulerSnapDegrees = settings.rulerSnapDegrees, onToggleRuler = { onSettingsChange(settings.copy(rulerEnabled = !settings.rulerEnabled)) }, onRulerSnap = { onSettingsChange(settings.copy(rulerSnapDegrees = it)) }, onResetRuler = { onSettingsChange(settings.copy(rulerCenterX = .5f, rulerCenterY = .5f, rulerAngleDegrees = 0f)) }, gridSnapEnabled = settings.gridSnapEnabled, gridSpacing = settings.gridSpacing, alignmentGuidesEnabled = settings.alignmentGuidesEnabled, onToggleGridSnap = { onSettingsChange(settings.copy(gridSnapEnabled = !settings.gridSnapEnabled)) }, onGridSpacing = { onSettingsChange(settings.copy(gridSpacing = it)) }, onToggleAlignmentGuides = { onSettingsChange(settings.copy(alignmentGuidesEnabled = !settings.alignmentGuidesEnabled)) }, onResetAlignmentGuides = { onSettingsChange(settings.copy(verticalGuideX = .5f, horizontalGuideY = .5f)) }, onDrag = { dx, dy ->
                    val next = FloatingToolbarPlacement.clamp(toolbarX + dx / editorDensity.density, toolbarY + dy / editorDensity.density, toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                    toolbarX = next.xDp
                    toolbarY = next.yDp
                }, onDragEnd = { onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY, verticalToolbar = dockVertical)) }, vertical = dockVertical, onToggleOrientation = { onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, verticalToolbar = !dockVertical)) }, onResetPosition = {
                    val reset = FloatingToolbarPlacement.defaultPosition(toolbarAreaWidth.value, toolbarAreaHeight.value, dockVertical)
                    toolbarX = reset.xDp
                    toolbarY = reset.yDp
                    onSettingsChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = toolbarX, toolbarOffsetYDp = toolbarY))
                }, onDock = { dock -> onSettingsChange(settings.copy(toolbarDock = dock, verticalToolbar = when (dock) { ToolbarDock.LEFT, ToolbarDock.RIGHT -> true; ToolbarDock.TOP, ToolbarDock.BOTTOM -> false; ToolbarDock.FREE -> dockVertical })) }, brushSize = brushSize, brushOpacity = brushOpacity, brushColor = brushColor, onSize = { raw ->
                    val value = raw.coerceIn(.5f, 400f)
                    brushSize = value
                    when (tool) {
                        EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penSize = value))
                        EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterSize = value))
                        EditorTool.ERASER -> if (activeBrush == null) onSettingsChange(settings.copy(eraserSize = value))
                        else -> Unit
                    }
                }, onOpacity = { raw ->
                    val value = raw.coerceIn(.05f, 1f)
                    brushOpacity = value
                    when (tool) {
                        EditorTool.PEN -> if (activeBrush == null) onSettingsChange(settings.copy(penOpacity = value))
                        EditorTool.HIGHLIGHTER -> onSettingsChange(settings.copy(highlighterOpacity = value))
                        else -> Unit
                    }
                }, onColor = { brushColor = it }, modifier = Modifier.align(dockAlignment).padding(dockPadding).graphicsLayer(translationX = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.xDp.dp.toPx() } else 0f, translationY = if (settings.toolbarDock == ToolbarDock.FREE) with(editorDensity) { boundedToolbarPosition.yDp.dp.toPx() } else 0f))
                if (settings.showInputDiagnostics) Surface(Modifier.align(Alignment.BottomCenter).padding(12.dp), shape = RoundedCornerShape(20.dp), color = Color(0xE6111111)) { Text(inputDiagnostic, Modifier.padding(horizontal = 14.dp, vertical = 7.dp), color = Color.White, fontSize = 11.sp) }
            }
            if (rightPanelTabs.size > 1) DockedPanelTabStrip(
                dock = WorkspacePanelDock.RIGHT,
                panels = rightPanelTabs,
                active = rightActivePanel,
                stacked = rightPanelsStacked,
                onSelect = { setActivePanel(WorkspacePanelDock.RIGHT, it) },
                onMove = ::movePanelTab,
                onStacked = { setDockStacked(WorkspacePanelDock.RIGHT, it, rightPanelTabs) },
            )
            if (editorWidth >= 700.dp && settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT)) PageRail(
                document, documents, selectedPage.id, settings.pagePanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                pageTemplates = pageTemplates,
                onSelect = ::selectPageWithViewport, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onTransfer = onTransferPage,
                onApplyTemplate = onApplyPageTemplate, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                panelMode = settings.pagePanelMode,
                panelLocked = settings.pagePanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT, pagePanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.RIGHT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
            )
            if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT)) LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                dock = settings.layerPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                onColor = { brushColor = it },
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                panelMode = settings.layerPanelMode,
                panelLocked = settings.layerPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT, layerPanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.RIGHT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
            )
            if (brushesOpen && settings.brushPanelDock == WorkspacePanelDock.RIGHT && showPanel(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.RIGHT)) BrushPanel(
                brushes = brushes,
                registeredGroups = brushGroups,
                selectedBrushId = selectedBrushId,
                settings = settings,
                dock = settings.brushPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.BRUSHES, it) },
                onClose = { if (brushesOpen) onToggleBrushes() },
                onBrushSelected = { brush ->
                    exitCanvasNavigation()
                    val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
                    onUpdateBrush(usedBrush)
                    selectedBrushId = usedBrush.id
                    activeBrush = usedBrush
                    brushSize = usedBrush.defaultSize
                    brushOpacity = usedBrush.defaultOpacity
                    brushColor = when (usedBrush.format) {
                        BrushFormat.ABR -> AndroidColor.rgb(17, 17, 17)
                        BrushFormat.SUT -> AndroidColor.rgb(17, 17, 17)
                        else -> AndroidColor.rgb(17, 17, 17)
                    }
                    val chosenTool = if (BrushRoleClassifier.isEraser(usedBrush)) EditorTool.ERASER else EditorTool.PEN
                    tool = chosenTool
                    canvasView?.apply {
                        this.tool = chosenTool
                        inkOpacity = 1f
                        brushId = usedBrush.id
                        baseWidth = usedBrush.defaultSize
                        inkColor = brushColor
                    }
                },
                onImportBrushes = onImportBrushes,
                onExportBrush = onExportBrush,
                onUpdateBrush = { updated -> onUpdateBrush(updated); if (activeBrush?.id == updated.id) { activeBrush = updated; brushSize = updated.defaultSize } },
                onReplaceBrushes = { updated ->
                    onReplaceBrushes(updated)
                    activeBrush = activeBrush?.let { current -> updated.firstOrNull { it.id == current.id } }
                    if (activeBrush == null) selectedBrushId = null
                },
                onReplaceGroups = onReplaceBrushGroups,
                onDeleteBrush = { brushId -> onDeleteBrush(brushId); if (activeBrush?.id == brushId) { activeBrush = null; selectedBrushId = null } },
                onReorderBrush = onReorderBrush,
                panelMode = settings.brushPanelMode,
                panelLocked = settings.brushPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(brushPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(brushPanelLocked = it)) },
                onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.RIGHT, brushPanelBounds) },
                onPanelDrag = ::updateDockedPanelDrag,
                onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.RIGHT) },
                onPanelDragCancel = ::clearDockedPanelDrag,
            )
        }
        if (bottomPanelCount > 0) {
            HorizontalDivider(color = OutlineSoft)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(bottomPanelHeight)
                    .background(Color(0xFFF7F7F7))
                    .padding(bottom = bottomNavigationPadding)
                    .semantics { contentDescription = bottomPanelsDescription },
            ) {
                if (bottomPanelTabs.size > 1) DockedPanelTabStrip(
                    dock = WorkspacePanelDock.BOTTOM,
                    panels = bottomPanelTabs,
                    active = bottomActivePanel,
                    stacked = bottomPanelsStacked,
                    onSelect = { setActivePanel(WorkspacePanelDock.BOTTOM, it) },
                    onMove = ::movePanelTab,
                    onStacked = { setDockStacked(WorkspacePanelDock.BOTTOM, it, bottomPanelTabs) },
                )
                if (settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.BOTTOM && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM)) PageRail(
                    document, documents, selectedPage.id, settings.pagePanelDock,
                    onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                    onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                    pageTemplates = pageTemplates,
                    onSelect = ::selectPageWithViewport, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onTransfer = onTransferPage,
                    onApplyTemplate = onApplyPageTemplate, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                    onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                    onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                    onClearPageInk, onSetPageCrop, onRotatePage,
                    panelMode = settings.pagePanelMode,
                    panelLocked = settings.pagePanelLocked,
                    onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                    onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                    onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM, pagePanelBounds) },
                    onPanelDrag = ::updateDockedPanelDrag,
                    onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.PAGES, WorkspacePanelDock.BOTTOM) },
                    onPanelDragCancel = ::clearDockedPanelDrag,
                    modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, OutlineSoft).padding(12.dp),
                )
                if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.BOTTOM && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM)) LayerPanel(
                    page = selectedPage,
                    activeLayerId = activeLayerId,
                    brushColor = brushColor,
                    dock = settings.layerPanelDock,
                    onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                    onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                    onColor = { brushColor = it },
                    onSelect = { activeLayerId = it },
                    onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                    onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                    onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                    onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                    onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                    onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                    onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                    onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                    onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                    onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                    onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                    onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                    onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                    panelMode = settings.layerPanelMode,
                    panelLocked = settings.layerPanelLocked,
                    onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                    onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                    onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM, layerPanelBounds) },
                    onPanelDrag = ::updateDockedPanelDrag,
                    onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.LAYERS, WorkspacePanelDock.BOTTOM) },
                    onPanelDragCancel = ::clearDockedPanelDrag,
                    modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, OutlineSoft).background(Color(0xFFF7F7F7)).padding(10.dp),
                )
                if (brushesOpen && settings.brushPanelDock == WorkspacePanelDock.BOTTOM && showPanel(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.BOTTOM)) BrushPanel(
                    brushes = brushes,
                    registeredGroups = brushGroups,
                    selectedBrushId = selectedBrushId,
                    settings = settings,
                    dock = settings.brushPanelDock,
                    onDock = { changePanelDock(WorkspacePanelKind.BRUSHES, it) },
                    onClose = { if (brushesOpen) onToggleBrushes() },
                    onBrushSelected = { brush ->
                        exitCanvasNavigation()
                        val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
                        onUpdateBrush(usedBrush)
                        selectedBrushId = usedBrush.id
                        activeBrush = usedBrush
                        brushSize = usedBrush.defaultSize
                        brushOpacity = usedBrush.defaultOpacity
                        brushColor = AndroidColor.rgb(17, 17, 17)
                        val chosenTool = if (BrushRoleClassifier.isEraser(usedBrush)) EditorTool.ERASER else EditorTool.PEN
                        tool = chosenTool
                        canvasView?.apply { this.tool = chosenTool; inkOpacity = 1f; brushId = usedBrush.id; baseWidth = usedBrush.defaultSize; inkColor = brushColor }
                    },
                    onImportBrushes = onImportBrushes,
                    onExportBrush = onExportBrush,
                    onUpdateBrush = { updated -> onUpdateBrush(updated); if (activeBrush?.id == updated.id) { activeBrush = updated; brushSize = updated.defaultSize } },
                    onReplaceBrushes = { updated ->
                        onReplaceBrushes(updated)
                        activeBrush = activeBrush?.let { current -> updated.firstOrNull { it.id == current.id } }
                        if (activeBrush == null) selectedBrushId = null
                    },
                    onReplaceGroups = onReplaceBrushGroups,
                    onDeleteBrush = { brushId -> onDeleteBrush(brushId); if (activeBrush?.id == brushId) { activeBrush = null; selectedBrushId = null } },
                    onReorderBrush = onReorderBrush,
                    panelMode = settings.brushPanelMode,
                    panelLocked = settings.brushPanelLocked,
                    onPanelMode = { onSettingsChange(settings.copy(brushPanelMode = it)) },
                    onPanelLocked = { onSettingsChange(settings.copy(brushPanelLocked = it)) },
                    onPanelDragStart = { beginDockedPanelDrag(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.BOTTOM, brushPanelBounds) },
                    onPanelDrag = ::updateDockedPanelDrag,
                    onPanelDragEnd = { finishDockedPanelDrag(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.BOTTOM) },
                    onPanelDragCancel = ::clearDockedPanelDrag,
                    modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, OutlineSoft).background(Color(0xFFF7F7F7)).padding(12.dp),
                )
            }
        }
        }
        panelDockPreview?.let { dock ->
            WorkspacePanelDocking.previewBounds(dock, workspaceWidthDp, usableWorkspaceHeightDp)?.let { preview ->
                WorkspacePanelDockPreview(dock, preview)
            }
        }
        panelGroupPreviewTarget?.let { target ->
            WorkspacePanelGroupPreview(panelBounds(target), target)
        }
        val dockedOrigin = dockedPanelOriginDock
        val dockedPreview = dockedPanelPreviewBounds
        if (
            dockedOrigin != null && dockedPreview != null &&
            WorkspacePanelDocking.shouldDetach(dockedOrigin, panelDragDeltaXDp, panelDragDeltaYDp)
        ) {
            val target = dockedPanelTargetDock
            if (target != null) {
                WorkspacePanelDocking.previewBounds(target, workspaceWidthDp, usableWorkspaceHeightDp)?.let { preview ->
                    WorkspacePanelDockPreview(target, preview)
                }
            } else {
                WorkspacePanelFloatPreview(dockedPreview)
            }
        }
        if (settings.showPageRail && settings.pagePanelDock == WorkspacePanelDock.FREE && showPanel(WorkspacePanelKind.PAGES, WorkspacePanelDock.FREE)) FloatingWorkspacePanel(
            bounds = visiblePagePanelBounds,
            mode = settings.pagePanelMode,
            locked = settings.pagePanelLocked,
            zIndex = if (activeFloatingPanel == "pages") 6f else 3f,
            resizeDescription = tr("Resize pages panel", "调整页面面板大小"),
            onActivate = { activeFloatingPanel = "pages" },
            onResize = { dx, dy ->
                activeFloatingPanel = "pages"
                pagePanelBounds = resizeFloatingPanel(WorkspacePanelKind.PAGES, pagePanelBounds, dx / editorDensity.density, dy / editorDensity.density)
            },
            onResizeEnd = { if (freePanelsStacked) persistFloatingPanels(freePanelTabs) else persistPagePanel(pagePanelBounds) },
            tabs = if (freePanelsStacked) freePanelTabs else emptyList(),
            activeTab = freeActivePanel,
            onTab = ::selectFloatingPanel,
            onMoveTab = ::movePanelTab,
            onSeparateTabs = ::separateFloatingPanels,
        ) {
            PageRail(
                document, documents, selectedPage.id, settings.pagePanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.PAGES, it) },
                onClose = { onSettingsChange(settings.copy(showPageRail = false)) },
                pageTemplates = pageTemplates,
                onSelect = ::selectPageWithViewport, onAdd = onAddPage, onDuplicate = onDuplicatePage, onDelete = onDeletePage, onMove = onMovePage, onTransfer = onTransferPage,
                onApplyTemplate = onApplyPageTemplate, onImportTemplates = onImportPageTemplates, onToggleTemplateFavorite = onTogglePageTemplateFavorite,
                onSetDefaultTemplate = onSetNotebookDefaultTemplate, onSaveAsTemplate = onSavePageAsTemplate, onDeleteTemplate = onDeletePageTemplate,
                onToggleBookmark, onChangePageOutlineLevel, onTogglePageOutlineCollapsed, onRenamePage, onSetPageMetadata,
                onClearPageInk, onSetPageCrop, onRotatePage,
                panelMode = settings.pagePanelMode,
                panelLocked = settings.pagePanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(pagePanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(pagePanelLocked = it)) },
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7), RoundedCornerShape(12.dp)).padding(12.dp),
                onPanelDragStart = { activeFloatingPanel = "pages"; beginPanelDrag(WorkspacePanelKind.PAGES, pagePanelBounds) },
                onPanelDrag = { dx, dy ->
                    activeFloatingPanel = "pages"
                    val deltaXDp = dx / editorDensity.density
                    val deltaYDp = dy / editorDensity.density
                    val next = moveFloatingPanel(WorkspacePanelKind.PAGES, pagePanelBounds, deltaXDp, deltaYDp)
                    pagePanelBounds = next
                    updatePanelDockPreview(WorkspacePanelKind.PAGES, next, deltaXDp, deltaYDp)
                },
                onPanelDragEnd = { finishPanelDrag(WorkspacePanelKind.PAGES, pagePanelBounds, ::persistPagePanel) },
                onPanelDragCancel = { cancelPanelDrag(WorkspacePanelKind.PAGES) { pagePanelBounds = it } },
            )
        }
        if (layersOpen && settings.layerPanelDock == WorkspacePanelDock.FREE && showPanel(WorkspacePanelKind.LAYERS, WorkspacePanelDock.FREE)) FloatingWorkspacePanel(
            bounds = visibleLayerPanelBounds,
            mode = settings.layerPanelMode,
            locked = settings.layerPanelLocked,
            zIndex = if (activeFloatingPanel == "layers") 6f else 4f,
            resizeDescription = tr("Resize layers panel", "调整图层面板大小"),
            onActivate = { activeFloatingPanel = "layers" },
            onResize = { dx, dy ->
                activeFloatingPanel = "layers"
                layerPanelBounds = resizeFloatingPanel(WorkspacePanelKind.LAYERS, layerPanelBounds, dx / editorDensity.density, dy / editorDensity.density)
            },
            onResizeEnd = { if (freePanelsStacked) persistFloatingPanels(freePanelTabs) else persistLayerPanel(layerPanelBounds) },
            tabs = if (freePanelsStacked) freePanelTabs else emptyList(),
            activeTab = freeActivePanel,
            onTab = ::selectFloatingPanel,
            onMoveTab = ::movePanelTab,
            onSeparateTabs = ::separateFloatingPanels,
        ) {
            LayerPanel(
                page = selectedPage,
                activeLayerId = activeLayerId,
                brushColor = brushColor,
                dock = settings.layerPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.LAYERS, it) },
                onClose = { layersOpen = false; onSettingsChange(settings.copy(showLayerPanel = false)) },
                onColor = { brushColor = it },
                onSelect = { activeLayerId = it },
                onAdd = { onAddLayer(document.id, selectedPage.id, "Layer") },
                onAddFolder = { onAddLayerFolder(document.id, selectedPage.id) },
                onRename = { layerId, name -> onRenameLayer(document.id, selectedPage.id, layerId, name) },
                onVisibility = { onToggleLayerVisibility(document.id, selectedPage.id, it) },
                onLock = { onToggleLayerLock(document.id, selectedPage.id, it) },
                onOpacity = { layerId, value -> onSetLayerOpacity(document.id, selectedPage.id, layerId, value) },
                onBlendMode = { layerId, mode -> onSetLayerBlendMode(document.id, selectedPage.id, layerId, mode) },
                onMoveToFolder = { layerId, folderId -> onMoveLayerToFolder(document.id, selectedPage.id, layerId, folderId) },
                onToggleFolderCollapsed = { onToggleLayerFolderCollapsed(document.id, selectedPage.id, it) },
                onMove = { layerId, delta -> onMoveLayer(document.id, selectedPage.id, layerId, delta) },
                onDuplicate = { layerId -> onDuplicateLayer(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onMergeDown = { layerId -> onMergeLayerDown(document.id, selectedPage.id, layerId)?.let { activeLayerId = it } },
                onDelete = { onDeleteLayer(document.id, selectedPage.id, it) },
                panelMode = settings.layerPanelMode,
                panelLocked = settings.layerPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(layerPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(layerPanelLocked = it)) },
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7), RoundedCornerShape(12.dp)).padding(10.dp),
                onPanelDragStart = { activeFloatingPanel = "layers"; beginPanelDrag(WorkspacePanelKind.LAYERS, layerPanelBounds) },
                onPanelDrag = { dx, dy ->
                    activeFloatingPanel = "layers"
                    val deltaXDp = dx / editorDensity.density
                    val deltaYDp = dy / editorDensity.density
                    val next = moveFloatingPanel(WorkspacePanelKind.LAYERS, layerPanelBounds, deltaXDp, deltaYDp)
                    layerPanelBounds = next
                    updatePanelDockPreview(WorkspacePanelKind.LAYERS, next, deltaXDp, deltaYDp)
                },
                onPanelDragEnd = { finishPanelDrag(WorkspacePanelKind.LAYERS, layerPanelBounds, ::persistLayerPanel) },
                onPanelDragCancel = { cancelPanelDrag(WorkspacePanelKind.LAYERS) { layerPanelBounds = it } },
            )
        }
        if (brushesOpen && settings.brushPanelDock == WorkspacePanelDock.FREE && showPanel(WorkspacePanelKind.BRUSHES, WorkspacePanelDock.FREE)) FloatingWorkspacePanel(
            bounds = visibleBrushPanelBounds,
            mode = settings.brushPanelMode,
            locked = settings.brushPanelLocked,
            zIndex = if (activeFloatingPanel == "brushes") 6f else 5f,
            resizeDescription = tr("Resize brushes panel", "调整笔刷面板大小"),
            onActivate = { activeFloatingPanel = "brushes" },
            onResize = { dx, dy ->
                activeFloatingPanel = "brushes"
                brushPanelBounds = resizeFloatingPanel(WorkspacePanelKind.BRUSHES, brushPanelBounds, dx / editorDensity.density, dy / editorDensity.density)
            },
            onResizeEnd = { if (freePanelsStacked) persistFloatingPanels(freePanelTabs) else persistBrushPanel(brushPanelBounds) },
            tabs = if (freePanelsStacked) freePanelTabs else emptyList(),
            activeTab = freeActivePanel,
            onTab = ::selectFloatingPanel,
            onMoveTab = ::movePanelTab,
            onSeparateTabs = ::separateFloatingPanels,
        ) {
            BrushPanel(
                brushes = brushes,
                registeredGroups = brushGroups,
                selectedBrushId = selectedBrushId,
                settings = settings,
                dock = settings.brushPanelDock,
                onDock = { changePanelDock(WorkspacePanelKind.BRUSHES, it) },
                onClose = { if (brushesOpen) onToggleBrushes() },
                onBrushSelected = { brush ->
                    exitCanvasNavigation()
                    val usedBrush = brush.copy(lastUsedAt = System.currentTimeMillis())
                    onUpdateBrush(usedBrush)
                    selectedBrushId = usedBrush.id
                    activeBrush = usedBrush
                    brushSize = usedBrush.defaultSize
                    brushOpacity = usedBrush.defaultOpacity
                    brushColor = AndroidColor.rgb(17, 17, 17)
                    val chosenTool = if (BrushRoleClassifier.isEraser(usedBrush)) EditorTool.ERASER else EditorTool.PEN
                    tool = chosenTool
                    canvasView?.apply { this.tool = chosenTool; inkOpacity = 1f; brushId = usedBrush.id; baseWidth = usedBrush.defaultSize; inkColor = brushColor }
                },
                onImportBrushes = onImportBrushes,
                onExportBrush = onExportBrush,
                onUpdateBrush = { updated -> onUpdateBrush(updated); if (activeBrush?.id == updated.id) { activeBrush = updated; brushSize = updated.defaultSize } },
                onReplaceBrushes = { updated ->
                    onReplaceBrushes(updated)
                    activeBrush = activeBrush?.let { current -> updated.firstOrNull { it.id == current.id } }
                    if (activeBrush == null) selectedBrushId = null
                },
                onReplaceGroups = onReplaceBrushGroups,
                onDeleteBrush = { brushId -> onDeleteBrush(brushId); if (activeBrush?.id == brushId) { activeBrush = null; selectedBrushId = null } },
                onReorderBrush = onReorderBrush,
                panelMode = settings.brushPanelMode,
                panelLocked = settings.brushPanelLocked,
                onPanelMode = { onSettingsChange(settings.copy(brushPanelMode = it)) },
                onPanelLocked = { onSettingsChange(settings.copy(brushPanelLocked = it)) },
                modifier = Modifier.fillMaxSize().background(Color(0xFFF7F7F7), RoundedCornerShape(12.dp)).padding(12.dp),
                onPanelDragStart = { activeFloatingPanel = "brushes"; beginPanelDrag(WorkspacePanelKind.BRUSHES, brushPanelBounds) },
                onPanelDrag = { dx, dy ->
                    activeFloatingPanel = "brushes"
                    val deltaXDp = dx / editorDensity.density
                    val deltaYDp = dy / editorDensity.density
                    val next = moveFloatingPanel(WorkspacePanelKind.BRUSHES, brushPanelBounds, deltaXDp, deltaYDp)
                    brushPanelBounds = next
                    updatePanelDockPreview(WorkspacePanelKind.BRUSHES, next, deltaXDp, deltaYDp)
                },
                onPanelDragEnd = { finishPanelDrag(WorkspacePanelKind.BRUSHES, brushPanelBounds, ::persistBrushPanel) },
                onPanelDragCancel = { cancelPanelDrag(WorkspacePanelKind.BRUSHES) { brushPanelBounds = it } },
            )
        }
        }
    }
    if (saveWorkspaceOpen) {
        val normalizedName = CustomWorkspaces.normalizeName(newWorkspaceName)
        val atCapacity = settings.customWorkspaces.size >= CustomWorkspaces.MAX_COUNT
        AlertDialog(
            onDismissRequest = { saveWorkspaceOpen = false },
            title = { Text(tr("Save workspace", "保存工作区")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        tr(
                            "The current layout and its matching landscape or portrait layout are saved together.",
                            "当前布局及其对应的横屏或竖屏布局会一起保存。",
                        ),
                        color = MutedInk,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = newWorkspaceName,
                        onValueChange = { newWorkspaceName = it.take(CustomWorkspaces.MAX_NAME_CHARS) },
                        label = { Text(tr("Workspace name", "工作区名称")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (atCapacity) Text(tr("The custom workspace library is full.", "自定义工作区数量已达上限。"), color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    enabled = normalizedName.isNotBlank() && !atCapacity,
                    onClick = {
                        val uniqueName = CustomWorkspaces.uniqueName(normalizedName, settings.customWorkspaces)
                        val profile = CustomWorkspaces.capture(UUID.randomUUID().toString(), uniqueName, settings, workspaceOrientation)
                        replaceCustomWorkspaces(settings.customWorkspaces + profile)
                        saveWorkspaceOpen = false
                    },
                ) { Text(tr("Save", "保存")) }
            },
            dismissButton = { TextButton(onClick = { saveWorkspaceOpen = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
    if (manageWorkspacesOpen) CustomWorkspaceManagerDialog(
        profiles = settings.customWorkspaces,
        onApply = ::applyCustomWorkspace,
        onUpdate = { profile ->
            val captured = CustomWorkspaces.capture(profile.id, profile.name, settings, workspaceOrientation)
            replaceCustomWorkspaces(settings.customWorkspaces.map { if (it.id == profile.id) captured else it })
        },
        onRename = { profile, requested ->
            val others = settings.customWorkspaces.filterNot { it.id == profile.id }
            val renamed = profile.copy(name = CustomWorkspaces.uniqueName(requested, others))
            replaceCustomWorkspaces(settings.customWorkspaces.map { if (it.id == profile.id) renamed else it })
        },
        onDuplicate = { profile ->
            if (settings.customWorkspaces.size < CustomWorkspaces.MAX_COUNT) {
                val duplicate = profile.copy(
                    id = UUID.randomUUID().toString(),
                    name = CustomWorkspaces.uniqueName("${profile.name} $workspaceCopySuffix", settings.customWorkspaces),
                )
                replaceCustomWorkspaces(settings.customWorkspaces + duplicate)
            }
        },
        onExport = onExportWorkspace,
        onDelete = { profile -> replaceCustomWorkspaces(settings.customWorkspaces.filterNot { it.id == profile.id }) },
        onImport = { manageWorkspacesOpen = false; onImportWorkspace() },
        onDismiss = { manageWorkspacesOpen = false },
    )
    val defaultStickyText = tr("New note", "新便签")
    if (textInsertOpen) AlertDialog(
        onDismissRequest = { textInsertOpen = false },
        title = { Text(tr("Insert text", "插入文本")) },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 3, label = { Text(tr("Text", "文本")) }) },
        confirmButton = { Button(onClick = { if (insertText.isNotBlank()) onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Text(x = .18f, y = .2f, text = insertText.trim()), selectedPage.crop)); textInsertOpen = false; tool = EditorTool.SELECT }) { Text(tr("Insert", "插入")) } },
        dismissButton = { TextButton(onClick = { textInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (stickyInsertOpen) AlertDialog(
        onDismissRequest = { stickyInsertOpen = false },
        title = { Text(tr("New sticky note", "新建便签")) },
        text = { OutlinedTextField(insertText, { insertText = it }, minLines = 4, label = { Text(tr("Note", "便签")) }) },
        confirmButton = { Button(onClick = { onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Sticky(x = .55f, y = .25f, text = insertText.trim().ifBlank { defaultStickyText }), selectedPage.crop)); stickyInsertOpen = false; tool = EditorTool.SELECT }) { Text(tr("Insert", "插入")) } },
        dismissButton = { TextButton(onClick = { stickyInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (linkInsertOpen) {
        val normalizedLink = LinkTarget.normalize(insertLinkUrl)
        AlertDialog(
            onDismissRequest = { linkInsertOpen = false },
            title = { Text(tr("Insert link", "插入链接")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(insertLinkTitle, { insertLinkTitle = it }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(insertLinkUrl, { insertLinkUrl = it }, label = { Text(tr("Web, email, or phone address", "网页、邮箱或电话号码")) }, placeholder = { Text("https://example.com") }, isError = insertLinkUrl.isNotBlank() && normalizedLink == null, supportingText = { Text(if (insertLinkUrl.isNotBlank() && normalizedLink == null) tr("Use a valid http, https, mailto, or tel address.", "请输入有效的 http、https、mailto 或 tel 地址。") else tr("Addresses without a scheme open with HTTPS.", "未填写协议的地址将通过 HTTPS 打开。")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { Button(enabled = normalizedLink != null, onClick = {
                val url = normalizedLink ?: return@Button
                val title = insertLinkTitle.trim().ifBlank { LinkTarget.displayDestination(url, 48) }
                onElement(document.id, selectedPage.id, PageCropTransform.unproject(PageElement.Link(x = .18f, y = .2f, title = title, url = url), selectedPage.crop))
                linkInsertOpen = false
                tool = EditorTool.SELECT
            }) { Text(tr("Insert", "插入")) } },
            dismissButton = { TextButton(onClick = { linkInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
    if (shapeInsertOpen) AlertDialog(
        onDismissRequest = { shapeInsertOpen = false },
        title = { Text(tr("Draw shape", "绘制形状")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(tr("Choose a shape, then drag on the page with the pen. Lines and arrows snap to nearby 45° angles; Freeform follows the pen and closes the polygon automatically.", "选择形状后用笔在页面上拖动。直线和箭头会吸附到邻近的 45° 角；自由形状会跟随笔迹并自动闭合。"), color = MutedInk, fontSize = 12.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { FilterChip(selected = insertShape == kind, onClick = { insertShape = kind }, label = { Text(localizedShapeKind(kind)) }) } } } } },
        confirmButton = { Button(onClick = { shapeInsertOpen = false; tool = EditorTool.SHAPE; canvasView?.tool = EditorTool.SHAPE }) { Text(tr("Draw", "绘制")) } },
        dismissButton = { TextButton(onClick = { shapeInsertOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    if (audioOpen) AlertDialog(
        onDismissRequest = { if (recording) audioRecorder.cancel(); recording = false; audioOpen = false },
        title = { Text(if (recording) tr("Recording audio…", "正在录音…") else tr("Audio note", "音频笔记")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(audioTitle, { audioTitle = it }, label = { Text(tr("Title", "标题")) }, singleLine = true, enabled = !recording); Text(if (recording) tr("Recording locally. Tap Stop and insert when finished.", "正在本机录音。完成后点按“停止并插入”。") else tr("Audio remains on this device and is included in editable Inkloom backups.", "音频保存在此设备，并会包含在可编辑的 Inkloom 备份中。"), color = MutedInk) } },
        confirmButton = {
            if (!recording) Button(onClick = { runCatching { audioRecorder.start() }.onSuccess { recording = true }.onFailure { audioError = it.message } }) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text(tr("Start", "开始")) }
            else Button(onClick = { runCatching { audioRecorder.stop(audioTitle) }.onSuccess { marker -> onElement(document.id, selectedPage.id, marker); recording = false; audioOpen = false; tool = EditorTool.SELECT }.onFailure { audioError = it.message; recording = false } }) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text(tr("Stop and insert", "停止并插入")) }
        },
        dismissButton = { TextButton(onClick = { if (recording) audioRecorder.cancel(); recording = false; audioOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
    audioError?.let { message -> AlertDialog(onDismissRequest = { audioError = null }, title = { Text(tr("Audio issue", "音频问题")) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { audioError = null }) { Text(tr("OK", "确定")) } }) }
    if (outlineOpen) {
        val visibleOutlinePages = PageOutline.visiblePages(document.pages)
        AlertDialog(
            onDismissRequest = { outlineOpen = false },
            title = { Text(tr("Page outline", "页面大纲")) },
            text = {
                LazyColumn(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visibleOutlinePages, key = { it.id }) { page ->
                        val index = document.pages.indexOf(page)
                        val hasChildren = PageOutline.hasChildren(document.pages, index)
                        ListItem(
                            headlineContent = { Text(page.title, maxLines = 1, fontWeight = if (page.outlineLevel == 0) FontWeight.SemiBold else FontWeight.Normal) },
                            supportingContent = { Text(listOfNotNull(page.customNumber, page.tags.takeIf { it.isNotEmpty() }?.joinToString(" · ")).joinToString(" · ").ifBlank { tr("Page ${index + 1}", "第 ${index + 1} 页") }, color = MutedInk, maxLines = 1) },
                            leadingContent = { if (page.bookmarked) Icon(Icons.Default.Bookmark, tr("Bookmarked", "已收藏"), tint = Sage) else Text((index + 1).toString(), color = MutedInk) },
                            trailingContent = { if (hasChildren) IconButton(onClick = { onTogglePageOutlineCollapsed(document.id, page.id) }) { Icon(if (page.outlineCollapsed) Icons.Default.ArrowRight else Icons.Default.ArrowDropDown, if (page.outlineCollapsed) tr("Expand", "展开") else tr("Collapse", "折叠")) } },
                            modifier = Modifier.padding(start = (page.outlineLevel * 18).dp).clickable { selectPageWithViewport(page.id); outlineOpen = false },
                            colors = ListItemDefaults.colors(containerColor = if (page.id == selectedPage.id) SageContainer else Color.Transparent),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { outlineOpen = false }) { Text(tr("Done", "完成")) } },
        )
    }
    if (searchOpen) {
        val results = if (documentQuery.isBlank()) emptyList() else document.pages.mapNotNull { page -> PageSearch.find(page, documentQuery)?.let { page to it } }
        val unindexedPdfPages = document.pages.count { it.background is PageBackground.Pdf && it.pdfTextIndexVersion == 0 }
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text(tr("Search this note", "搜索此笔记")) },
            text = { Column(Modifier.widthIn(min = 340.dp, max = 560.dp)) {
                OutlinedTextField(documentQuery, { documentQuery = it; searchFocus = null }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Page, note, or PDF text", "页面、笔记或 PDF 文字")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (unindexedPdfPages > 0) Surface(Modifier.fillMaxWidth().padding(top = 10.dp), color = Workspace, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (pdfIndexing) tr("Indexing PDF text…", "正在索引 PDF 文字…") else tr("$unindexedPdfPages PDF pages need a text index.", "$unindexedPdfPages 个 PDF 页面需要建立文字索引。"), Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
                        if (pdfIndexing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = { onIndexPdfText(document.id) }) { Text(tr("Index now", "立即索引")) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (documentQuery.isNotBlank() && results.isEmpty()) Text(tr("No matching page titles, tags, object text, or indexed PDF text.", "没有匹配的页面标题、标签、对象文字或已索引 PDF 文字。"), color = MutedInk)
                else LazyColumn(Modifier.heightIn(max = 340.dp)) { items(results, key = { (page, _) -> page.id }) { (page, hit) ->
                    ListItem(
                        headlineContent = { Text(page.title) },
                        supportingContent = { Text(hit.snippet, maxLines = 2) },
                        leadingContent = { Text(when (hit.source) { PageSearchSource.PDF -> "PDF"; PageSearchSource.OBJECT -> "OBJ"; PageSearchSource.PAGE -> "PAGE" }, color = Sage, fontSize = 9.sp) },
                        modifier = Modifier.clickable { searchFocus = hit; selectPageWithViewport(page.id); searchOpen = false },
                    )
                } }
            } },
            confirmButton = { TextButton(onClick = { searchOpen = false }) { Text(tr("Done", "完成")) } },
        )
    }
    if (importError != null) AlertDialog(onDismissRequest = onDismissError, confirmButton = { TextButton(onClick = onDismissError) { Text(tr("OK", "确定")) } }, title = { Text(tr("Import issue", "导入问题")) }, text = { Text(importError) })
}

@Composable
private fun CustomWorkspaceManagerDialog(
    profiles: List<CustomWorkspaceProfile>,
    onApply: (CustomWorkspaceProfile) -> Unit,
    onUpdate: (CustomWorkspaceProfile) -> Unit,
    onRename: (CustomWorkspaceProfile, String) -> Unit,
    onDuplicate: (CustomWorkspaceProfile) -> Unit,
    onExport: (CustomWorkspaceProfile) -> Unit,
    onDelete: (CustomWorkspaceProfile) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var menuForId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<CustomWorkspaceProfile?>(null) }
    var renameText by remember { mutableStateOf("") }
    val editing = renameTarget
    if (editing != null) {
        val normalizedName = CustomWorkspaces.normalizeName(renameText)
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(tr("Rename workspace", "重命名工作区")) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(CustomWorkspaces.MAX_NAME_CHARS) },
                    label = { Text(tr("Workspace name", "工作区名称")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(enabled = normalizedName.isNotBlank(), onClick = {
                    onRename(editing, normalizedName)
                    renameTarget = null
                }) { Text(tr("Rename", "重命名")) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(tr("Cancel", "取消")) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Custom workspaces", "自定义工作区")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 380.dp, max = 620.dp).heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Import workspace file", "导入工作区文件"))
                    }
                }
                if (profiles.isEmpty()) item {
                    Text(tr("No custom workspaces yet. Save the current layout from the workspace menu.", "还没有自定义工作区。请从工作区菜单保存当前布局。"), color = MutedInk, modifier = Modifier.padding(vertical = 18.dp))
                }
                items(profiles, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name, maxLines = 1) },
                        supportingContent = { Text(tr("Landscape + portrait", "横屏 + 竖屏"), color = MutedInk, fontSize = 10.sp) },
                        leadingContent = { Icon(Icons.Outlined.SpaceDashboard, null) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { onApply(profile) }) { Text(tr("Apply", "应用")) }
                                Box {
                                    IconButton(onClick = { menuForId = profile.id }) { Icon(Icons.Outlined.MoreVert, tr("Manage ${profile.name}", "管理 ${profile.name}")) }
                                    DropdownMenu(expanded = menuForId == profile.id, onDismissRequest = { menuForId = null }) {
                                        DropdownMenuItem(
                                            text = { Text(tr("Update from current layout", "用当前布局更新")) },
                                            leadingIcon = { Icon(Icons.Outlined.Save, null) },
                                            onClick = { menuForId = null; onUpdate(profile) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Rename", "重命名")) },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                            onClick = { menuForId = null; renameText = profile.name; renameTarget = profile },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Duplicate", "复制")) },
                                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                            enabled = profiles.size < CustomWorkspaces.MAX_COUNT,
                                            onClick = { menuForId = null; onDuplicate(profile) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Export", "导出")) },
                                            leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) },
                                            onClick = { menuForId = null; onExport(profile) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr("Delete", "删除")) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                            onClick = { menuForId = null; onDelete(profile) },
                                        )
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Workspace),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

@Composable
private fun DockedPanelTabStrip(
    dock: WorkspacePanelDock,
    panels: List<WorkspacePanelKind>,
    active: WorkspacePanelKind?,
    stacked: Boolean,
    onSelect: (WorkspacePanelKind) -> Unit,
    onMove: (WorkspacePanelKind, Int) -> Unit,
    onStacked: (Boolean) -> Unit,
) {
    @Composable fun title(panel: WorkspacePanelKind): String = when (panel) {
        WorkspacePanelKind.PAGES -> tr("Pages", "页面")
        WorkspacePanelKind.BRUSHES -> tr("Brushes", "笔刷")
        WorkspacePanelKind.LAYERS -> tr("Layers", "图层")
    }
    fun icon(panel: WorkspacePanelKind): ImageVector = when (panel) {
        WorkspacePanelKind.PAGES -> Icons.Outlined.ViewCarousel
        WorkspacePanelKind.BRUSHES -> Icons.Outlined.Brush
        WorkspacePanelKind.LAYERS -> Icons.Outlined.Layers
    }
    val dockName = when (dock) {
        WorkspacePanelDock.LEFT -> tr("left", "左侧")
        WorkspacePanelDock.RIGHT -> tr("right", "右侧")
        WorkspacePanelDock.BOTTOM -> tr("bottom", "底部")
        WorkspacePanelDock.FREE -> tr("floating", "浮动")
    }
    val stripDescription = tr("$dockName panel tabs", "$dockName 面板标签")
    Surface(
        Modifier.width(58.dp).fillMaxHeight().semantics {
            contentDescription = stripDescription
        },
        color = Color(0xFFF1F1F1),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxSize().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!stacked) {
                IconButton(
                    onClick = { onStacked(true) },
                    modifier = Modifier.size(50.dp),
                ) { Icon(Icons.Outlined.ViewAgenda, tr("Stack panels in $dockName dock", "堆叠$dockName 面板")) }
                Text(tr("Stack", "堆叠"), fontSize = 9.sp, color = MutedInk)
            } else {
                panels.forEach { panel ->
                    var dragDistance by remember(panel, panels) { mutableFloatStateOf(0f) }
                    val selected = active == panel
                    val panelTitle = title(panel)
                    val tabDescription = tr("$panelTitle tab. Drag to reorder.", "$panelTitle 标签，可拖动排序")
                    Column(
                        Modifier
                            .width(52.dp)
                            .height(62.dp)
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Ink else Color.Transparent)
                            .semantics { contentDescription = tabDescription }
                            .clickable { onSelect(panel) }
                            .pointerInput(panel, panels) {
                                detectDragGestures(
                                    onDragStart = { dragDistance = 0f },
                                    onDragCancel = { dragDistance = 0f },
                                    onDragEnd = {
                                        when {
                                            dragDistance < -20f -> onMove(panel, -1)
                                            dragDistance > 20f -> onMove(panel, 1)
                                        }
                                        dragDistance = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon(panel), null, Modifier.size(20.dp), tint = if (selected) Color.White else Ink)
                        Text(panelTitle, fontSize = 9.sp, maxLines = 1, color = if (selected) Color.White else Ink)
                    }
                }
                IconButton(onClick = { onStacked(false) }, modifier = Modifier.size(50.dp)) {
                    Icon(Icons.Outlined.CallSplit, tr("Separate $dockName panel tabs", "拆分$dockName 面板标签"))
                }
            }
        }
    }
}

@Composable
private fun WorkspacePanelDockMenu(
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    mode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    locked: Boolean = false,
    onMode: (WorkspacePanelMode) -> Unit = {},
    onLocked: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(
                if (locked) Icons.Outlined.Lock else when (dock) {
                    WorkspacePanelDock.LEFT -> Icons.Outlined.ChevronLeft
                    WorkspacePanelDock.FREE -> Icons.Outlined.OpenWith
                    WorkspacePanelDock.RIGHT -> Icons.Outlined.ChevronRight
                    WorkspacePanelDock.BOTTOM -> Icons.Outlined.KeyboardArrowDown
                },
                contentDescription,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WorkspacePanelMode.entries.forEach { target ->
                DropdownMenuItem(
                    text = { Text(when (target) {
                        WorkspacePanelMode.EXPANDED -> tr("Expanded", "展开")
                        WorkspacePanelMode.COLLAPSED -> tr("Collapsed", "收起")
                        WorkspacePanelMode.ICON_ONLY -> tr("Icon only", "仅图标")
                    }) },
                    leadingIcon = { Icon(when (target) {
                        WorkspacePanelMode.EXPANDED -> Icons.Outlined.UnfoldMore
                        WorkspacePanelMode.COLLAPSED -> Icons.Outlined.UnfoldLess
                        WorkspacePanelMode.ICON_ONLY -> Icons.Outlined.Apps
                    }, null) },
                    trailingIcon = { if (target == mode) Icon(Icons.Outlined.Check, null) },
                    onClick = { expanded = false; onMode(target) },
                )
            }
            DropdownMenuItem(
                text = { Text(if (locked) tr("Unlock position and size", "解锁位置与尺寸") else tr("Lock position and size", "锁定位置与尺寸")) },
                leadingIcon = { Icon(if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) },
                onClick = { expanded = false; onLocked(!locked) },
            )
            HorizontalDivider()
            WorkspacePanelDock.entries.forEach { target ->
                DropdownMenuItem(
                    text = {
                        Text(when (target) {
                            WorkspacePanelDock.LEFT -> tr("Dock left", "停靠左侧")
                            WorkspacePanelDock.FREE -> tr("Float", "自由浮动")
                            WorkspacePanelDock.RIGHT -> tr("Dock right", "停靠右侧")
                            WorkspacePanelDock.BOTTOM -> tr("Dock bottom", "停靠底部")
                        })
                    },
                    leadingIcon = {
                        Icon(
                            when (target) {
                                WorkspacePanelDock.LEFT -> Icons.Outlined.ChevronLeft
                                WorkspacePanelDock.FREE -> Icons.Outlined.OpenWith
                                WorkspacePanelDock.RIGHT -> Icons.Outlined.ChevronRight
                                WorkspacePanelDock.BOTTOM -> Icons.Outlined.KeyboardArrowDown
                            },
                            null,
                        )
                    },
                    trailingIcon = { if (target == dock) Icon(Icons.Outlined.Check, null) },
                    enabled = !locked,
                    onClick = { expanded = false; onDock(target) },
                )
            }
        }
    }
}

@Composable
private fun CompactWorkspacePanel(
    title: String,
    icon: ImageVector,
    dock: WorkspacePanelDock,
    mode: WorkspacePanelMode,
    locked: Boolean,
    onMode: (WorkspacePanelMode) -> Unit,
    onLocked: (Boolean) -> Unit,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    dockDescription: String,
    modifier: Modifier,
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    val compactDragDescription = tr("Drag $title panel", "拖动$title 面板")
    val draggableModifier = modifier.then(if (!locked) {
        Modifier
            .semantics { contentDescription = compactDragDescription }
            .pointerInput(dock, mode) {
                detectDragGestures(
                    onDragStart = { currentPanelDragStart() },
                    onDragEnd = { currentPanelDragEnd() },
                    onDragCancel = { currentPanelDragCancel() },
                ) { change, amount ->
                    change.consume()
                    currentPanelDrag(amount.x, amount.y)
                }
            }
    } else Modifier)
    Surface(draggableModifier, color = Color(0xFFF7F7F7), border = CardDefaults.outlinedCardBorder()) {
        if (mode == WorkspacePanelMode.ICON_ONLY && dock == WorkspacePanelDock.BOTTOM) {
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMode(WorkspacePanelMode.EXPANDED) }, modifier = Modifier.size(38.dp)) { Icon(icon, tr("Expand $title panel", "展开$title 面板")) }
                Spacer(Modifier.weight(1f))
                if (locked) Icon(Icons.Outlined.Lock, tr("Panel locked", "面板已锁定"), Modifier.size(15.dp), tint = MutedInk)
                WorkspacePanelDockMenu(dock, onDock, dockDescription, Modifier.size(38.dp), mode, locked, onMode, onLocked)
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Close, tr("Close $title panel", "关闭$title 面板")) }
            }
        } else if (mode == WorkspacePanelMode.ICON_ONLY) {
            Column(Modifier.fillMaxSize().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { onMode(WorkspacePanelMode.EXPANDED) }) { Icon(icon, tr("Expand $title panel", "展开$title 面板")) }
                WorkspacePanelDockMenu(dock, onDock, dockDescription, Modifier.size(38.dp), mode, locked, onMode, onLocked)
                if (locked) Icon(Icons.Outlined.Lock, tr("Panel locked", "面板已锁定"), Modifier.size(15.dp), tint = MutedInk)
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Close, tr("Close $title panel", "关闭$title 面板")) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMode(WorkspacePanelMode.EXPANDED) }, modifier = Modifier.size(38.dp)) { Icon(icon, tr("Expand $title panel", "展开$title 面板")) }
                Text(title, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold)
                if (locked) Icon(Icons.Outlined.Lock, tr("Panel locked", "面板已锁定"), Modifier.size(15.dp), tint = MutedInk)
                WorkspacePanelDockMenu(dock, onDock, dockDescription, Modifier.size(38.dp), mode, locked, onMode, onLocked)
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) { Icon(Icons.Outlined.Close, tr("Close $title panel", "关闭$title 面板")) }
            }
        }
    }
}

@Composable
private fun WorkspacePanelDockPreview(dock: WorkspacePanelDock, bounds: WorkspacePanelBounds) {
    val density = LocalDensity.current
    val label = when (dock) {
        WorkspacePanelDock.LEFT -> tr("Release to dock left", "松开停靠到左侧")
        WorkspacePanelDock.RIGHT -> tr("Release to dock right", "松开停靠到右侧")
        WorkspacePanelDock.BOTTOM -> tr("Release to dock bottom", "松开停靠到底部")
        WorkspacePanelDock.FREE -> tr("Keep floating", "保持浮动")
    }
    Box(
        Modifier
            .width(bounds.widthDp.dp)
            .height(bounds.heightDp.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(7.5f)
            .background(Color(0x26000000), RoundedCornerShape(12.dp))
            .border(4.dp, Ink, RoundedCornerShape(12.dp))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Ink, shape = RoundedCornerShape(22.dp)) {
            Text(label, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WorkspacePanelFloatPreview(bounds: WorkspacePanelBounds) {
    val density = LocalDensity.current
    val label = tr("Release to float", "松开转为浮动面板")
    Box(
        Modifier
            .width(bounds.widthDp.dp)
            .height(bounds.heightDp.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(7.5f)
            .background(Color(0x26000000), RoundedCornerShape(12.dp))
            .border(4.dp, Ink, RoundedCornerShape(12.dp))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Ink, shape = RoundedCornerShape(22.dp)) {
            Text(label, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WorkspacePanelGroupPreview(bounds: WorkspacePanelBounds, target: WorkspacePanelKind) {
    val density = LocalDensity.current
    val targetName = when (target) {
        WorkspacePanelKind.PAGES -> tr("Pages", "页面")
        WorkspacePanelKind.BRUSHES -> tr("Brushes", "笔刷")
        WorkspacePanelKind.LAYERS -> tr("Layers", "图层")
    }
    val label = tr("Release to group with $targetName", "松开与$targetName 组合")
    Box(
        Modifier
            .width(bounds.widthDp.dp)
            .height(bounds.heightDp.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(7.5f)
            .background(Color(0x26000000), RoundedCornerShape(12.dp))
            .border(4.dp, Ink, RoundedCornerShape(12.dp))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = Ink, shape = RoundedCornerShape(22.dp)) {
            Text(label, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FloatingWorkspacePanel(
    bounds: WorkspacePanelBounds,
    mode: WorkspacePanelMode,
    locked: Boolean,
    zIndex: Float,
    resizeDescription: String,
    onActivate: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    tabs: List<WorkspacePanelKind> = emptyList(),
    activeTab: WorkspacePanelKind? = null,
    onTab: (WorkspacePanelKind) -> Unit = {},
    onMoveTab: (WorkspacePanelKind, Int) -> Unit = { _, _ -> },
    onSeparateTabs: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val currentActivate by rememberUpdatedState(onActivate)
    val currentResize by rememberUpdatedState(onResize)
    val currentResizeEnd by rememberUpdatedState(onResizeEnd)
    val visibleWidth = when (mode) {
        WorkspacePanelMode.EXPANDED -> bounds.widthDp
        WorkspacePanelMode.COLLAPSED -> bounds.widthDp.coerceAtMost(240f)
        WorkspacePanelMode.ICON_ONLY -> 56f
    }
    val visibleHeight = when (mode) {
        WorkspacePanelMode.EXPANDED -> bounds.heightDp
        WorkspacePanelMode.COLLAPSED -> 58f
        WorkspacePanelMode.ICON_ONLY -> 148f
    }
    Box(
        Modifier
            .width(visibleWidth.dp)
            .height(visibleHeight.dp)
            .graphicsLayer(
                translationX = with(density) { bounds.xDp.dp.toPx() },
                translationY = with(density) { bounds.yDp.dp.toPx() },
            )
            .zIndex(zIndex)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .background(Color(0xFFF7F7F7), RoundedCornerShape(12.dp))
            .border(1.dp, OutlineSoft, RoundedCornerShape(12.dp)),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (tabs.size > 1) FloatingPanelTabStrip(tabs, activeTab, locked, onTab, onMoveTab, onSeparateTabs)
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
                if (!locked && mode == WorkspacePanelMode.EXPANDED) Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(30.dp)
                        .clip(RoundedCornerShape(topStart = 10.dp, bottomEnd = 12.dp))
                        .background(Color.White)
                        .semantics { contentDescription = resizeDescription }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { currentActivate() },
                                onDragEnd = { currentResizeEnd() },
                                onDragCancel = { currentResizeEnd() },
                            ) { change, amount ->
                                change.consume()
                                currentResize(amount.x, amount.y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.OpenInFull, null, Modifier.size(15.dp), tint = MutedInk)
                }
            }
        }
    }
}

@Composable
private fun FloatingPanelTabStrip(
    panels: List<WorkspacePanelKind>,
    active: WorkspacePanelKind?,
    locked: Boolean,
    onSelect: (WorkspacePanelKind) -> Unit,
    onMove: (WorkspacePanelKind, Int) -> Unit,
    onSeparate: () -> Unit,
) {
    @Composable fun title(panel: WorkspacePanelKind): String = when (panel) {
        WorkspacePanelKind.PAGES -> tr("Pages", "页面")
        WorkspacePanelKind.BRUSHES -> tr("Brushes", "笔刷")
        WorkspacePanelKind.LAYERS -> tr("Layers", "图层")
    }
    val stripDescription = tr("Floating panel tabs", "浮动面板标签")
    Surface(
        Modifier.fillMaxWidth().height(42.dp).semantics { contentDescription = stripDescription },
        color = Color(0xFFF1F1F1),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            panels.forEach { panel ->
                var dragDistance by remember(panel, panels) { mutableFloatStateOf(0f) }
                val selected = active == panel
                val panelTitle = title(panel)
                val tabDescription = tr("$panelTitle floating tab. Drag to reorder.", "$panelTitle 浮动标签，可拖动排序")
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (selected) Ink else Color.Transparent)
                        .semantics { contentDescription = tabDescription }
                        .clickable { onSelect(panel) }
                        .then(if (!locked) Modifier.pointerInput(panel, panels) {
                            detectDragGestures(
                                onDragStart = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                                onDragEnd = {
                                    when {
                                        dragDistance < -20f -> onMove(panel, -1)
                                        dragDistance > 20f -> onMove(panel, 1)
                                    }
                                    dragDistance = 0f
                                },
                            ) { change, amount -> change.consume(); dragDistance += amount.x }
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(panelTitle, fontSize = 10.sp, maxLines = 1, color = if (selected) Color.White else Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            IconButton(onClick = onSeparate, enabled = !locked, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.CallSplit, tr("Separate floating panel tabs", "拆分浮动面板标签"), Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PageRail(
    document: InkDocument,
    documents: List<InkDocument>,
    selectedPageId: String,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    pageTemplates: PageTemplateLibrary,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onDuplicate: (String, String) -> Unit,
    onDelete: (String, String) -> Unit,
    onMove: (String, String, Int) -> Unit,
    onTransfer: (String, String, String, Boolean) -> Unit,
    onApplyTemplate: (String, String, String) -> Unit,
    onImportTemplates: () -> Unit,
    onToggleTemplateFavorite: (String) -> Unit,
    onSetDefaultTemplate: (String, String?) -> Unit,
    onSaveAsTemplate: (String, String, String) -> PageTemplate?,
    onDeleteTemplate: (String) -> Unit,
    onToggleBookmark: (String, String) -> Unit,
    onOutlineLevel: (String, String, Int) -> Unit,
    onOutlineCollapsed: (String, String) -> Unit,
    onRename: (String, String, String) -> Unit,
    onMetadata: (String, String, String?, List<String>) -> Unit,
    onClearInk: (String, String) -> Unit,
    onCrop: (String, String, PageCrop) -> Unit,
    onRotate: (String, String) -> Unit,
    panelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    panelLocked: Boolean = false,
    onPanelMode: (WorkspacePanelMode) -> Unit = {},
    onPanelLocked: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.width(112.dp).fillMaxHeight().padding(12.dp),
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    if (panelMode != WorkspacePanelMode.EXPANDED) {
        val compactModifier = when (dock) {
            WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> Modifier.width(if (panelMode == WorkspacePanelMode.ICON_ONLY) 56.dp else 176.dp).fillMaxHeight()
            WorkspacePanelDock.FREE -> Modifier.fillMaxSize()
            WorkspacePanelDock.BOTTOM -> modifier
        }
        CompactWorkspacePanel(
            tr("Pages", "页面"), Icons.Outlined.ViewCarousel, dock, panelMode, panelLocked,
            onPanelMode, onPanelLocked, onDock, onClose,
            tr("Change pages panel docking", "更改页面面板停靠方式"), compactModifier,
            onPanelDragStart, onPanelDrag, onPanelDragEnd, onPanelDragCancel,
        )
        return
    }
    val transferTargets = documents.filter { it.id != document.id && it.trashedAt == null }
    val dragDescription = tr("Drag pages panel", "拖动页面面板")
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    var addTemplateOpen by remember(document.id) { mutableStateOf(false) }
    LazyColumn(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Pages", "页面"),
                Modifier.weight(1f).then(
                    if (!panelLocked) Modifier.semantics { contentDescription = dragDescription }.pointerInput(dock) {
                        detectDragGestures(onDragStart = { currentPanelDragStart() }, onDragEnd = { currentPanelDragEnd() }, onDragCancel = { currentPanelDragCancel() }) { change, amount ->
                            change.consume()
                            currentPanelDrag(amount.x, amount.y)
                        }
                    } else Modifier,
                ),
                color = Ink,
                maxLines = 1,
            )
            WorkspacePanelDockMenu(dock, onDock, tr("Change pages panel docking", "更改页面面板停靠方式"), Modifier.size(28.dp), panelMode, panelLocked, onPanelMode, onPanelLocked)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, tr("Close pages panel", "关闭页面面板")) }
            IconButton(onClick = { addTemplateOpen = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, tr("Add page", "添加页面")) }
        }
            if (addTemplateOpen) PageTemplatePickerDialog(
                library = pageTemplates,
                documentId = document.id,
                title = tr("Choose template for new page", "选择新页面模板"),
                currentBackground = null,
                onDismiss = { addTemplateOpen = false },
                onChoose = { template -> onAdd(template.id); addTemplateOpen = false },
                onImport = onImportTemplates,
                onToggleFavorite = onToggleTemplateFavorite,
                onSetDefault = { onSetDefaultTemplate(document.id, it) },
                onDelete = onDeleteTemplate,
            )
        }
        items(document.pages, key = { it.id }) { page ->
            val pageIndex = document.pages.indexOf(page)
            val hasOutlineChildren = PageOutline.hasChildren(document.pages, pageIndex)
            var menu by remember { mutableStateOf(false) }
            var renameOpen by remember { mutableStateOf(false) }
            var metadataOpen by remember { mutableStateOf(false) }
            var clearOpen by remember { mutableStateOf(false) }
            var transferOpen by remember { mutableStateOf(false) }
            var templateOpen by remember { mutableStateOf(false) }
            var saveTemplateNameOpen by remember { mutableStateOf(false) }
            var templateName by remember(page.id) { mutableStateOf(page.title) }
            var cropOpen by remember { mutableStateOf(false) }
            var cropDraft by remember(page.crop) { mutableStateOf(page.crop) }
            var title by remember(page.title) { mutableStateOf(page.title) }
            var customNumber by remember(page.customNumber) { mutableStateOf(page.customNumber.orEmpty()) }
            var tags by remember(page.tags) { mutableStateOf(page.tags.joinToString(", ")) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp, 74.dp).background(Color.White, RoundedCornerShape(5.dp)).border(if (page.id == selectedPageId) 2.dp else 1.dp, if (page.id == selectedPageId) Sage else OutlineSoft, RoundedCornerShape(5.dp)).clickable { onSelect(page.id) }.padding(4.dp)) {
                    if (page.background is PageBackground.Pdf) Text("PDF", fontSize = 9.sp, color = Sage)
                    if (page.bookmarked) Icon(Icons.Default.Bookmark, tr("Bookmarked", "已收藏"), tint = Sage, modifier = Modifier.size(16.dp).align(Alignment.TopEnd))
                }
                var pageDragDistance by remember(page.id) { mutableFloatStateOf(0f) }
                Row(Modifier.width(78.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        tr("Drag to reorder page", "拖动调整页面顺序"),
                        Modifier.size(22.dp).pointerInput(page.id) {
                            detectDragGestures(
                                onDragStart = { pageDragDistance = 0f },
                                onDragEnd = {
                                    val delta = (pageDragDistance / 52.dp.toPx()).toInt()
                                    if (delta != 0) onMove(document.id, page.id, delta)
                                    pageDragDistance = 0f
                                },
                                onDragCancel = { pageDragDistance = 0f },
                            ) { change, amount -> change.consume(); pageDragDistance += amount.y }
                        },
                        tint = MutedInk,
                    )
                    Text(page.customNumber ?: (document.pages.indexOf(page) + 1).toString(), color = if (page.id == selectedPageId) Sage else MutedInk, modifier = Modifier.weight(1f).clip(RoundedCornerShape(5.dp)).clickable { onSelect(page.id) }.padding(vertical = 6.dp), maxLines = 1)
                    if (page.id == selectedPageId) {
                        Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Workspace).clickable { menu = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.MoreVert, tr("Page menu", "页面菜单"), modifier = Modifier.size(16.dp)) }
                    } else {
                        Spacer(Modifier.size(26.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Duplicate", "复制")) }, onClick = { menu = false; onDuplicate(document.id, page.id) })
                        DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameOpen = true })
                        DropdownMenuItem(text = { Text(tr("Number and tags", "页码与标签")) }, onClick = { menu = false; metadataOpen = true })
                        DropdownMenuItem(text = { Text(tr("Move up", "上移")) }, enabled = document.pages.first().id != page.id, onClick = { menu = false; onMove(document.id, page.id, -1) })
                        DropdownMenuItem(text = { Text(tr("Move down", "下移")) }, enabled = document.pages.last().id != page.id, onClick = { menu = false; onMove(document.id, page.id, 1) })
                        DropdownMenuItem(text = { Text(tr("Move or copy to note", "移动或复制到笔记")) }, enabled = transferTargets.isNotEmpty(), onClick = { menu = false; transferOpen = true })
                        DropdownMenuItem(text = { Text(tr("Increase outline indent", "增加大纲缩进")) }, enabled = pageIndex > 0 && page.outlineLevel < 3 && page.outlineLevel <= document.pages[pageIndex - 1].outlineLevel, onClick = { menu = false; onOutlineLevel(document.id, page.id, 1) })
                        DropdownMenuItem(text = { Text(tr("Decrease outline indent", "减少大纲缩进")) }, enabled = page.outlineLevel > 0, onClick = { menu = false; onOutlineLevel(document.id, page.id, -1) })
                        if (hasOutlineChildren) DropdownMenuItem(text = { Text(if (page.outlineCollapsed) tr("Expand outline children", "展开子页面") else tr("Collapse outline children", "折叠子页面")) }, onClick = { menu = false; onOutlineCollapsed(document.id, page.id) })
                        DropdownMenuItem(text = { Text(tr("Page template", "页面模板")) }, onClick = { menu = false; templateOpen = true })
                        DropdownMenuItem(text = { Text(tr("Crop page", "裁剪页面")) }, onClick = { menu = false; cropDraft = page.crop; cropOpen = true })
                        DropdownMenuItem(text = { Text(tr("Rotate 90° clockwise", "顺时针旋转 90°")) }, onClick = { menu = false; onRotate(document.id, page.id) })
                        DropdownMenuItem(text = { Text(if (page.bookmarked) tr("Remove bookmark", "取消收藏") else tr("Bookmark", "收藏")) }, onClick = { menu = false; onToggleBookmark(document.id, page.id) })
                        DropdownMenuItem(text = { Text(tr("Clear unlocked ink", "清除未锁定墨迹")) }, enabled = page.strokes.any { stroke -> page.layers.firstOrNull { it.id == stroke.layerId }?.locked != true }, onClick = { menu = false; clearOpen = true })
                        DropdownMenuItem(text = { Text(tr("Delete", "删除")) }, enabled = document.pages.size > 1, onClick = { menu = false; onDelete(document.id, page.id) })
                    }
                }
            }
            if (renameOpen) AlertDialog(onDismissRequest = { renameOpen = false }, title = { Text(tr("Rename page", "重命名页面")) }, text = { OutlinedTextField(title, { title = it }, singleLine = true) }, confirmButton = { TextButton(onClick = { onRename(document.id, page.id, title); renameOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { renameOpen = false }) { Text(tr("Cancel", "取消")) } })
            if (metadataOpen) AlertDialog(onDismissRequest = { metadataOpen = false }, title = { Text(tr("Page metadata", "页面信息")) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(customNumber, { customNumber = it }, label = { Text(tr("Custom page number", "自定义页码")) }, singleLine = true); OutlinedTextField(tags, { tags = it }, label = { Text(tr("Tags", "标签")) }, supportingText = { Text(tr("Separate tags with commas", "用逗号分隔标签")) }) } }, confirmButton = { TextButton(onClick = { onMetadata(document.id, page.id, customNumber, tags.split(',')); metadataOpen = false }) { Text(tr("Save", "保存")) } }, dismissButton = { TextButton(onClick = { metadataOpen = false }) { Text(tr("Cancel", "取消")) } })
            if (templateOpen) PageTemplatePickerDialog(
                library = pageTemplates,
                documentId = document.id,
                title = tr("Choose page template", "选择页面模板"),
                currentBackground = page.background,
                onDismiss = { templateOpen = false },
                onChoose = { template -> onApplyTemplate(document.id, page.id, template.id); templateOpen = false },
                onImport = onImportTemplates,
                onToggleFavorite = onToggleTemplateFavorite,
                onSetDefault = { onSetDefaultTemplate(document.id, it) },
                onSaveCurrent = { templateName = page.title; templateOpen = false; saveTemplateNameOpen = true },
                onDelete = onDeleteTemplate,
            )
            if (saveTemplateNameOpen) AlertDialog(
                onDismissRequest = { saveTemplateNameOpen = false },
                title = { Text(tr("Save page as template", "将页面保存为模板")) },
                text = { OutlinedTextField(templateName, { templateName = it }, label = { Text(tr("Template name", "模板名称")) }, singleLine = true) },
                confirmButton = { TextButton(onClick = { onSaveAsTemplate(document.id, page.id, templateName); saveTemplateNameOpen = false }) { Text(tr("Save", "保存")) } },
                dismissButton = { TextButton(onClick = { saveTemplateNameOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
            if (clearOpen) AlertDialog(onDismissRequest = { clearOpen = false }, title = { Text(tr("Clear page ink?", "清除页面墨迹？")) }, text = { Text(tr("All strokes on unlocked layers will be removed. Locked-layer ink is preserved.", "将删除所有未锁定图层上的笔画，锁定图层中的墨迹会保留。")) }, confirmButton = { TextButton(onClick = { onClearInk(document.id, page.id); clearOpen = false }) { Text(tr("Clear", "清除")) } }, dismissButton = { TextButton(onClick = { clearOpen = false }) { Text(tr("Cancel", "取消")) } })
            if (cropOpen) AlertDialog(
                onDismissRequest = { cropOpen = false },
                title = { Text(tr("Crop page", "裁剪页面")) },
                text = {
                    LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { Text(tr("The source PDF/image and all page content remain unchanged. Resetting the crop restores the full page.", "源 PDF、图片及页面内容不会改变；重置裁剪即可恢复完整页面。"), color = MutedInk, fontSize = 12.sp) }
                        item { BrushSettingSlider("${tr("Left", "左")}  ${(cropDraft.left * 100).toInt()}%", cropDraft.left, 0f..0.95f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(left = it)) } }
                        item { BrushSettingSlider("${tr("Top", "上")}  ${(cropDraft.top * 100).toInt()}%", cropDraft.top, 0f..0.95f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(top = it)) } }
                        item { BrushSettingSlider("${tr("Right", "右")}  ${(cropDraft.right * 100).toInt()}%", cropDraft.right, .05f..1f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(right = it)) } }
                        item { BrushSettingSlider("${tr("Bottom", "下")}  ${(cropDraft.bottom * 100).toInt()}%", cropDraft.bottom, .05f..1f) { cropDraft = PageCropTransform.normalized(cropDraft.copy(bottom = it)) } }
                        item { OutlinedButton(onClick = { cropDraft = PageCrop() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset to full page", "重置为完整页面")) } }
                    }
                },
                confirmButton = { Button(onClick = { onCrop(document.id, page.id, cropDraft); cropOpen = false }) { Text(tr("Apply", "应用")) } },
                dismissButton = { TextButton(onClick = { cropOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
            if (transferOpen) AlertDialog(
                onDismissRequest = { transferOpen = false },
                title = { Text(tr("Move or copy page", "移动或复制页面")) },
                text = {
                    LazyColumn(Modifier.widthIn(min = 360.dp, max = 560.dp).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(transferTargets, key = { it.id }) { target ->
                            ListItem(
                                headlineContent = { Text(target.title, maxLines = 1) },
                                supportingContent = { Text(tr("${target.pages.size} pages", "${target.pages.size} 页"), color = MutedInk) },
                                trailingContent = { Row { TextButton(onClick = { onTransfer(document.id, page.id, target.id, false); transferOpen = false }) { Text(tr("Move", "移动")) }; TextButton(onClick = { onTransfer(document.id, page.id, target.id, true); transferOpen = false }) { Text(tr("Copy", "复制")) } } },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { transferOpen = false }) { Text(tr("Cancel", "取消")) } },
            )
        }
    }
}

@Composable
private fun PageTemplatePickerDialog(
    library: PageTemplateLibrary,
    documentId: String,
    title: String,
    currentBackground: PageBackground?,
    onDismiss: () -> Unit,
    onChoose: (PageTemplate) -> Unit,
    onImport: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetDefault: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onSaveCurrent: (() -> Unit)? = null,
) {
    val all = library.allTemplates()
    val ordered = all.filter { it.id in library.favoriteIds } + all.filterNot { it.id in library.favoriteIds }
    val defaultId = library.notebookDefaults[documentId] ?: PageTemplateCatalog.blank.id
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.widthIn(min = 440.dp, max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr(
                        "Choose a reusable paper, image, or PDF background. Existing ink and objects are preserved.",
                        "选择可复用的纸张、图片或 PDF 背景；现有墨迹与对象会完整保留。",
                    ),
                    color = MutedInk,
                    fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(tr("Import image/PDF", "导入图片/PDF")) }
                    if (onSaveCurrent != null) OutlinedButton(onClick = onSaveCurrent) { Icon(Icons.Outlined.BookmarkAdd, null); Spacer(Modifier.width(6.dp)); Text(tr("Save current", "保存当前页")) }
                }
                LazyColumn(Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(ordered, key = { it.id }) { template ->
                        val selected = currentBackground == template.background
                        val builtIn = PageTemplateCatalog.isBuiltIn(template.id)
                        val isDefault = template.id == defaultId
                        val displayName = pageTemplateName(template)
                        val favoriteDescription = tr("Favorite $displayName template", "收藏 $displayName 模板")
                        val defaultDescription = tr("Set $displayName as notebook default", "将 $displayName 设为笔记默认模板")
                        val useDescription = tr("Use $displayName template", "使用 $displayName 模板")
                        ListItem(
                            headlineContent = { Text(displayName, maxLines = 1) },
                            supportingContent = {
                                Text(
                                    "${pageTemplateKind(template)} · ${template.width.toInt()} × ${template.height.toInt()}" + if (isDefault) tr(" · notebook default", " · 笔记默认") else "",
                                    color = MutedInk,
                                    fontSize = 11.sp,
                                )
                            },
                            leadingContent = { PageTemplatePreview(template, selected) },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onToggleFavorite(template.id) }, modifier = Modifier.size(34.dp).semantics { contentDescription = favoriteDescription }) {
                                        Icon(if (template.id in library.favoriteIds) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = Ink)
                                    }
                                    IconButton(onClick = { onSetDefault(if (isDefault) null else template.id) }, modifier = Modifier.size(34.dp).semantics { contentDescription = defaultDescription }) {
                                        Icon(if (isDefault) Icons.Filled.Home else Icons.Outlined.Home, null, tint = Ink)
                                    }
                                    if (!builtIn) IconButton(onClick = { onDelete(template.id) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Outlined.Delete, tr("Delete template", "删除模板"), tint = MutedInk)
                                    }
                                    TextButton(onClick = { onChoose(template) }, modifier = Modifier.semantics { contentDescription = useDescription }) { Text(if (selected) tr("Selected", "已选择") else tr("Use", "使用")) }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = if (selected) Workspace else Color.Transparent),
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun PageTemplatePreview(template: PageTemplate, selected: Boolean) {
    val borderColor = if (selected) Ink else OutlineSoft
    Box(
        Modifier.size(42.dp, 54.dp).background(Color.White, RoundedCornerShape(4.dp)).border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (val background = template.background) {
            is PageBackground.Paper -> Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val guide = Color(0xFFD7D7D7)
                when (background.pattern) {
                    PaperPattern.PLAIN -> Unit
                    PaperPattern.RULED -> { var y = 6f; while (y < size.height) { drawLine(guide, Offset(0f, y), Offset(size.width, y)); y += 7f } }
                    PaperPattern.GRID -> { var x = 5f; while (x < size.width) { drawLine(guide, Offset(x, 0f), Offset(x, size.height)); x += 7f }; var y = 5f; while (y < size.height) { drawLine(guide, Offset(0f, y), Offset(size.width, y)); y += 7f } }
                    PaperPattern.DOT -> { var x = 4f; while (x < size.width) { var y = 4f; while (y < size.height) { drawCircle(guide, .8f, Offset(x, y)); y += 7f }; x += 7f } }
                    PaperPattern.CORNELL -> { drawLine(guide, Offset(size.width * .28f, 0f), Offset(size.width * .28f, size.height * .78f)); drawLine(guide, Offset(0f, size.height * .78f), Offset(size.width, size.height * .78f)) }
                }
            }
            is PageBackground.Image -> Icon(Icons.Outlined.Image, null, tint = MutedInk)
            is PageBackground.Pdf -> Text("PDF", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun pageTemplateName(template: PageTemplate): String = when (template.id) {
    "builtin-plain" -> tr("Blank", "空白")
    "builtin-ruled" -> tr("Ruled", "横线")
    "builtin-grid" -> tr("Grid", "方格")
    "builtin-dot" -> tr("Dot grid", "点阵")
    "builtin-cornell" -> tr("Cornell notes", "康奈尔笔记")
    else -> template.name
}

@Composable
private fun pageTemplateKind(template: PageTemplate): String = when (template.background) {
    is PageBackground.Paper -> tr("Paper", "纸张")
    is PageBackground.Image -> tr("Image", "图片")
    is PageBackground.Pdf -> tr("PDF page", "PDF 页面")
}

@Composable
private fun PaperCanvas(modifier: Modifier, tool: EditorTool, onView: (InkCanvasView) -> Unit, page: InkPage, documentId: String, onStroke: (String, String, InkStroke) -> Unit, onRemoveStroke: (String, String, String) -> Unit, onStrokesReplaced: (String, String, List<String>, List<InkStroke>, List<Int>) -> Unit, onElement: (String, String, PageElement) -> Unit, onRemoveElement: (String, String, String) -> Unit, onDuplicateElement: (String, String, String) -> Unit, selectedElementId: String?, onSelectElement: (String?) -> Unit, assets: DocumentAssetService, settings: AppSettings, onZoom: (Float, Float, Float) -> Unit, onPan: (Float, Float) -> Unit, onRotate: (Float) -> Unit, onUndoGesture: () -> Unit, onRedoGesture: () -> Unit, activeBrush: ImportedBrush?, brushSize: Float, brushOpacity: Float, brushColor: Int, shapeKind: ShapeKind, activeLayerId: String, eraserPreset: EraserPreset, forceNavigation: Boolean, rotateNavigation: Boolean, searchBounds: PdfTextBounds?, onPdfGotoLink: (PdfGotoLink) -> Unit, onInputDiagnostic: (String, Float, Float, Int) -> Unit, onColorSampled: (Int) -> Unit, onBrushSizeAdjusted: (Float) -> Unit, onKeyInputDiagnostic: (KeyInputDiagnostic) -> Unit, onMotionInputDiagnostic: (MotionInputDiagnostic) -> Unit, onShortcutAction: (ShortcutActivation) -> Unit, onRulerGuideChange: (RulerGuideState) -> Unit, onCanvasGuideChange: (CanvasGuideState) -> Unit) {
    val renderedPage = remember(page) { PageCropTransform.project(page) }
    val backgroundVisible = page.layers.firstOrNull { it.role == LayerRole.BACKGROUND }?.visible != false
    val background = remember(page.id, page.background, page.backgroundRotationQuarterTurns, page.crop, backgroundVisible) { if (backgroundVisible) runCatching { assets.renderBackground(page.background, 1200, page.backgroundRotationQuarterTurns, page.crop) }.getOrNull() else null }
    DisposableEffect(background) { onDispose { background?.recycle() } }
    val paperColor = (page.background as? PageBackground.Paper)?.colorArgb?.let(::Color) ?: Color.White
    val visibleLayerIds = page.layers.filter { LayerHierarchy.effectiveVisible(page.layers, it) && it.role != LayerRole.FOLDER }.mapTo(mutableSetOf()) { it.id }
    val lockedLayerIds = page.layers.filter { LayerHierarchy.effectiveLocked(page.layers, it) || it.role == LayerRole.FOLDER }.mapTo(mutableSetOf()) { it.id }
    val layerOpacities = page.layers.associate { it.id to it.opacity }
    val layerBlendModes = page.layers.associate { it.id to it.blendMode }
    val layerOrder = page.layers.mapIndexed { index, layer -> layer.id to index }.toMap()
    val rulerGuide = RulerGuideState(settings.rulerCenterX, settings.rulerCenterY, settings.rulerAngleDegrees, settings.rulerSnapDegrees).normalized()
    val canvasGuide = CanvasGuideState(settings.gridSnapEnabled, settings.gridSpacing, settings.alignmentGuidesEnabled, settings.verticalGuideX, settings.horizontalGuideY).normalized()
    BoxWithConstraints(modifier.shadow(3.dp, RoundedCornerShape(4.dp)).background(paperColor).padding(48.dp)) {
        val density = LocalDensity.current
        val canvasWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val canvasHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        if (backgroundVisible && renderedPage.background is PageBackground.Paper) Canvas(Modifier.fillMaxSize()) {
            when (renderedPage.background.pattern) {
                PaperPattern.PLAIN -> Unit
                PaperPattern.RULED -> { var y = 36f; while (y < size.height) { drawLine(Color(0xFFE3E3E3), Offset(0f, y), Offset(size.width, y)); y += 36f } }
                PaperPattern.GRID -> { var x = 0f; while (x < size.width) { drawLine(Color(0xFFE3E3E3), Offset(x, 0f), Offset(x, size.height)); x += 32f }; var y = 0f; while (y < size.height) { drawLine(Color(0xFFE3E3E3), Offset(0f, y), Offset(size.width, y)); y += 32f } }
                PaperPattern.DOT -> { var x = 8f; while (x < size.width) { var y = 8f; while (y < size.height) { drawCircle(Color(0xFFD9D9D9), 1.4f, Offset(x, y)); y += 28f }; x += 28f } }
                PaperPattern.CORNELL -> {
                    val lineColor = Color(0xFFD4D4D4)
                    val cueX = size.width * .28f
                    val summaryY = size.height * .78f
                    drawLine(lineColor, Offset(cueX, 0f), Offset(cueX, summaryY), strokeWidth = 1.5f)
                    drawLine(lineColor, Offset(0f, summaryY), Offset(size.width, summaryY), strokeWidth = 1.5f)
                    var y = 36f
                    while (y < summaryY) { drawLine(Color(0xFFE7E7E7), Offset(cueX, y), Offset(size.width, y)); y += 36f }
                    y = summaryY + 36f
                    while (y < size.height) { drawLine(Color(0xFFE7E7E7), Offset(0f, y), Offset(size.width, y)); y += 36f }
                }
            }
        }
        AndroidView(
            factory = { context -> InkCanvasView(context).also { view ->
                view.tool = tool
                view.forceNavigation = forceNavigation
                view.rotateNavigation = rotateNavigation
                view.onStrokeCommitted = { onStroke(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onStrokeRemoved = { onRemoveStroke(documentId, page.id, it) }
                view.onStrokesReplaced = { removed, replacements, indices -> onStrokesReplaced(documentId, page.id, removed, replacements.map { PageCropTransform.unproject(it, page.crop) }, indices) }
                view.onViewportScale = onZoom
                view.onViewportPan = onPan
                view.onViewportRotate = onRotate
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onUndoGesture = onUndoGesture
                view.onRedoGesture = onRedoGesture
                view.singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
                view.twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
                view.threeFingerTapRedo = settings.threeFingerTapRedo
                view.twoFingerRotationEnabled = settings.twoFingerRotation
                view.onInputDiagnostic = onInputDiagnostic
                view.onColorSampled = onColorSampled
                view.onBrushSizeAdjusted = onBrushSizeAdjusted
                view.onKeyInputDiagnostic = onKeyInputDiagnostic
                view.onMotionInputDiagnostic = onMotionInputDiagnostic
                view.onShortcutAction = onShortcutAction
                view.rulerEnabled = settings.rulerEnabled
                view.rulerGuide = rulerGuide
                view.onRulerGuideChanged = onRulerGuideChange
                view.canvasGuideState = canvasGuide
                view.onCanvasGuideChanged = onCanvasGuideChange
                view.shortcutBindings = settings.shortcutBindings
                view.shortcutAxisBindings = settings.shortcutAxisBindings
                view.shortcutButtonBindings = settings.shortcutButtonBindings
                view.post { view.requestFocus() }
                onView(view)
            } },
            update = { view ->
                view.tool = tool
                view.forceNavigation = forceNavigation
                view.rotateNavigation = rotateNavigation
                view.setDocumentStrokes(renderedPage.strokes.filter { it.layerId in visibleLayerIds }.sortedBy { layerOrder[it.layerId] ?: Int.MAX_VALUE }, "$documentId:${page.id}")
                view.setProtectedStrokeIds(page.strokes.filter { it.layerId in lockedLayerIds }.mapTo(mutableSetOf()) { it.id })
                view.setProtectedLayerIds(lockedLayerIds)
                view.setPageBackground(background)
                view.activeLayerId = activeLayerId
                view.layerOpacities = layerOpacities
                view.layerBlendModes = layerBlendModes
                view.pagePaperColor = if (renderedPage.background is PageBackground.Paper) (if (backgroundVisible) renderedPage.background.colorArgb.toInt() else AndroidColor.WHITE) else null
                view.pagePaperPattern = (renderedPage.background as? PageBackground.Paper)?.pattern?.takeIf { backgroundVisible }
                view.shapeKind = shapeKind
                view.pageModelWidth = renderedPage.width
                view.pageModelHeight = renderedPage.height
                view.onStrokeCommitted = { onStroke(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onShapeCommitted = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) }
                view.onStrokeRemoved = { onRemoveStroke(documentId, page.id, it) }
                view.onStrokesReplaced = { removed, replacements, indices -> onStrokesReplaced(documentId, page.id, removed, replacements.map { PageCropTransform.unproject(it, page.crop) }, indices) }
                view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, brushColor, eraserPreset)
                view.onViewportScale = onZoom
                view.onViewportPan = onPan
                view.onViewportRotate = onRotate
                view.pdfGotoLinks = renderedPage.pdfGotoLinks
                view.onPdfGotoLink = onPdfGotoLink
                view.onUndoGesture = onUndoGesture
                view.onRedoGesture = onRedoGesture
                view.singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
                view.twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
                view.threeFingerTapRedo = settings.threeFingerTapRedo
                view.twoFingerRotationEnabled = settings.twoFingerRotation
                view.onInputDiagnostic = onInputDiagnostic
                view.onColorSampled = onColorSampled
                view.onBrushSizeAdjusted = onBrushSizeAdjusted
                view.onKeyInputDiagnostic = onKeyInputDiagnostic
                view.onMotionInputDiagnostic = onMotionInputDiagnostic
                view.onShortcutAction = onShortcutAction
                view.rulerEnabled = settings.rulerEnabled
                view.rulerGuide = rulerGuide
                view.onRulerGuideChanged = onRulerGuideChange
                view.canvasGuideState = canvasGuide
                view.onCanvasGuideChanged = onCanvasGuideChange
                view.shortcutBindings = settings.shortcutBindings
                view.shortcutAxisBindings = settings.shortcutAxisBindings
                view.shortcutButtonBindings = settings.shortcutButtonBindings
            },
            modifier = Modifier.fillMaxSize(),
        )
        searchBounds?.let { PageCropTransform.project(it, page.crop) }?.let { bounds ->
            Canvas(Modifier.fillMaxSize()) {
                val topLeft = Offset(bounds.left * size.width, bounds.top * size.height)
                val highlightSize = androidx.compose.ui.geometry.Size((bounds.right - bounds.left) * size.width, (bounds.bottom - bounds.top) * size.height)
                drawRect(Color(0x33111111), topLeft = topLeft, size = highlightSize)
                drawRect(Color(0xFF111111), topLeft = topLeft, size = highlightSize, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            }
        }
        renderedPage.elements.filter { it.layerId in visibleLayerIds }.sortedBy { layerOrder[it.layerId] ?: Int.MAX_VALUE }.forEach { element ->
            ObjectElementView(
                element = element,
                page = renderedPage,
                layerOpacity = layerOpacities[element.layerId] ?: 1f,
                selected = selectedElementId == element.id,
                editable = tool == EditorTool.SELECT && element.layerId !in lockedLayerIds,
                canvasWidth = maxWidth,
                canvasHeight = maxHeight,
                canvasWidthPx = canvasWidthPx,
                canvasHeightPx = canvasHeightPx,
                onSelect = { onSelectElement(element.id) },
                onChange = { onElement(documentId, page.id, PageCropTransform.unproject(it, page.crop)) },
                onDuplicate = { onDuplicateElement(documentId, page.id, element.id) },
                onDelete = { onRemoveElement(documentId, page.id, element.id); onSelectElement(null) },
                settings = settings,
                activeBrush = activeBrush,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                brushColor = brushColor,
                eraserPreset = eraserPreset,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

private fun InkCanvasView.applyBrushConfiguration(
    selectedTool: EditorTool,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    eraserPreset: EraserPreset,
) {
    tool = selectedTool
    fingerDrawingEnabled = settings.fingerDrawing
    singleFingerDoubleTapUndo = settings.singleFingerDoubleTapUndo
    twoFingerDoubleTapUndo = settings.twoFingerDoubleTapUndo
    threeFingerTapRedo = settings.threeFingerTapRedo
    pressureSensitivity = settings.pressureSensitivity
    pressureMinimumInput = settings.pressureMinimumInput
    pressureMaximumInput = settings.pressureMaximumInput
    stabilization = maxOf(settings.stabilization, activeBrush?.stabilization ?: 0f)
    stabilizationBySpeed = activeBrush?.stabilizationBySpeed ?: false
    stabilizationBySpeedMode = activeBrush?.stabilizationBySpeedMode ?: StrokeStabilization.REDUCE_WHEN_FAST
    primaryButtonAction = settings.stylusPrimaryButton
    secondaryButtonAction = settings.stylusSecondaryButton
    primaryButtonTrigger = settings.stylusPrimaryTrigger
    secondaryButtonTrigger = settings.stylusSecondaryTrigger
    selectionShape = settings.selectionShape
    selectionMode = settings.selectionMode
    selectionFilter = settings.selectionFilter
    baseWidth = brushSize
    val importedEraser = activeBrush?.takeIf { selectedTool == EditorTool.ERASER && BrushRoleClassifier.isEraser(it) }
    eraserWidth = if (importedEraser != null) brushSize else settings.eraserSize
    this.localEraser = if (importedEraser != null) false else eraserPreset.splitsStroke
    nonDestructiveEraser = if (importedEraser != null) true else eraserPreset.usesMask
    eraserHardness = importedEraser?.hardness ?: eraserPreset.hardness
    eraserOpacity = importedEraser?.let { brushOpacity * it.flow }?.coerceIn(.01f, 1f) ?: eraserPreset.opacity
    eraserPressureSize = importedEraser?.sizePressureEnabled ?: eraserPreset.pressureSize
    eraserTipAssetPath = importedEraser?.tipPreviewPath
    inkOpacity = brushOpacity
    inkColor = brushColor
    brushId = activeBrush?.id ?: if (selectedTool == EditorTool.HIGHLIGHTER) "system-highlighter" else "system-pen"
    tipAssetPath = activeBrush?.tipPreviewPath
    tipAssetPaths = activeBrush?.tipPreviewPaths.orEmpty()
    dualTipAssetPaths = activeBrush?.dualTipPreviewPaths.orEmpty()
    grainAssetPath = activeBrush?.grainPreviewPath
    grainDensity = activeBrush?.grainDensity ?: 0f
    grainScale = activeBrush?.grainScale ?: 100f
    grainRotationDegrees = activeBrush?.grainRotationDegrees ?: 0f
    grainBrightness = activeBrush?.grainBrightness ?: 0f
    grainContrast = activeBrush?.grainContrast ?: 0f
    grainReversed = activeBrush?.grainReversed ?: false
    grainEmphasized = activeBrush?.grainEmphasized ?: false
    grainOffsetJitter = activeBrush?.grainOffsetJitter ?: false
    grainCompositeMode = activeBrush?.grainCompositeMode ?: CspTextureComposite.MULTIPLY
    brushSpacing = activeBrush?.spacing ?: .12f
    brushSpacingMode = activeBrush?.spacingMode ?: CspBrushGap.FIXED
    brushFlow = activeBrush?.flow ?: 1f
    brushHardness = activeBrush?.hardness ?: 1f
    brushAntiAliasMode = activeBrush?.antiAliasMode ?: CspAntiAlias.MEDIUM
    brushBlur = activeBrush?.blur ?: 0f
    brushBlurUnit = activeBrush?.blurUnit ?: 0
    brushBlurLinkedToSize = activeBrush?.blurLinkedToSize ?: false
    brushThickness = activeBrush?.thickness ?: 1f
    brushThicknessVertical = activeBrush?.thicknessVertical ?: false
    brushRotation = activeBrush?.rotationDegrees ?: 0f
    followStylusOrientation = activeBrush?.followStylusOrientation ?: false
    followStrokeDirection = activeBrush?.followStrokeDirection ?: false
    randomRotationDegrees = activeBrush?.randomRotationDegrees ?: 0f
    strokeRotationJitterDegrees = activeBrush?.strokeRotationJitterDegrees ?: 0f
    stampCount = activeBrush?.stampCount ?: 1
    stampCountJitter = activeBrush?.stampCountJitter ?: 0f
    brushContinuousPlot = activeBrush?.continuousPlot ?: false
    colorJitterHueDegrees = activeBrush?.colorJitterHueDegrees ?: 0f
    colorJitterSaturation = activeBrush?.colorJitterSaturation ?: 0f
    colorJitterValue = activeBrush?.colorJitterValue ?: 0f
    colorJitterTarget = activeBrush?.colorJitterTarget ?: 0
    colorJitterHueDynamics = activeBrush?.colorJitterHueDynamics ?: BrushColorDynamics()
    colorJitterSaturationDynamics = activeBrush?.colorJitterSaturationDynamics ?: BrushColorDynamics()
    colorJitterValueDynamics = activeBrush?.colorJitterValueDynamics ?: BrushColorDynamics()
    brushWatercolor = activeBrush?.watercolor ?: false
    brushColorMix = activeBrush?.colorMix ?: 0f
    colorMixPressureEnabled = activeBrush?.colorMixPressureEnabled ?: false
    colorMixPressureMinimum = activeBrush?.colorMixPressureMinimum ?: 0f
    colorMixPressureCurve = activeBrush?.colorMixPressureCurve.orEmpty()
    colorMixSecondaryDynamics = activeBrush?.colorMixSecondaryDynamics ?: BrushSecondaryDynamics()
    brushPaintMix = activeBrush?.paintMix ?: 0f
    paintMixPressureEnabled = activeBrush?.paintMixPressureEnabled ?: false
    paintMixPressureMinimum = activeBrush?.paintMixPressureMinimum ?: 0f
    paintMixPressureCurve = activeBrush?.paintMixPressureCurve.orEmpty()
    paintMixSecondaryDynamics = activeBrush?.paintMixSecondaryDynamics ?: BrushSecondaryDynamics()
    brushSpray = activeBrush?.spray ?: false
    sprayParticleSize = activeBrush?.sprayParticleSize ?: 100f
    sprayParticleSizeUnit = activeBrush?.sprayParticleSizeUnit ?: 0
    sprayParticleSizeSyncBrushSize = activeBrush?.sprayParticleSizeSyncBrushSize ?: true
    sprayParticleSizePressureEnabled = activeBrush?.sprayParticleSizePressureEnabled ?: false
    sprayParticleSizePressureMinimum = activeBrush?.sprayParticleSizePressureMinimum ?: 0f
    sprayParticleSizePressureCurve = activeBrush?.sprayParticleSizePressureCurve.orEmpty()
    sprayParticleSizeSecondaryDynamics = activeBrush?.sprayParticleSizeSecondaryDynamics ?: BrushSecondaryDynamics()
    sprayDensity = activeBrush?.sprayDensity ?: 1f
    sprayDensityPressureEnabled = activeBrush?.sprayDensityPressureEnabled ?: false
    sprayDensityPressureMinimum = activeBrush?.sprayDensityPressureMinimum ?: 0f
    sprayDensityPressureCurve = activeBrush?.sprayDensityPressureCurve.orEmpty()
    sprayDensitySecondaryDynamics = activeBrush?.sprayDensitySecondaryDynamics ?: BrushSecondaryDynamics()
    sprayBias = activeBrush?.sprayBias ?: 0f
    spraySpread = activeBrush?.spraySpread ?: 1f
    sprayParticleRotationDegrees = activeBrush?.sprayParticleRotationDegrees ?: 0f
    sprayParticleFollowStylusOrientation = activeBrush?.sprayParticleFollowStylusOrientation ?: false
    sprayParticleFollowStrokeDirection = activeBrush?.sprayParticleFollowStrokeDirection ?: false
    sprayParticleRandomRotationDegrees = activeBrush?.sprayParticleRandomRotationDegrees ?: 0f
    brushRibbon = activeBrush?.ribbon ?: false
    brushTipSequenceMode = activeBrush?.tipSequenceMode ?: 0
    tipFlipHorizontalMode = activeBrush?.tipFlipHorizontalMode ?: 0
    tipFlipVerticalMode = activeBrush?.tipFlipVerticalMode ?: 0
    blendTipsWithDarken = activeBrush?.blendTipsWithDarken ?: false
    brushDual = activeBrush?.dualBrush ?: false
    dualSizeScale = activeBrush?.dualSizeScale ?: 1f
    dualSpacing = activeBrush?.dualSpacing ?: .12f
    dualSpacingMode = activeBrush?.dualSpacingMode ?: CspBrushGap.FIXED
    dualFlow = activeBrush?.dualFlow ?: 1f
    dualHardness = activeBrush?.dualHardness ?: 1f
    dualAntiAliasMode = activeBrush?.dualAntiAliasMode ?: CspAntiAlias.MEDIUM
    dualThickness = activeBrush?.dualThickness ?: 1f
    dualThicknessVertical = activeBrush?.dualThicknessVertical ?: false
    dualRotationDegrees = activeBrush?.dualRotationDegrees ?: 0f
    dualFollowStylusOrientation = activeBrush?.dualFollowStylusOrientation ?: false
    dualFollowStrokeDirection = activeBrush?.dualFollowStrokeDirection ?: false
    dualRandomRotationDegrees = activeBrush?.dualRandomRotationDegrees ?: 0f
    dualStrokeRotationJitterDegrees = activeBrush?.dualStrokeRotationJitterDegrees ?: 0f
    dualStampCount = activeBrush?.dualStampCount ?: 1
    dualStampCountJitter = activeBrush?.dualStampCountJitter ?: 0f
    dualRibbon = activeBrush?.dualRibbon ?: false
    dualSpray = activeBrush?.dualSpray ?: false
    dualSprayParticleSize = activeBrush?.dualSprayParticleSize ?: 100f
    dualSprayParticleSizeUnit = activeBrush?.dualSprayParticleSizeUnit ?: 0
    dualSprayParticleSizeSyncBrushSize = activeBrush?.dualSprayParticleSizeSyncBrushSize ?: true
    dualSprayDensity = activeBrush?.dualSprayDensity ?: 1f
    dualSprayBias = activeBrush?.dualSprayBias ?: 0f
    dualSpraySpread = activeBrush?.dualSpraySpread ?: 1f
    dualSprayParticleRotationDegrees = activeBrush?.dualSprayParticleRotationDegrees ?: 0f
    dualSprayParticleFollowStylusOrientation = activeBrush?.dualSprayParticleFollowStylusOrientation ?: false
    dualSprayParticleFollowStrokeDirection = activeBrush?.dualSprayParticleFollowStrokeDirection ?: false
    dualSprayParticleRandomRotationDegrees = activeBrush?.dualSprayParticleRandomRotationDegrees ?: 0f
    dualTipSequenceMode = activeBrush?.dualTipSequenceMode ?: 0
    dualTipFlipHorizontalMode = activeBrush?.dualTipFlipHorizontalMode ?: 0
    dualTipFlipVerticalMode = activeBrush?.dualTipFlipVerticalMode ?: 0
    dualBlendTipsWithDarken = activeBrush?.dualBlendTipsWithDarken ?: false
    dualCompositeMode = activeBrush?.dualCompositeMode ?: 0
    dualApplyRgb = activeBrush?.dualApplyRgb ?: false
    taperMode = activeBrush?.taperMode ?: BrushTaperMode.PERCENTAGE
    taperStartEnabled = activeBrush?.taperStartEnabled ?: false
    taperStartLength = activeBrush?.taperStartLength ?: .2f
    taperStartLengthUnit = activeBrush?.taperStartLengthUnit ?: 0
    taperStartMinimum = activeBrush?.taperStartMinimum ?: .3f
    taperEndEnabled = activeBrush?.taperEndEnabled ?: false
    taperEndLength = activeBrush?.taperEndLength ?: .2f
    taperEndLengthUnit = activeBrush?.taperEndLengthUnit ?: 0
    taperEndMinimum = activeBrush?.taperEndMinimum ?: .3f
    waterEdgeEnabled = activeBrush?.waterEdgeEnabled ?: false
    waterEdgeRadius = activeBrush?.waterEdgeRadius ?: 3f
    waterEdgeRadiusUnit = activeBrush?.waterEdgeRadiusUnit ?: 0
    waterEdgeOpacity = activeBrush?.waterEdgeOpacity ?: .2f
    waterEdgeDarkness = activeBrush?.waterEdgeDarkness ?: 0f
    waterEdgeBlur = activeBrush?.waterEdgeBlur ?: 0f
    waterEdgeBlurUnit = activeBrush?.waterEdgeBlurUnit ?: 0
    sizePressureEnabled = activeBrush?.sizePressureEnabled ?: (selectedTool == EditorTool.PEN)
    sizePressureMinimum = activeBrush?.sizePressureMinimum ?: .35f
    sizePressureCurve = activeBrush?.sizePressureCurve.orEmpty()
    sizeSecondaryDynamics = activeBrush?.sizeSecondaryDynamics ?: BrushSecondaryDynamics()
    opacityPressureEnabled = activeBrush?.opacityPressureEnabled ?: false
    opacityPressureMinimum = activeBrush?.opacityPressureMinimum ?: 0f
    opacityPressureCurve = activeBrush?.opacityPressureCurve.orEmpty()
    opacitySecondaryDynamics = activeBrush?.opacitySecondaryDynamics ?: BrushSecondaryDynamics()
    flowPressureEnabled = activeBrush?.flowPressureEnabled ?: false
    flowPressureMinimum = activeBrush?.flowPressureMinimum ?: 0f
    flowPressureCurve = activeBrush?.flowPressureCurve.orEmpty()
    flowSecondaryDynamics = activeBrush?.flowSecondaryDynamics ?: BrushSecondaryDynamics()
    spacingPressureEnabled = activeBrush?.spacingPressureEnabled ?: false
    spacingPressureMinimum = activeBrush?.spacingPressureMinimum ?: 0f
    spacingPressureCurve = activeBrush?.spacingPressureCurve.orEmpty()
    spacingSecondaryDynamics = activeBrush?.spacingSecondaryDynamics ?: BrushSecondaryDynamics()
    thicknessPressureEnabled = activeBrush?.thicknessPressureEnabled ?: false
    thicknessPressureMinimum = activeBrush?.thicknessPressureMinimum ?: 0f
    thicknessPressureCurve = activeBrush?.thicknessPressureCurve.orEmpty()
    thicknessSecondaryDynamics = activeBrush?.thicknessSecondaryDynamics ?: BrushSecondaryDynamics()
    blurPressureEnabled = activeBrush?.blurPressureEnabled ?: false
    blurPressureMinimum = activeBrush?.blurPressureMinimum ?: 0f
    blurPressureCurve = activeBrush?.blurPressureCurve.orEmpty()
    blurSecondaryDynamics = activeBrush?.blurSecondaryDynamics ?: BrushSecondaryDynamics()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectElementView(
    element: PageElement,
    page: InkPage,
    layerOpacity: Float,
    selected: Boolean,
    editable: Boolean,
    canvasWidth: androidx.compose.ui.unit.Dp,
    canvasHeight: androidx.compose.ui.unit.Dp,
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onSelect: () -> Unit,
    onChange: (PageElement) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    eraserPreset: EraserPreset,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    var dragX by remember(element.id) { mutableFloatStateOf(0f) }
    var dragY by remember(element.id) { mutableFloatStateOf(0f) }
    var editing by remember(element.id) { mutableStateOf(false) }
    var stickyInkEditing by remember(element.id) { mutableStateOf(false) }
    val objectWidth = (canvasWidth * (element.width / page.width)).coerceIn(54.dp, canvasWidth)
    val objectHeight = (canvasHeight * (element.height / page.height)).coerceIn(42.dp, canvasHeight)
    val gestureModifier = if (editable) Modifier.pointerInput(element.id, canvasWidthPx, canvasHeightPx) {
        detectDragGestures(
            onDragStart = { onSelect() },
            onDragEnd = { onChange(moveElement(element, dragX / canvasWidthPx, dragY / canvasHeightPx)); dragX = 0f; dragY = 0f },
            onDragCancel = { dragX = 0f; dragY = 0f },
        ) { change, amount -> change.consume(); dragX += amount.x; dragY += amount.y }
    } else Modifier
    Box(
        modifier
            .offset(x = canvasWidth * element.x, y = canvasHeight * element.y)
            .size(objectWidth, objectHeight)
            .graphicsLayer(rotationZ = element.rotationDegrees, alpha = (element.opacity * layerOpacity).coerceIn(0f, 1f), translationX = dragX, translationY = dragY)
            .then(if (selected) Modifier.border(2.dp, Sage, RoundedCornerShape(5.dp)) else Modifier)
            .then(gestureModifier)
            .combinedClickable(
                enabled = editable,
                onClick = onSelect,
                onDoubleClick = {
                    onSelect()
                    when (element) {
                        is PageElement.Sticky -> stickyInkEditing = true
                        is PageElement.Link -> openLink(context, element.url, language)
                        else -> Unit
                    }
                },
            ),
    ) {
        when (element) {
            is PageElement.Text -> {
                val scaledTextSize = (element.textSize * canvasWidth.value / page.width).coerceIn(10f, 40f)
                Text(
                    RichTextFormatter.displayText(element.text, element.listStyle),
                    color = Color(element.colorArgb),
                    fontSize = scaledTextSize.sp,
                    lineHeight = (scaledTextSize * element.lineSpacing.coerceIn(.8f, 2f)).sp,
                    fontWeight = if (element.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (element.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (element.underline) TextDecoration.Underline else TextDecoration.None,
                    textAlign = when (element.alignment) { InkTextAlignment.START -> TextAlign.Start; InkTextAlignment.CENTER -> TextAlign.Center; InkTextAlignment.END -> TextAlign.End },
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                )
            }
            is PageElement.Sticky -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(element.colorArgb))) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        if (element.collapsed) element.text.lineSequence().firstOrNull().orEmpty() else element.text,
                        Modifier.padding(10.dp),
                        maxLines = if (element.collapsed) 1 else 12,
                    )
                    if (!element.collapsed && element.strokes.isNotEmpty()) AndroidView(
                        factory = { context -> InkCanvasView(context).apply { isEnabled = false; tool = EditorTool.SELECT } },
                        update = { preview ->
                            preview.setDocumentStrokes(element.strokes, "sticky-preview:${element.id}")
                            preview.setProtectedStrokeIds(element.strokes.mapTo(mutableSetOf(), InkStroke::id))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            is PageElement.Shape -> Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val color = Color(element.colorArgb)
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(element.strokeWidth.coerceIn(1f, 40f))
                val triangle = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                val diamond = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width / 2f, size.height); lineTo(0f, size.height / 2f); close() }
                val freeform = androidx.compose.ui.graphics.Path().apply {
                    element.path.firstOrNull()?.let { first ->
                        moveTo(first.x * size.width, first.y * size.height)
                        for (index in 1 until element.path.size) { val vertex = element.path[index]; lineTo(vertex.x * size.width, vertex.y * size.height) }
                        close()
                    }
                }
                element.fillArgb?.let { fill -> when (element.kind) { ShapeKind.ELLIPSE -> drawOval(Color(fill)); ShapeKind.RECTANGLE -> drawRect(Color(fill)); ShapeKind.TRIANGLE -> drawPath(triangle, Color(fill)); ShapeKind.DIAMOND -> drawPath(diamond, Color(fill)); ShapeKind.FREEFORM -> drawPath(freeform, Color(fill)); else -> Unit } }
                when (element.kind) {
                    ShapeKind.ELLIPSE -> drawOval(color, style = stroke)
                    ShapeKind.RECTANGLE -> drawRect(color, style = stroke)
                    ShapeKind.TRIANGLE -> drawPath(triangle, color, style = stroke)
                    ShapeKind.DIAMOND -> drawPath(diamond, color, style = stroke)
                    ShapeKind.LINE -> drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = element.strokeWidth)
                    ShapeKind.ARROW -> { val y = size.height / 2f; drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = element.strokeWidth); drawLine(color, Offset(size.width, y), Offset(size.width - 24f, y - 16f), strokeWidth = element.strokeWidth); drawLine(color, Offset(size.width, y), Offset(size.width - 24f, y + 16f), strokeWidth = element.strokeWidth) }
                    ShapeKind.FREEFORM -> drawPath(freeform, color, style = stroke)
                }
            }
            is PageElement.Image -> {
                val normalizedCrop = element.crop.normalized()
                val bitmap = remember(element.assetPath, normalizedCrop) { ImageAssetDecoder.decodeCropped(element.assetPath, normalizedCrop) }
                DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
                val maskModifier = when (element.mask) {
                    ImageMask.RECTANGLE -> Modifier
                    ImageMask.ROUNDED -> Modifier.clip(RoundedCornerShape((element.cornerRadius * canvasWidth.value / page.width).coerceIn(0f, 120f).dp))
                    ImageMask.OVAL -> Modifier.clip(CircleShape)
                }
                if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), tr("Page image", "页面图片"), Modifier.fillMaxSize().then(maskModifier), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(Workspace), contentAlignment = Alignment.Center) { Text(tr("Image unavailable", "图片不可用"), color = MutedInk, fontSize = 11.sp) }
            }
            is PageElement.AudioMarker -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E7E7))) {
                Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { playAudio(element.assetPath) }) { Icon(Icons.Default.PlayArrow, tr("Play audio", "播放音频")) }
                    Column(Modifier.weight(1f)) { Text(element.title, maxLines = 1); Text(tr("Audio · ${formatDuration(element.durationMs)}", "音频 · ${formatDuration(element.durationMs)}"), color = MutedInk, fontSize = 11.sp) }
                }
            }
            is PageElement.Link -> Card(Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E7E7))) {
                Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White) { Icon(Icons.Outlined.Link, null, Modifier.padding(10.dp), tint = Color(0xFF111111)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(element.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        Text(LinkTarget.displayDestination(element.url), color = MutedInk, fontSize = 11.sp, maxLines = 1)
                    }
                    IconButton(onClick = { openLink(context, element.url, language) }) { Icon(Icons.Outlined.OpenInNew, tr("Open link", "打开链接")) }
                }
            }
        }
        if (selected && editable) Surface(Modifier.align(Alignment.TopEnd).offset(y = (-38).dp), shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp) {
            Row {
                if (element is PageElement.Sticky) {
                    IconButton(onClick = { stickyInkEditing = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Brush, tr("Write inside sticky note", "在便签内书写"), Modifier.size(18.dp)) }
                    IconButton(onClick = { onChange(element.copy(collapsed = !element.collapsed)) }, modifier = Modifier.size(36.dp)) { Icon(if (element.collapsed) Icons.Outlined.UnfoldMore else Icons.Outlined.UnfoldLess, if (element.collapsed) tr("Expand sticky note", "展开便签") else tr("Collapse sticky note", "折叠便签"), Modifier.size(18.dp)) }
                }
                if (element is PageElement.Link) IconButton(onClick = { openLink(context, element.url, language) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.OpenInNew, tr("Open link", "打开链接"), Modifier.size(18.dp)) }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Tune, tr("Edit object", "编辑对象"), Modifier.size(18.dp)) }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ContentCopy, tr("Duplicate object", "复制对象"), Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Delete, tr("Delete object", "删除对象"), Modifier.size(18.dp)) }
            }
        }
    }
    if (editing) ElementEditorDialog(element, onDismiss = { editing = false }, onSave = { onChange(it); editing = false })
    if (stickyInkEditing && element is PageElement.Sticky) StickyInkEditorDialog(
        sticky = element,
        settings = settings,
        activeBrush = activeBrush,
        brushSize = brushSize,
        brushOpacity = brushOpacity,
        brushColor = brushColor,
        initialEraserPreset = eraserPreset,
        onChange = { onChange(it) },
        onDismiss = { stickyInkEditing = false },
    )
}

@Composable
private fun StickyInkEditorDialog(
    sticky: PageElement.Sticky,
    settings: AppSettings,
    activeBrush: ImportedBrush?,
    brushSize: Float,
    brushOpacity: Float,
    brushColor: Int,
    initialEraserPreset: EraserPreset,
    onChange: (PageElement.Sticky) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(sticky.id) { mutableStateOf(sticky) }
    var tool by remember(sticky.id) { mutableStateOf(EditorTool.PEN) }
    var eraserPreset by remember(sticky.id) { mutableStateOf(initialEraserPreset) }
    var canvasView by remember(sticky.id) { mutableStateOf<InkCanvasView?>(null) }
    LaunchedEffect(sticky.strokes, sticky.text, sticky.collapsed) { draft = sticky }

    fun persist(strokes: List<InkStroke> = draft.strokes, collapsed: Boolean = draft.collapsed) {
        val updated = draft.copy(strokes = strokes, collapsed = collapsed)
        draft = updated
        onChange(updated)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(.9f).fillMaxHeight(.86f),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Write inside sticky note", "在便签内书写"), fontWeight = FontWeight.SemiBold)
                        Text(activeBrush?.name ?: tr("System pen", "系统画笔"), color = MutedInk, fontSize = 11.sp, maxLines = 1)
                    }
                    FilterChip(selected = tool == EditorTool.PEN, onClick = { tool = EditorTool.PEN }, label = { Text(tr("Pen", "钢笔")) }, leadingIcon = { Icon(Icons.Outlined.Brush, null, Modifier.size(16.dp)) })
                    FilterChip(selected = tool == EditorTool.HIGHLIGHTER, onClick = { tool = EditorTool.HIGHLIGHTER }, label = { Text(tr("Marker", "马克笔")) }, leadingIcon = { Icon(Icons.Outlined.BorderColor, null, Modifier.size(16.dp)) })
                    FilterChip(selected = tool == EditorTool.ERASER, onClick = { tool = EditorTool.ERASER }, label = { Text(tr("Eraser", "橡皮擦")) }, leadingIcon = { Icon(Icons.Outlined.AutoFixOff, null, Modifier.size(16.dp)) })
                    if (tool == EditorTool.ERASER) EraserPresetMenu(eraserPreset, { eraserPreset = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { canvasView?.undo() }) { Icon(Icons.Outlined.Undo, tr("Undo sticky ink", "撤销便签笔迹")) }
                    IconButton(onClick = { canvasView?.redo() }) { Icon(Icons.Outlined.Redo, tr("Redo sticky ink", "重做便签笔迹")) }
                    TextButton(onClick = { persist(strokes = emptyList()) }, enabled = draft.strokes.isNotEmpty()) { Text(tr("Clear ink", "清除笔迹")) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { persist(collapsed = !draft.collapsed) }) { Text(if (draft.collapsed) tr("Expand on page", "在页面上展开") else tr("Collapse on page", "在页面上折叠")) }
                    Button(onClick = onDismiss) { Text(tr("Done", "完成")) }
                }
                HorizontalDivider()
                Box(
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(draft.colorArgb)).border(1.dp, Color(0xFFBDBDBD), RoundedCornerShape(12.dp)),
                ) {
                    Text(draft.text, Modifier.padding(14.dp), color = Color(0xFF333333), maxLines = 10)
                    AndroidView(
                        factory = { context -> InkCanvasView(context).also { canvasView = it } },
                        update = { view ->
                            canvasView = view
                            view.applyBrushConfiguration(tool, settings, activeBrush, brushSize, brushOpacity, brushColor, eraserPreset)
                            view.activeLayerId = LayerIds.INK
                            view.pageModelWidth = sticky.width
                            view.pageModelHeight = sticky.height
                            view.setDocumentStrokes(draft.strokes, "sticky:${sticky.id}")
                            view.setProtectedStrokeIds(emptySet())
                            view.onStrokeCommitted = { stroke -> persist(strokes = draft.strokes + stroke) }
                            view.onStrokeRemoved = { strokeId -> persist(strokes = draft.strokes.filterNot { it.id == strokeId }) }
                            view.onStrokesReplaced = { removedIds, replacements, indices ->
                                persist(strokes = replaceStickyStrokes(draft.strokes, removedIds, replacements, indices))
                            }
                            view.onUndoGesture = { view.undo() }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(tr("Stylus writes. Fingers navigate unless finger drawing is enabled in Settings. Changes are saved after every completed stroke.", "触控笔用于书写；除非在设置中开启手指书写，否则手指仅用于导航。每笔完成后都会自动保存。"), color = MutedInk, fontSize = 11.sp)
            }
        }
    }
}

private fun replaceStickyStrokes(
    current: List<InkStroke>,
    removedIds: List<String>,
    replacements: List<InkStroke>,
    replacementIndices: List<Int>,
): List<InkStroke> {
    val replacedIds = removedIds.toSet() + replacements.map(InkStroke::id)
    val result = current.filterNot { it.id in replacedIds }.toMutableList()
    if (replacementIndices.size == replacements.size) {
        replacements.zip(replacementIndices).sortedBy { (_, index) -> index }.forEach { (stroke, index) ->
            result.add(index.coerceIn(0, result.size), stroke)
        }
    } else {
        result += replacements
    }
    return result
}

@Composable
private fun ElementEditorDialog(element: PageElement, onDismiss: () -> Unit, onSave: (PageElement) -> Unit) {
    var draft by remember(element.id) { mutableStateOf(element) }
    val normalizedDraftLink = (draft as? PageElement.Link)?.let { LinkTarget.normalize(it.url) }
    val canSave = draft !is PageElement.Link || normalizedDraftLink != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Object properties", "对象属性")) },
        text = { LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (val current = draft) {
                is PageElement.Text -> {
                    item { OutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text(tr("Text", "文本")) }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
                    item { BrushSettingSlider("Text size", current.textSize, 16f..160f) { draft = current.copy(textSize = it) } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = current.bold, onClick = { draft = current.copy(bold = !current.bold) }, label = { Text(tr("Bold", "粗体"), fontWeight = FontWeight.Bold) })
                        FilterChip(selected = current.italic, onClick = { draft = current.copy(italic = !current.italic) }, label = { Text(tr("Italic", "斜体"), fontStyle = FontStyle.Italic) })
                        FilterChip(selected = current.underline, onClick = { draft = current.copy(underline = !current.underline) }, label = { Text(tr("Underline", "下划线"), textDecoration = TextDecoration.Underline) })
                    } }
                    item { Text(tr("Alignment", "对齐方式"), color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { InkTextAlignment.entries.forEach { alignment -> FilterChip(selected = current.alignment == alignment, onClick = { draft = current.copy(alignment = alignment) }, label = { Text(localizedTextAlignment(alignment)) }) } } }
                    item { Text(tr("List style", "列表样式"), color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextListStyle.entries.forEach { style -> item { FilterChip(selected = current.listStyle == style, onClick = { draft = current.copy(listStyle = style) }, label = { Text(localizedListStyle(style)) }) } } } }
                    item { BrushSettingSlider("Line spacing", current.lineSpacing, .8f..2f) { draft = current.copy(lineSpacing = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt()) { draft = current.copy(colorArgb = it.toUInt().toLong()) } } }
                }
                is PageElement.Sticky -> {
                    item { OutlinedTextField(current.text, { draft = current.copy(text = it) }, label = { Text(tr("Note", "便签")) }, minLines = 4, modifier = Modifier.fillMaxWidth()) }
                    item { SettingSwitch("Collapsed", "Show only the first line.", current.collapsed) { draft = current.copy(collapsed = it) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt()) { draft = current.copy(colorArgb = it.toUInt().toLong()) } } }
                }
                is PageElement.Shape -> {
                    item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ShapeKind.entries.forEach { kind -> item { FilterChip(selected = current.kind == kind, onClick = {
                        val path = if (kind == ShapeKind.FREEFORM && current.path.size < 3) listOf(ShapeVertex(0f, 0f), ShapeVertex(1f, 0f), ShapeVertex(1f, 1f), ShapeVertex(0f, 1f)) else current.path
                        draft = current.copy(kind = kind, path = path)
                    }, label = { Text(localizedShapeKind(kind)) }) } } } }
                    item { BrushSettingSlider("Stroke width", current.strokeWidth, 1f..40f) { draft = current.copy(strokeWidth = it) } }
                    item { SettingSwitch("Fill", "Adds a translucent shape fill.", current.fillArgb != null) { draft = current.copy(fillArgb = if (it) 0x33111111 else null) } }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Stroke color", "描边颜色"), Modifier.weight(1f)); BrushColorPicker(current.colorArgb.toInt()) { draft = current.copy(colorArgb = it.toUInt().toLong()) } } }
                }
                is PageElement.Image -> {
                    item { Text(tr("Image source is retained unchanged. Crop and mask remain reversible and are included in editable backups.", "图片源文件保持不变。裁剪和蒙版均可撤销，并会包含在可编辑备份中。"), color = MutedInk, fontSize = 12.sp) }
                    item { Text(tr("Mask", "蒙版"), color = MutedInk, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ImageMask.entries.forEach { mask -> FilterChip(selected = current.mask == mask, onClick = { draft = current.copy(mask = mask) }, label = { Text(localizedImageMask(mask)) }) } } }
                    if (current.mask == ImageMask.ROUNDED) item { BrushSettingSlider("Corner radius", current.cornerRadius, 0f..200f) { draft = current.copy(cornerRadius = it) } }
                    item { Text(tr("Crop edges", "裁剪边缘"), color = MutedInk, fontSize = 11.sp) }
                    item { BrushSettingSlider("Left", current.crop.left, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(left = it).normalized()) } }
                    item { BrushSettingSlider("Top", current.crop.top, 0f..0.95f) { draft = current.copy(crop = current.crop.copy(top = it).normalized()) } }
                    item { BrushSettingSlider("Right", current.crop.right, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(right = it).normalized()) } }
                    item { BrushSettingSlider("Bottom", current.crop.bottom, 0.05f..1f) { draft = current.copy(crop = current.crop.copy(bottom = it).normalized()) } }
                    item { OutlinedButton(onClick = { draft = current.copy(crop = ImageCrop()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset crop", "重置裁剪")) } }
                }
                is PageElement.AudioMarker -> {
                    item { OutlinedTextField(current.title, { draft = current.copy(title = it) }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { Text(tr("Duration ${formatDuration(current.durationMs)} · audio is included in editable backups", "时长 ${formatDuration(current.durationMs)} · 音频会包含在可编辑备份中"), color = MutedInk, fontSize = 12.sp) }
                }
                is PageElement.Link -> {
                    item { OutlinedTextField(current.title, { draft = current.copy(title = it) }, label = { Text(tr("Title", "标题")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(current.url, { draft = current.copy(url = it) }, label = { Text(tr("Web, email, or phone address", "网页、邮箱或电话号码")) }, isError = LinkTarget.normalize(current.url) == null, supportingText = { Text(if (LinkTarget.normalize(current.url) == null) tr("Use a valid http, https, mailto, or tel address.", "请输入有效的 http、https、mailto 或 tel 地址。") else LinkTarget.displayDestination(current.url)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
            }
            item { BrushSettingSlider("Opacity", draft.opacity, .05f..1f) { draft = setElementOpacity(draft, it) } }
            item { BrushSettingSlider("Rotation", draft.rotationDegrees, -180f..180f) { draft = setElementRotation(draft, it) } }
            item { BrushSettingSlider("Width", draft.width, 40f..1600f) { draft = setElementSize(draft, it, draft.height) } }
            item { BrushSettingSlider("Height", draft.height, 40f..2263f) { draft = setElementSize(draft, draft.width, it) } }
        } },
        confirmButton = { Button(enabled = canSave, onClick = {
            val saved = (draft as? PageElement.Link)?.let { link ->
                val url = normalizedDraftLink ?: return@Button
                link.copy(title = link.title.trim().ifBlank { LinkTarget.displayDestination(url, 48) }, url = url)
            } ?: draft
            onSave(saved)
        }) { Text(tr("Save", "保存")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

private fun moveElement(element: PageElement, dx: Float, dy: Float): PageElement = when (element) {
    is PageElement.Text -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Sticky -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Shape -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Image -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.AudioMarker -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
    is PageElement.Link -> element.copy(x = (element.x + dx).coerceIn(0f, .98f), y = (element.y + dy).coerceIn(0f, .98f))
}

private fun resizeElement(element: PageElement, factor: Float): PageElement = setElementSize(element, (element.width * factor).coerceIn(40f, 1600f), (element.height * factor).coerceIn(40f, 2263f))
private fun rotateElement(element: PageElement, delta: Float): PageElement = setElementRotation(element, ((element.rotationDegrees + delta + 180f) % 360f) - 180f)
private fun setElementSize(element: PageElement, width: Float, height: Float): PageElement = when (element) { is PageElement.Text -> element.copy(width = width, height = height); is PageElement.Sticky -> element.copy(width = width, height = height); is PageElement.Shape -> element.copy(width = width, height = height); is PageElement.Image -> element.copy(width = width, height = height); is PageElement.AudioMarker -> element.copy(width = width, height = height); is PageElement.Link -> element.copy(width = width, height = height) }
private fun setElementRotation(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(rotationDegrees = value); is PageElement.Sticky -> element.copy(rotationDegrees = value); is PageElement.Shape -> element.copy(rotationDegrees = value); is PageElement.Image -> element.copy(rotationDegrees = value); is PageElement.AudioMarker -> element.copy(rotationDegrees = value); is PageElement.Link -> element.copy(rotationDegrees = value) }
private fun setElementOpacity(element: PageElement, value: Float): PageElement = when (element) { is PageElement.Text -> element.copy(opacity = value); is PageElement.Sticky -> element.copy(opacity = value); is PageElement.Shape -> element.copy(opacity = value); is PageElement.Image -> element.copy(opacity = value); is PageElement.AudioMarker -> element.copy(opacity = value); is PageElement.Link -> element.copy(opacity = value) }

private fun openLink(context: android.content.Context, rawUrl: String, language: UiLanguage) {
    val url = LinkTarget.normalize(rawUrl)
    if (url == null) {
        Toast.makeText(context, language.text("This link is not valid or supported.", "此链接无效或不受支持。"), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }.onFailure {
        Toast.makeText(context, language.text("No app can open this link.", "没有应用可以打开此链接。"), Toast.LENGTH_SHORT).show()
    }
}

private fun playAudio(path: String) {
    var player: android.media.MediaPlayer? = null
    runCatching {
        player = android.media.MediaPlayer().apply { setDataSource(path); prepare(); setOnCompletionListener { completed -> completed.release() }; start() }
    }.onFailure { player?.release() }
}

private fun formatDuration(durationMs: Long): String { val total = durationMs / 1000; return "%d:%02d".format(total / 60, total % 60) }

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
                DropdownMenuItem(
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
                    FilterChip(
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
private fun ToolDock(tool: EditorTool, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onScale: () -> Unit, onRotate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onDelete: () -> Unit, selectionShape: SelectionShape, selectionMode: SelectionMode, selectionFilter: SelectionFilter, onSelectionShape: (SelectionShape) -> Unit, onSelectionMode: (SelectionMode) -> Unit, onSelectionFilter: (SelectionFilter) -> Unit, eraserPreset: EraserPreset, onEraserPreset: (EraserPreset) -> Unit, rulerEnabled: Boolean, rulerSnapDegrees: Float, onToggleRuler: () -> Unit, onRulerSnap: (Float) -> Unit, onResetRuler: () -> Unit, gridSnapEnabled: Boolean, gridSpacing: Float, alignmentGuidesEnabled: Boolean, onToggleGridSnap: () -> Unit, onGridSpacing: (Float) -> Unit, onToggleAlignmentGuides: () -> Unit, onResetAlignmentGuides: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, vertical: Boolean, onToggleOrientation: () -> Unit, onResetPosition: () -> Unit, onDock: (ToolbarDock) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit, modifier: Modifier = Modifier) {
    var moreToolsOpen by remember { mutableStateOf(false) }
    var controlsOpen by remember { mutableStateOf(false) }
    val dragModifier = Modifier.size(32.dp).pointerInput(vertical) {
        detectDragGestures(
            onDragEnd = onDragEnd,
            onDragCancel = onDragEnd,
        ) { change, amount -> change.consume(); onDrag(amount.x, amount.y) }
    }.padding(6.dp)
    Surface(modifier.shadow(10.dp, RoundedCornerShape(18.dp)), shape = RoundedCornerShape(18.dp), color = Color.White, tonalElevation = 1.dp) {
        val content: @Composable () -> Unit = {
            ToolDockItems(
                tool = tool,
                onTool = onTool,
                onUndo = onUndo,
                onRedo = onRedo,
                onScale = onScale,
                onRotate = onRotate,
                onCopy = onCopy,
                onCut = onCut,
                onClipboardCopy = onClipboardCopy,
                onPaste = onPaste,
                onDelete = onDelete,
                selectionShape = selectionShape,
                selectionMode = selectionMode,
                selectionFilter = selectionFilter,
                onSelectionShape = onSelectionShape,
                onSelectionMode = onSelectionMode,
                onSelectionFilter = onSelectionFilter,
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
                onToggleOrientation = onToggleOrientation,
                onResetPosition = onResetPosition,
                onDock = onDock,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                brushColor = brushColor,
                onSize = onSize,
                onOpacity = onOpacity,
                onColor = onColor,
            )
        }
        if (vertical) Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
        else Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun ToolDockItems(tool: EditorTool, onTool: (EditorTool) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onScale: () -> Unit, onRotate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onClipboardCopy: () -> Unit, onPaste: () -> Unit, onDelete: () -> Unit, selectionShape: SelectionShape, selectionMode: SelectionMode, selectionFilter: SelectionFilter, onSelectionShape: (SelectionShape) -> Unit, onSelectionMode: (SelectionMode) -> Unit, onSelectionFilter: (SelectionFilter) -> Unit, eraserPreset: EraserPreset, onEraserPreset: (EraserPreset) -> Unit, rulerEnabled: Boolean, rulerSnapDegrees: Float, onToggleRuler: () -> Unit, onRulerSnap: (Float) -> Unit, onResetRuler: () -> Unit, gridSnapEnabled: Boolean, gridSpacing: Float, alignmentGuidesEnabled: Boolean, onToggleGridSnap: () -> Unit, onGridSpacing: (Float) -> Unit, onToggleAlignmentGuides: () -> Unit, onResetAlignmentGuides: () -> Unit, dragModifier: Modifier, vertical: Boolean, moreToolsOpen: Boolean, onMoreToolsOpen: (Boolean) -> Unit, controlsOpen: Boolean, onControlsOpen: (Boolean) -> Unit, onToggleOrientation: () -> Unit, onResetPosition: () -> Unit, onDock: (ToolbarDock) -> Unit, brushSize: Float, brushOpacity: Float, brushColor: Int, onSize: (Float) -> Unit, onOpacity: (Float) -> Unit, onColor: (Int) -> Unit) {
    Icon(Icons.Outlined.DragIndicator, tr("Drag toolbar", "拖动工具栏"), modifier = dragModifier, tint = MutedInk)
    ToolButton(EditorTool.PEN, Icons.Outlined.Edit, tr("Pen", "钢笔"), tool, onTool)
    ToolButton(EditorTool.HIGHLIGHTER, Icons.Outlined.BorderColor, tr("Marker", "标记笔"), tool, onTool)
    ToolButton(EditorTool.ERASER, Icons.Outlined.AutoFixOff, tr("Eraser", "橡皮"), tool, onTool)
    ToolButton(EditorTool.SELECT, Icons.Outlined.Gesture, tr("Select", "选择"), tool, onTool)
    Box {
        CompactToolbarIconButton(onClick = { onMoreToolsOpen(true) }, description = tr("More tools", "更多工具"), selected = rulerEnabled || gridSnapEnabled || alignmentGuidesEnabled || tool in setOf(EditorTool.EYEDROPPER, EditorTool.SHAPE, EditorTool.TEXT, EditorTool.NOTE, EditorTool.LINK, EditorTool.IMAGE, EditorTool.AUDIO, EditorTool.LASER)) { Icon(Icons.Outlined.Apps, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = moreToolsOpen, onDismissRequest = { onMoreToolsOpen(false) }) {
            listOf(
                Triple(EditorTool.EYEDROPPER, Icons.Outlined.Colorize, tr("Eyedropper", "吸管")),
                Triple(EditorTool.SHAPE, Icons.Outlined.Category, tr("Shape", "形状")),
                Triple(EditorTool.TEXT, Icons.Outlined.Title, tr("Text", "文字")),
                Triple(EditorTool.NOTE, Icons.Outlined.StickyNote2, tr("Note", "便笺")),
                Triple(EditorTool.LINK, Icons.Outlined.Link, tr("Link", "链接")),
                Triple(EditorTool.IMAGE, Icons.Outlined.Image, tr("Image", "图片")),
                Triple(EditorTool.AUDIO, Icons.Outlined.Mic, tr("Audio", "音频")),
                Triple(EditorTool.LASER, Icons.Outlined.TrackChanges, tr("Laser pointer", "激光笔")),
            ).forEach { (value, icon, label) ->
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { onMoreToolsOpen(false); onTool(value) })
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(tr(if (rulerEnabled) "Hide ruler" else "Show ruler", if (rulerEnabled) "隐藏直尺" else "显示直尺")) },
                leadingIcon = { Icon(Icons.Outlined.Straighten, null) },
                trailingIcon = { if (rulerEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleRuler() },
            )
            DropdownMenuItem(
                text = { Text(tr(if (gridSnapEnabled) "Hide snap grid" else "Show snap grid", if (gridSnapEnabled) "隐藏吸附网格" else "显示吸附网格")) },
                leadingIcon = { Icon(Icons.Outlined.GridOn, null) },
                trailingIcon = { if (gridSnapEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleGridSnap() },
            )
            DropdownMenuItem(
                text = { Text(tr(if (alignmentGuidesEnabled) "Hide alignment guides" else "Show alignment guides", if (alignmentGuidesEnabled) "隐藏对齐参考线" else "显示对齐参考线")) },
                leadingIcon = { Icon(Icons.Outlined.AlignHorizontalCenter, null) },
                trailingIcon = { if (alignmentGuidesEnabled) Icon(Icons.Outlined.Check, null) },
                onClick = { onMoreToolsOpen(false); onToggleAlignmentGuides() },
            )
        }
    }
    Box {
        CompactToolbarIconButton(onClick = { onControlsOpen(true) }, description = tr("Tool controls", "工具参数")) { Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp)) }
        DropdownMenu(expanded = controlsOpen, onDismissRequest = { onControlsOpen(false) }, modifier = Modifier.widthIn(min = 300.dp, max = 360.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${when (tool) { EditorTool.PEN -> tr("Pen", "钢笔"); EditorTool.HIGHLIGHTER -> tr("Marker", "标记笔"); EditorTool.ERASER -> tr("Eraser", "橡皮"); EditorTool.SELECT -> tr("Selection", "选择"); else -> tool.name.lowercase().replaceFirstChar(Char::uppercase) }} ${tr("controls", "参数")}", fontWeight = FontWeight.SemiBold)
                if (tool in setOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ERASER)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Size", "大小"), Modifier.weight(1f), color = MutedInk)
                        IconButton(onClick = { onSize(brushSize - maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Remove, tr("Smaller", "减小")) }
                        Text(if (brushSize < 10f) "%.1f".format(brushSize) else brushSize.toInt().toString(), Modifier.width(38.dp), textAlign = TextAlign.Center)
                        IconButton(onClick = { onSize(brushSize + maxOf(.5f, brushSize * .12f)) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, tr("Larger", "增大")) }
                    }
                    if (tool != EditorTool.ERASER) {
                        Text("${tr("Opacity", "不透明度")}  ${(brushOpacity * 100).toInt()}%", color = MutedInk, fontSize = 12.sp)
                        Slider(value = brushOpacity, onValueChange = onOpacity, valueRange = .05f..1f)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(tr("Color", "颜色"), Modifier.weight(1f), color = MutedInk); BrushColorPicker(brushColor, onColor) }
                    }
                }
                if (tool == EditorTool.ERASER) {
                    Text(tr("Eraser brush", "橡皮笔刷"), color = MutedInk, fontSize = 12.sp)
                    EraserPresetPicker(eraserPreset, onEraserPreset)
                }
                if (rulerEnabled) {
                    HorizontalDivider()
                    Text(tr("Ruler angle snap", "直尺角度吸附"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1f, 5f, 15f, 45f).forEach { step ->
                            FilterChip(selected = rulerSnapDegrees == step, onClick = { onRulerSnap(step) }, label = { Text("${step.toInt()}°", fontSize = 10.sp) })
                        }
                    }
                    OutlinedButton(onClick = onResetRuler, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Reset ruler", "重置直尺")) }
                }
                if (gridSnapEnabled) {
                    HorizontalDivider()
                    Text(tr("Snap grid spacing", "吸附网格间距"), color = MutedInk, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(32f, 64f, 96f, 128f).forEach { spacing ->
                            FilterChip(selected = gridSpacing == spacing, onClick = { onGridSpacing(spacing) }, label = { Text(spacing.toInt().toString(), fontSize = 10.sp) })
                        }
                    }
                }
                if (alignmentGuidesEnabled) {
                    HorizontalDivider()
                    Text(tr("Alignment guides", "对齐参考线"), color = MutedInk, fontSize = 12.sp)
                    Text(tr("Drag either blue line with one finger. Pen strokes and shape points snap when they start nearby.", "用单指拖动任一蓝色参考线；笔迹落点和形状端点靠近时会自动吸附。"), color = MutedInk, fontSize = 10.sp)
                    OutlinedButton(onClick = onResetAlignmentGuides, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Center alignment guides", "参考线居中")) }
                }
                if (tool == EditorTool.SELECT) {
                    Text(tr("Selection", "选择"), color = MutedInk, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionShape.entries.forEach { shape -> FilterChip(selected = selectionShape == shape, onClick = { onSelectionShape(shape) }, label = { Text(if (shape == SelectionShape.FREEFORM) tr("Freeform", "自由套索") else tr("Rectangle", "矩形"), fontSize = 10.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionMode.entries.forEach { mode -> FilterChip(selected = selectionMode == mode, onClick = { onSelectionMode(mode) }, label = { Text(when (mode) { SelectionMode.REPLACE -> tr("New", "新建"); SelectionMode.ADD -> tr("Add", "添加"); SelectionMode.SUBTRACT -> tr("Subtract", "减去"); SelectionMode.INTERSECT -> tr("Intersect", "交集") }, fontSize = 10.sp) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        SelectionFilter.entries.forEach { filter -> FilterChip(selected = selectionFilter == filter, onClick = { onSelectionFilter(filter) }, label = { Text(when (filter) { SelectionFilter.ALL -> tr("All", "全部"); SelectionFilter.ACTIVE_LAYER -> tr("Layer", "图层"); SelectionFilter.CURRENT_BRUSH -> tr("Brush", "笔刷"); SelectionFilter.CURRENT_COLOR -> tr("Color", "颜色") }, fontSize = 10.sp) }) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CompactToolbarIconButton(onScale, tr("Scale selection", "缩放所选内容")) { Icon(Icons.Outlined.ZoomOutMap, null) }
                        CompactToolbarIconButton(onRotate, tr("Rotate selection", "旋转所选内容")) { Icon(Icons.Outlined.RotateRight, null) }
                        CompactToolbarIconButton(onCopy, tr("Duplicate selection", "复制所选内容")) { Icon(Icons.Outlined.ControlPointDuplicate, null) }
                        CompactToolbarIconButton(onDelete, tr("Delete selection", "删除所选内容")) { Icon(Icons.Outlined.Delete, null) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        CompactToolbarIconButton(onCut, tr("Cut ink selection", "剪切所选墨迹")) { Icon(Icons.Outlined.ContentCut, null) }
                        CompactToolbarIconButton(onClipboardCopy, tr("Copy ink selection to clipboard", "复制所选墨迹到剪贴板")) { Icon(Icons.Outlined.ContentCopy, null) }
                        CompactToolbarIconButton(onPaste, tr("Paste ink selection", "粘贴所选墨迹")) { Icon(Icons.Outlined.ContentPaste, null) }
                    }
                }
                HorizontalDivider()
                Text(tr("Palette position", "工具栏位置"), color = MutedInk, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    ToolbarDock.entries.forEach { dock -> AssistChip(onClick = { onDock(dock) }, label = { Text(when (dock) { ToolbarDock.FREE -> tr("Free", "自由"); ToolbarDock.TOP -> tr("Top", "顶部"); ToolbarDock.BOTTOM -> tr("Bottom", "底部"); ToolbarDock.LEFT -> tr("Left", "左侧"); ToolbarDock.RIGHT -> tr("Right", "右侧") }, fontSize = 10.sp) }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleOrientation, modifier = Modifier.weight(1f)) { Icon(if (vertical) Icons.Outlined.SwapHoriz else Icons.Outlined.SwapVert, null); Spacer(Modifier.width(5.dp)); Text(if (vertical) tr("Horizontal", "水平") else tr("Vertical", "垂直")) }
                    OutlinedButton(onClick = onResetPosition, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(5.dp)); Text(tr("Reset", "重置")) }
                }
            }
        }
    }
    CompactToolbarIconButton(onClick = onUndo, description = tr("Undo", "撤销")) { Icon(Icons.Outlined.Undo, null, Modifier.size(20.dp)) }
    CompactToolbarIconButton(onClick = onRedo, description = tr("Redo", "重做")) { Icon(Icons.Outlined.Redo, null, Modifier.size(20.dp)) }
}

@Composable
private fun CompactToolbarIconButton(onClick: () -> Unit, description: String, selected: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) Sage else Color.Transparent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (selected) Color.White else Ink) { Box(Modifier.semantics { contentDescription = description }) { content() } }
    }
}

@Composable
private fun BrushColorPicker(color: Int, onColor: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var editorOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val paletteStore = remember { ColorPaletteStore(context) }
    var customPalette by remember { mutableStateOf(paletteStore.load()) }
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var brightness by remember { mutableFloatStateOf(0f) }
    val presets = listOf(0xFF111111, 0xFF5B4B8A, 0xFFB33A3A, 0xFF235A9F, 0xFFB56A21, 0xFF2E7D60, 0xFFFFFFFF).map { it.toInt() }
    val colors = (presets + customPalette).distinct()
    LaunchedEffect(editorOpen, color) {
        if (editorOpen) {
            val values = FloatArray(3)
            AndroidColor.colorToHSV(color, values)
            hue = values[0]; saturation = values[1]; brightness = values[2]
        }
    }
    val customColor = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
    Box {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)).border(1.dp, OutlineSoft, CircleShape).clickable { customPalette = paletteStore.load(); open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Column(Modifier.widthIn(min = 280.dp, max = 420.dp).padding(10.dp)) {
                Text(tr("Colors", "颜色"), fontSize = 12.sp, color = MutedInk)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { items(colors) { value -> Box(Modifier.size(30.dp).clip(CircleShape).background(Color(value)).border(if (value == color) 3.dp else 1.dp, if (value == color) Sage else OutlineSoft, CircleShape).clickable { onColor(value); open = false }) } }
                TextButton(onClick = { open = false; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Palette, null); Spacer(Modifier.width(8.dp)); Text(tr("Custom color and palette", "自定义颜色与色板")) }
            }
        }
    }
    if (editorOpen) AlertDialog(
        onDismissRequest = { editorOpen = false },
        title = { Text(tr("Color and palette", "颜色与色板")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 340.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(54.dp).clip(CircleShape).background(Color(customColor)).border(1.dp, OutlineSoft, CircleShape)); Spacer(Modifier.width(12.dp)); Text("#${customColor.toUInt().toString(16).padStart(8, '0').uppercase()}") } }
                item { Text("${tr("Hue", "色相")}  ${hue.toInt()}°"); Slider(hue, { hue = it }, valueRange = 0f..360f) }
                item { Text("${tr("Saturation", "饱和度")}  ${(saturation * 100).toInt()}%"); Slider(saturation, { saturation = it }, valueRange = 0f..1f) }
                item { Text("${tr("Brightness", "明度")}  ${(brightness * 100).toInt()}%"); Slider(brightness, { brightness = it }, valueRange = 0f..1f) }
                item { OutlinedButton(onClick = { customPalette = paletteStore.add(customColor) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("Add current color to palette", "将当前颜色加入色板")) } }
                if (customPalette.isNotEmpty()) item {
                    Text(tr("Saved palette", "已保存色板"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(customPalette, key = { it }) { value ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Color(value)).border(1.dp, OutlineSoft, CircleShape).clickable { val values = FloatArray(3); AndroidColor.colorToHSV(value, values); hue = values[0]; saturation = values[1]; brightness = values[2] })
                                IconButton(onClick = { customPalette = paletteStore.remove(value) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, tr("Remove color", "移除颜色"), modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onColor(customColor); editorOpen = false }) { Text(tr("Use color", "使用此颜色")) } },
        dismissButton = { TextButton(onClick = { editorOpen = false }) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun ToolButton(value: EditorTool, icon: ImageVector, label: String, selected: EditorTool, onTool: (EditorTool) -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (value == selected) Sage else Color.Transparent).clickable { onTool(value) }, contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (value == selected) Color.White else Ink)
    }
}

@Composable
private fun LayerPanel(
    page: InkPage,
    activeLayerId: String,
    brushColor: Int,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    onColor: (Int) -> Unit,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    onAddFolder: () -> Unit,
    onRename: (String, String) -> Unit,
    onVisibility: (String) -> Unit,
    onLock: (String) -> Unit,
    onOpacity: (String, Float) -> Unit,
    onBlendMode: (String, LayerBlendMode) -> Unit,
    onMoveToFolder: (String, String?) -> Unit,
    onToggleFolderCollapsed: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDuplicate: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onDelete: (String) -> Unit,
    panelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    panelLocked: Boolean = false,
    onPanelMode: (WorkspacePanelMode) -> Unit = {},
    onPanelLocked: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.width(280.dp).fillMaxHeight().background(Color(0xFFF7F7F7)).padding(10.dp),
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    if (panelMode != WorkspacePanelMode.EXPANDED) {
        val compactModifier = when (dock) {
            WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> Modifier.width(if (panelMode == WorkspacePanelMode.ICON_ONLY) 56.dp else 176.dp).fillMaxHeight()
            WorkspacePanelDock.FREE -> Modifier.fillMaxSize()
            WorkspacePanelDock.BOTTOM -> modifier
        }
        CompactWorkspacePanel(
            tr("Layers", "图层"), Icons.Outlined.Layers, dock, panelMode, panelLocked,
            onPanelMode, onPanelLocked, onDock, onClose,
            tr("Change layers panel docking", "更改图层面板停靠方式"), compactModifier,
            onPanelDragStart, onPanelDrag, onPanelDragEnd, onPanelDragCancel,
        )
        return
    }
    var renameLayer by remember { mutableStateOf<InkLayer?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val dragDescription = tr("Drag layers panel", "拖动图层面板")
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    Column(modifier) {
        Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(10.dp), border = CardDefaults.outlinedCardBorder()) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Color", "颜色"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("Active drawing color", "当前绘画颜色"), fontSize = 10.sp, color = MutedInk)
                }
                BrushColorPicker(brushColor, onColor)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).then(
                    if (!panelLocked) Modifier.semantics { contentDescription = dragDescription }.pointerInput(dock) {
                        detectDragGestures(onDragStart = { currentPanelDragStart() }, onDragEnd = { currentPanelDragEnd() }, onDragCancel = { currentPanelDragCancel() }) { change, amount ->
                            change.consume()
                            currentPanelDrag(amount.x, amount.y)
                        }
                    } else Modifier,
                ),
            ) {
                Text(tr("Layers", "图层"), fontSize = 18.sp)
                Text(tr("Visible canvas stack", "可见画布堆栈"), fontSize = 10.sp, color = MutedInk)
            }
            WorkspacePanelDockMenu(dock, onDock, tr("Change layers panel docking", "更改图层面板停靠方式"), mode = panelMode, locked = panelLocked, onMode = onPanelMode, onLocked = onPanelLocked)
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, tr("Close layers panel", "关闭图层面板")) }
            IconButton(onClick = onAddFolder) { Icon(Icons.Outlined.CreateNewFolder, tr("Add layer folder", "添加图层文件夹")) }
            IconButton(onClick = onAdd) { Icon(Icons.Default.Add, tr("Add layer", "添加图层")) }
        }
        Spacer(Modifier.height(8.dp))
        page.layers.firstOrNull { it.id == activeLayerId }?.takeIf { it.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER) }?.let { activeLayer ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Opacity", "不透明度"), Modifier.weight(1f), color = MutedInk, fontSize = 11.sp)
                Text("${(activeLayer.opacity * 100f).toInt()}%", color = MutedInk, fontSize = 11.sp)
            }
            Slider(activeLayer.opacity, { onOpacity(activeLayer.id, it) }, valueRange = 0f..1f)
            Text(tr("Blend mode", "混合模式"), color = MutedInk, fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                LayerBlendMode.entries.forEach { mode ->
                    item(mode.name) {
                        FilterChip(
                            selected = activeLayer.blendMode == mode,
                            onClick = { onBlendMode(activeLayer.id, mode) },
                            label = { Text(when (mode) {
                                LayerBlendMode.NORMAL -> tr("Normal", "正常")
                                LayerBlendMode.MULTIPLY -> tr("Multiply", "正片叠底")
                                LayerBlendMode.SCREEN -> tr("Screen", "滤色")
                                LayerBlendMode.ADD -> tr("Add", "添加")
                            }, fontSize = 10.sp) },
                        )
                    }
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(LayerHierarchy.panelOrder(page.layers), key = { it.id }) { layer ->
                var menu by remember { mutableStateOf(false) }
                val effectivelyVisible = LayerHierarchy.effectiveVisible(page.layers, layer)
                val effectivelyLocked = LayerHierarchy.effectiveLocked(page.layers, layer)
                val editable = effectivelyVisible && !effectivelyLocked && layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)
                val depth = LayerHierarchy.depth(page.layers, layer)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp).clickable(enabled = editable) { onSelect(layer.id) },
                    colors = CardDefaults.cardColors(containerColor = if (layer.id == activeLayerId) SageContainer else Color.White),
                    border = if (layer.id == activeLayerId) CardDefaults.outlinedCardBorder().copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(Sage)) else CardDefaults.outlinedCardBorder(),
                ) {
                    Row(Modifier.heightIn(min = 48.dp).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (layer.role) { LayerRole.BACKGROUND -> Icons.Outlined.Image; LayerRole.HIGHLIGHT -> Icons.Outlined.BorderColor; LayerRole.OBJECTS -> Icons.Outlined.Widgets; LayerRole.FOLDER -> if (layer.collapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen; else -> Icons.Outlined.Layers },
                            contentDescription = null,
                            tint = if (effectivelyVisible) Ink else MutedInk,
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(layer.name, maxLines = 1, color = if (effectivelyVisible) Ink else MutedInk)
                            Text(when (layer.role) { LayerRole.BACKGROUND -> tr("Background", "背景"); LayerRole.INK -> tr("Ink", "墨迹"); LayerRole.HIGHLIGHT -> tr("Highlight", "高亮"); LayerRole.OBJECTS -> tr("Objects", "对象"); LayerRole.CUSTOM -> tr("Custom", "自定义"); LayerRole.FOLDER -> tr("Folder", "文件夹") }, fontSize = 10.sp, color = MutedInk)
                        }
                        if (layer.role == LayerRole.FOLDER) IconButton(onClick = { onToggleFolderCollapsed(layer.id) }, modifier = Modifier.size(34.dp)) { Icon(if (layer.collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, if (layer.collapsed) tr("Expand folder", "展开文件夹") else tr("Collapse folder", "折叠文件夹"), Modifier.size(18.dp)) }
                        IconButton(onClick = { onVisibility(layer.id) }, modifier = Modifier.size(34.dp)) { Icon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (layer.visible) tr("Hide ${layer.name}", "隐藏 ${layer.name}") else tr("Show ${layer.name}", "显示 ${layer.name}"), Modifier.size(18.dp)) }
                        IconButton(onClick = { onLock(layer.id) }, modifier = Modifier.size(34.dp)) { Icon(if (layer.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, if (layer.locked) tr("Unlock ${layer.name}", "解锁 ${layer.name}") else tr("Lock ${layer.name}", "锁定 ${layer.name}"), Modifier.size(18.dp)) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.MoreVert, tr("Layer actions", "图层操作"), Modifier.size(18.dp)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(tr("Rename", "重命名")) }, onClick = { menu = false; renameLayer = layer; renameValue = layer.name })
                                DropdownMenuItem(text = { Text(tr("Move up", "上移")) }, enabled = page.layers.last().id != layer.id, onClick = { menu = false; onMove(layer.id, 1) })
                                DropdownMenuItem(text = { Text(tr("Move down", "下移")) }, enabled = page.layers.first().id != layer.id, onClick = { menu = false; onMove(layer.id, -1) })
                                if (layer.parentId != null) DropdownMenuItem(text = { Text(tr("Move out of folder", "移出文件夹")) }, onClick = { menu = false; onMoveToFolder(layer.id, null) })
                                LayerHierarchy.availableFolders(page.layers, layer.id).forEach { folder -> DropdownMenuItem(text = { Text("${tr("Move into", "移入")} ${folder.name}") }, onClick = { menu = false; onMoveToFolder(layer.id, folder.id) }) }
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Duplicate layer", "复制图层")) }, onClick = { menu = false; onDuplicate(layer.id) })
                                if (layer.role !in setOf(LayerRole.BACKGROUND, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(tr("Merge down", "向下合并")) }, enabled = LayerOperations.canMergeDown(page, layer.id), onClick = { menu = false; onMergeDown(layer.id) })
                                if (layer.role in setOf(LayerRole.CUSTOM, LayerRole.FOLDER)) DropdownMenuItem(text = { Text(if (layer.role == LayerRole.FOLDER) tr("Delete folder", "删除文件夹") else tr("Delete layer", "删除图层")) }, onClick = { menu = false; onDelete(layer.id) })
                            }
                        }
                    }
                }
            }
        }
    }
    renameLayer?.let { layer ->
        AlertDialog(
            onDismissRequest = { renameLayer = null },
            title = { Text(tr("Rename layer", "重命名图层")) },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true, label = { Text(tr("Layer name", "图层名称")) }) },
            confirmButton = { TextButton(onClick = { onRename(layer.id, renameValue); renameLayer = null }) { Text(tr("Save", "保存")) } },
            dismissButton = { TextButton(onClick = { renameLayer = null }) { Text(tr("Cancel", "取消")) } },
        )
    }
}

@Composable
private fun BrushPanel(
    brushes: List<ImportedBrush>,
    registeredGroups: List<String>,
    selectedBrushId: String?,
    settings: AppSettings,
    dock: WorkspacePanelDock,
    onDock: (WorkspacePanelDock) -> Unit,
    onClose: () -> Unit,
    onBrushSelected: (ImportedBrush) -> Unit,
    onImportBrushes: (String?) -> Unit,
    onExportBrush: (ImportedBrush) -> Unit,
    onUpdateBrush: (ImportedBrush) -> Unit,
    onReplaceBrushes: (List<ImportedBrush>) -> Unit,
    onReplaceGroups: (List<String>) -> Unit,
    onDeleteBrush: (String) -> Unit,
    onReorderBrush: (String, Int) -> Unit,
    panelMode: WorkspacePanelMode = WorkspacePanelMode.EXPANDED,
    panelLocked: Boolean = false,
    onPanelMode: (WorkspacePanelMode) -> Unit = {},
    onPanelLocked: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.width(330.dp).fillMaxHeight().background(Color(0xFFF7F7F7)).padding(12.dp),
    onPanelDragStart: () -> Unit = {},
    onPanelDrag: (Float, Float) -> Unit = { _, _ -> },
    onPanelDragEnd: () -> Unit = {},
    onPanelDragCancel: () -> Unit = {},
) {
    if (panelMode != WorkspacePanelMode.EXPANDED) {
        val compactModifier = when (dock) {
            WorkspacePanelDock.LEFT, WorkspacePanelDock.RIGHT -> Modifier.width(if (panelMode == WorkspacePanelMode.ICON_ONLY) 56.dp else 176.dp).fillMaxHeight()
            WorkspacePanelDock.FREE -> Modifier.fillMaxSize()
            WorkspacePanelDock.BOTTOM -> modifier
        }
        CompactWorkspacePanel(
            tr("Brush Library", "笔刷库"), Icons.Outlined.Brush, dock, panelMode, panelLocked,
            onPanelMode, onPanelLocked, onDock, onClose,
            tr("Change brushes panel docking", "更改笔刷面板停靠方式"), compactModifier,
            onPanelDragStart, onPanelDrag, onPanelDragEnd, onPanelDragCancel,
        )
        return
    }
    val context = LocalContext.current
    val historyStore = remember { BrushVersionHistoryStore(context) }
    var reportBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var editBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var historyBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var moveBrush by remember { mutableStateOf<ImportedBrush?>(null) }
    var manageGroups by remember { mutableStateOf(false) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var format by remember { mutableStateOf<BrushFormat?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var multiSelecting by remember { mutableStateOf(false) }
    var selectedBrushIds by remember { mutableStateOf(emptySet<String>()) }
    var batchGroupOperation by remember { mutableStateOf<BrushBatchGroupOperation?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var collapsedGroupPaths by remember { mutableStateOf(emptySet<String>()) }
    val copiedBrushSuffix = tr("copy", "副本")
    val groups = BrushGroupOperations.names(brushes)
    val groupTree = BrushGroupOperations.treePaths(brushes, registeredGroups)
    val currentBrushIds = brushes.mapTo(linkedSetOf()) { it.id }
    LaunchedEffect(currentBrushIds) {
        selectedBrushIds = selectedBrushIds.intersect(currentBrushIds)
        if (selectedBrushIds.isEmpty() && brushes.isEmpty()) multiSelecting = false
    }
    LaunchedEffect(groupTree, selectedGroup) {
        collapsedGroupPaths = collapsedGroupPaths.filterTo(linkedSetOf()) { collapsed -> groupTree.any { it.equals(collapsed, true) } }
        if (selectedGroup != null && selectedGroup != UNGROUPED_BRUSH_FILTER && groupTree.none { it.equals(selectedGroup, true) }) selectedGroup = null
    }
    val visibleBrushes = brushes.filter { brush ->
        val normalizedGroup = BrushGroupOperations.normalize(brush.groupName)
        val groupMatches = when (selectedGroup) {
            null -> true
            UNGROUPED_BRUSH_FILTER -> normalizedGroup.isBlank()
            else -> BrushGroupOperations.isWithin(normalizedGroup, selectedGroup.orEmpty())
        }
        (showHidden || !brush.hidden) && (!favoritesOnly || brush.favorite) && groupMatches && (format == null || brush.format == format) && (search.isBlank() || brush.name.contains(search, true) || normalizedGroup.contains(search, true) || brush.tags.any { it.contains(search, true) })
    }.sortedWith(compareBy<ImportedBrush> { it.sortOrder }.thenByDescending { it.favorite }.thenByDescending { it.lastUsedAt }.thenBy { it.name.lowercase() })
    val groupOrder = groups.mapIndexed { index, group -> group.lowercase() to index }.toMap()
    val brushSections = visibleBrushes.groupBy { BrushGroupOperations.normalize(it.groupName).ifBlank { "Ungrouped" } }
        .toList().sortedBy { (group, _) -> groupOrder[group.lowercase()] ?: Int.MAX_VALUE }
    val dragDescription = tr("Drag brushes panel", "拖动笔刷面板")
    val currentPanelDrag by rememberUpdatedState(onPanelDrag)
    val currentPanelDragStart by rememberUpdatedState(onPanelDragStart)
    val currentPanelDragEnd by rememberUpdatedState(onPanelDragEnd)
    val currentPanelDragCancel by rememberUpdatedState(onPanelDragCancel)
    val bottomDocked = dock == WorkspacePanelDock.BOTTOM
    val visibleGroupPaths = groupTree.filter { path ->
        generateSequence(BrushGroupOperations.parent(path), BrushGroupOperations::parent).none { ancestor -> collapsedGroupPaths.any { it.equals(ancestor, true) } }
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Brush Library", "笔刷库"),
                fontSize = if (bottomDocked) 16.sp else 20.sp,
                modifier = Modifier.weight(1f).then(
                    if (!panelLocked) Modifier.semantics { contentDescription = dragDescription }.pointerInput(dock) {
                        detectDragGestures(onDragStart = { currentPanelDragStart() }, onDragEnd = { currentPanelDragEnd() }, onDragCancel = { currentPanelDragCancel() }) { change, amount ->
                            change.consume()
                            currentPanelDrag(amount.x, amount.y)
                        }
                    } else Modifier,
                ),
            )
            WorkspacePanelDockMenu(dock, onDock, tr("Change brushes panel docking", "更改笔刷面板停靠方式"), mode = panelMode, locked = panelLocked, onMode = onPanelMode, onLocked = onPanelLocked)
            IconButton(onClick = {
                multiSelecting = !multiSelecting
                if (!multiSelecting) selectedBrushIds = emptySet()
            }) { Icon(if (multiSelecting) Icons.Outlined.Close else Icons.Outlined.Checklist, if (multiSelecting) tr("Cancel brush selection", "取消选择笔刷") else tr("Select multiple brushes", "批量选择笔刷")) }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, tr("Close brushes panel", "关闭笔刷面板")) }
            IconButton(onClick = { onImportBrushes(selectedGroup?.takeUnless { it == UNGROUPED_BRUSH_FILTER }) }) { Icon(Icons.Outlined.Add, tr("Import", "导入")) }
        }
        if (!bottomDocked) {
            Text(tr("Imported presets · compact view", "已导入预设 · 紧凑视图"), fontSize = 11.sp, color = MutedInk)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(search, { search = it }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(tr("Search brushes", "搜索笔刷")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (bottomDocked) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 3.dp)) {
                item("all") { FilterChip(selected = selectedGroup == null, onClick = { selectedGroup = null }, label = { Text(tr("All", "全部"), fontSize = 10.sp) }) }
                item("ungrouped") { FilterChip(selected = selectedGroup == UNGROUPED_BRUSH_FILTER, onClick = { selectedGroup = UNGROUPED_BRUSH_FILTER }, label = { Text(tr("Ungrouped", "未分组"), fontSize = 10.sp) }) }
                items(groupTree, key = { "bottom:$it" }) { path ->
                    FilterChip(selected = selectedGroup?.equals(path, true) == true, onClick = { selectedGroup = path }, label = { Text(BrushGroupOperations.displayName(path), fontSize = 10.sp, maxLines = 1) })
                }
                item("favorites") { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(tr("Favorites", "收藏"), fontSize = 10.sp) }) }
                item("manage") { IconButton(onClick = { manageGroups = true }, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.DriveFileRenameOutline, tr("Manage brush groups", "管理笔刷分组"), Modifier.size(17.dp)) } }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                item { FilterChip(selected = format == null, onClick = { format = null }, label = { Text(tr("All", "全部")) }) }
                BrushFormat.entries.filter { it !in setOf(BrushFormat.SYSTEM, BrushFormat.UNKNOWN) }.forEach { source -> item { FilterChip(selected = format == source, onClick = { format = source }, label = { Text(source.label) }) } }
                item { FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly }, label = { Text(tr("Favorites", "收藏")) }, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(16.dp)) }) }
                item { FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text(tr("Hidden", "隐藏")) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("GROUPS", "分组"), fontSize = 10.sp, color = MutedInk, modifier = Modifier.weight(1f))
                IconButton(onClick = { manageGroups = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.DriveFileRenameOutline, tr("Manage brush groups", "管理笔刷分组"), Modifier.size(17.dp)) }
            }
            Surface(color = Color.White, shape = RoundedCornerShape(8.dp), border = CardDefaults.outlinedCardBorder(), modifier = Modifier.fillMaxWidth().heightIn(max = 154.dp).padding(bottom = 6.dp)) {
                LazyColumn(contentPadding = PaddingValues(vertical = 3.dp)) {
                item("all") {
                    Row(Modifier.fillMaxWidth().clickable { selectedGroup = null }.background(if (selectedGroup == null) SageContainer else Color.Transparent).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AllInclusive, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(tr("All brushes", "全部笔刷"), fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(brushes.size.toString(), color = MutedInk, fontSize = 10.sp)
                    }
                }
                item("ungrouped") {
                    Row(Modifier.fillMaxWidth().clickable { selectedGroup = UNGROUPED_BRUSH_FILTER }.background(if (selectedGroup == UNGROUPED_BRUSH_FILTER) SageContainer else Color.Transparent).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOff, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text(tr("Ungrouped", "未分组"), fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(brushes.count { BrushGroupOperations.normalize(it.groupName).isBlank() }.toString(), color = MutedInk, fontSize = 10.sp)
                    }
                }
                items(visibleGroupPaths, key = { "tree:$it" }) { path ->
                    val hasChildren = groupTree.any { candidate -> BrushGroupOperations.parent(candidate)?.equals(path, true) == true }
                    val collapsed = collapsedGroupPaths.any { it.equals(path, true) }
                    val selected = selectedGroup?.equals(path, true) == true
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedGroup = path }.background(if (selected) SageContainer else Color.Transparent).padding(start = (6 + BrushGroupOperations.depth(path) * 16).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasChildren) IconButton(onClick = { collapsedGroupPaths = if (collapsed) collapsedGroupPaths - path else collapsedGroupPaths + path }, modifier = Modifier.size(22.dp)) {
                            Icon(if (collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, "${if (collapsed) tr("Expand group", "展开分组") else tr("Collapse group", "折叠分组")} $path", Modifier.size(16.dp))
                        } else Spacer(Modifier.width(22.dp))
                        Icon(if (hasChildren) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(BrushGroupOperations.displayName(path), maxLines = 1, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(brushes.count { BrushGroupOperations.isWithin(it.groupName, path) }.toString(), color = MutedInk, fontSize = 10.sp)
                    }
                }
                }
            }
        }
        if (multiSelecting) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(9.dp),
                border = CardDefaults.outlinedCardBorder(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("${selectedBrushIds.size} ${tr("selected", "已选择")}", fontSize = 11.sp, color = MutedInk)
                    val visibleIds = visibleBrushes.mapTo(linkedSetOf()) { it.id }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(top = 4.dp)) {
                        item {
                            AssistChip(
                                onClick = {
                                    selectedBrushIds = if (visibleIds.isNotEmpty() && selectedBrushIds.containsAll(visibleIds)) selectedBrushIds - visibleIds else selectedBrushIds + visibleIds
                                },
                                label = { Text(if (visibleIds.isNotEmpty() && selectedBrushIds.containsAll(visibleIds)) tr("Clear visible", "取消当前列表") else tr("Select visible", "选择当前列表"), fontSize = 10.sp) },
                            )
                        }
                        item { AssistChip(onClick = { batchGroupOperation = BrushBatchGroupOperation.MOVE }, enabled = selectedBrushIds.isNotEmpty(), label = { Text(tr("Move", "移动"), fontSize = 10.sp) }) }
                        item { AssistChip(onClick = { batchGroupOperation = BrushBatchGroupOperation.COPY }, enabled = selectedBrushIds.isNotEmpty(), label = { Text(tr("Copy", "复制"), fontSize = 10.sp) }) }
                        item {
                            AssistChip(
                                onClick = {
                                    onReplaceBrushes(BrushGroupOperations.setFavorite(brushes, selectedBrushIds, true))
                                    selectedBrushIds = emptySet(); multiSelecting = false
                                },
                                enabled = selectedBrushIds.isNotEmpty(),
                                label = { Text(tr("Favorite", "收藏"), fontSize = 10.sp) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    onReplaceBrushes(BrushGroupOperations.setHidden(brushes, selectedBrushIds, true))
                                    selectedBrushIds = emptySet(); multiSelecting = false
                                },
                                enabled = selectedBrushIds.isNotEmpty(),
                                label = { Text(tr("Hide", "隐藏"), fontSize = 10.sp) },
                            )
                        }
                        item { AssistChip(onClick = { confirmBatchDelete = true }, enabled = selectedBrushIds.isNotEmpty(), label = { Text(tr("Remove", "移除"), fontSize = 10.sp) }) }
                    }
                }
            }
        }
        if (brushes.isNotEmpty()) {
            Text("${tr("BRUSHES", "笔刷")} (${visibleBrushes.size}/${brushes.size})", fontSize = 11.sp, color = MutedInk)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                brushSections.forEach { (group, sectionBrushes) ->
                    item("group:$group") { Text(if (group == "Ungrouped") tr("UNGROUPED", "未分组") else group.uppercase(), color = MutedInk, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp)) }
                    items(sectionBrushes, key = { it.id }) { brush ->
                        ImportedBrushRow(
                            brush = brush,
                            selected = brush.id == selectedBrushId,
                            multiSelecting = multiSelecting,
                            multiSelected = brush.id in selectedBrushIds,
                            onToggleSelection = { selectedBrushIds = if (brush.id in selectedBrushIds) selectedBrushIds - brush.id else selectedBrushIds + brush.id },
                            onBeginSelection = { multiSelecting = true; selectedBrushIds = selectedBrushIds + brush.id },
                            onSelect = { onBrushSelected(brush) },
                            onReport = { reportBrush = brush },
                            onEdit = { editBrush = brush },
                            onHistory = { historyBrush = brush },
                            onMove = { moveBrush = brush },
                            onExport = { onExportBrush(brush) },
                            onFavorite = { onUpdateBrush(brush.copy(favorite = !brush.favorite)) },
                            onHide = { onUpdateBrush(brush.copy(hidden = !brush.hidden)) },
                            onDelete = { historyStore.clear(brush.id); historyRevision++; onDeleteBrush(brush.id) },
                            onReorder = { delta -> onReorderBrush(brush.id, delta) },
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(18.dp)); Text(tr("SOURCES", "来源"), fontSize = 11.sp, color = MutedInk)
            Spacer(Modifier.height(10.dp)); listOf(tr("System Brushes", "系统笔刷") to 12, "ABR" to 0, "CSP / SUT" to 0, "Procreate" to 0, tr("My Brushes", "我的笔刷") to 0).forEach { (name, count) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(name, Modifier.weight(1f)); Text(count.toString(), color = MutedInk, fontSize = 11.sp) }
            }
            Spacer(Modifier.weight(1f))
        }
        if (!bottomDocked) {
            val importTarget = selectedGroup?.takeUnless { it == UNGROUPED_BRUSH_FILTER }
            FilledTonalButton(onClick = { onImportBrushes(importTarget) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text(importTarget?.let { "${tr("Import into", "导入到")} $it" } ?: tr("Import Brushes", "导入笔刷"), maxLines = 1)
            }
        }
    }
    reportBrush?.let { brush ->
        AlertDialog(
            onDismissRequest = { reportBrush = null },
            confirmButton = { TextButton(onClick = { reportBrush = null }) { Text(tr("Done", "完成")) } },
            title = { Text(brush.name) },
            text = {
                Column {
                    Text("${brush.format.label} · ${brush.compatibility.label}", color = Sage)
                    Spacer(Modifier.height(12.dp))
                    brush.report.forEach { Text("• $it", modifier = Modifier.padding(vertical = 3.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text(tr("The original source remains unchanged and can be reconverted by a future parser.", "原始文件保持不变，未来可使用更新的解析器重新转换。"), color = MutedInk, fontSize = 12.sp)
                }
            },
        )
    }
    editBrush?.let { brush ->
        BrushEditorDialog(
            brush,
            settings,
            onDismiss = { editBrush = null },
            onSave = { updated ->
                if (updated != brush) {
                    historyStore.record(brush)
                    historyRevision++
                    onUpdateBrush(updated)
                }
                editBrush = null
            },
        )
    }
    historyBrush?.let { selected ->
        val current = brushes.firstOrNull { it.id == selected.id } ?: selected
        val versions = remember(selected.id, historyRevision) { historyStore.versions(selected.id) }
        BrushVersionHistoryDialog(
            brush = current,
            versions = versions,
            onDismiss = { historyBrush = null },
            onRestore = { version ->
                historyStore.record(current)
                onUpdateBrush(historyStore.restore(current, version))
                historyRevision++
                historyBrush = null
            },
            onDelete = { version -> historyStore.delete(version.versionId); historyRevision++ },
        )
    }
    moveBrush?.let { selected ->
        val current = brushes.firstOrNull { it.id == selected.id } ?: selected
        BrushMoveToGroupDialog(
            brush = current,
            groups = groupTree,
            onDismiss = { moveBrush = null },
            onMove = { group ->
                val updated = BrushGroupOperations.assign(current, group)
                if (updated != current) onUpdateBrush(updated)
                selectedGroup = updated.groupName.takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                moveBrush = null
            },
        )
    }
    batchGroupOperation?.let { operation ->
        BrushBatchGroupDialog(
            count = selectedBrushIds.size,
            groups = groupTree,
            operation = operation,
            onDismiss = { batchGroupOperation = null },
            onApply = { group ->
                val updated = when (operation) {
                    BrushBatchGroupOperation.MOVE -> BrushGroupOperations.assignMany(brushes, selectedBrushIds, group)
                    BrushBatchGroupOperation.COPY -> BrushGroupOperations.duplicateMany(brushes, selectedBrushIds, group, copiedBrushSuffix)
                }
                onReplaceBrushes(updated)
                selectedGroup = BrushGroupOperations.normalize(group).takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                selectedBrushIds = emptySet()
                multiSelecting = false
                batchGroupOperation = null
            },
        )
    }
    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text(tr("Remove selected brushes?", "移除所选笔刷？")) },
            text = { Text(tr("This removes ${selectedBrushIds.size} presets from the brush library. Existing strokes keep their saved brush snapshots and will not change.", "这会从笔刷库移除 ${selectedBrushIds.size} 个预设。已有笔画会保留已保存的笔刷快照，不会发生变化。")) },
            confirmButton = {
                Button(onClick = {
                    selectedBrushIds.forEach(historyStore::clear)
                    historyRevision++
                    onReplaceBrushes(BrushGroupOperations.removeMany(brushes, selectedBrushIds))
                    selectedBrushIds = emptySet()
                    multiSelecting = false
                    confirmBatchDelete = false
                }) { Text(tr("Remove", "移除")) }
            },
            dismissButton = { TextButton(onClick = { confirmBatchDelete = false }) { Text(tr("Cancel", "取消")) } },
        )
    }
    if (manageGroups) {
        BrushGroupManagerDialog(
            groups = groupTree,
            onDismiss = { manageGroups = false },
            onRename = { oldName, newName ->
                val updated = BrushGroupOperations.rename(brushes, oldName, newName)
                onReplaceBrushes(updated)
                onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(BrushGroupOperations.renameRegisteredGroups(registeredGroups, oldName, newName), updated))
                val normalized = BrushGroupOperations.normalize(newName)
                selectedGroup = normalized.takeIf(String::isNotBlank) ?: UNGROUPED_BRUSH_FILTER
                manageGroups = false
            },
            onMove = { group, delta ->
                onReplaceBrushes(BrushGroupOperations.moveGroup(brushes, group, delta))
                onReplaceGroups(BrushGroupOperations.moveRegisteredGroup(registeredGroups, group, delta))
            },
            onCreate = { path ->
                val normalized = BrushGroupOperations.normalize(path)
                if (normalized.isNotBlank()) {
                    onReplaceGroups(BrushGroupOperations.reconcileRegisteredGroups(registeredGroups + normalized, brushes))
                    selectedGroup = normalized
                }
            },
        )
    }
}

@Composable
private fun BrushRow(name: String, color: Color, selected: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).border(if (selected) 2.dp else 1.dp, if (selected) Sage else OutlineSoft, RoundedCornerShape(10.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(52.dp).height(18.dp)) { drawLine(color, Offset(4f, size.height / 2), Offset(size.width - 4f, size.height / 2), strokeWidth = if (name.contains("Marker")) 9f else 3f, cap = StrokeCap.Round) }
        Spacer(Modifier.width(12.dp)); Text(name, color = if (selected) Sage else Ink)
    }
}

@Composable
private fun BrushMoveToGroupDialog(brush: ImportedBrush, groups: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var groupName by remember(brush.id) { mutableStateOf(brush.groupName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Move brush", "移动笔刷")) },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                    label = { Text(tr("Group", "分组")) },
                    supportingText = { Text(tr("Enter a new name or choose an existing group", "输入新名称或选择已有分组")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { FilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
                }
                TextButton(onClick = { onMove("") }) { Text(tr("Move to Ungrouped", "移到未分组")) }
            }
        },
        confirmButton = { Button(onClick = { onMove(groupName) }, enabled = BrushGroupOperations.normalize(groupName).isNotBlank()) { Text(tr("Move", "移动")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun BrushBatchGroupDialog(
    count: Int,
    groups: List<String>,
    operation: BrushBatchGroupOperation,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    var groupName by remember(operation) { mutableStateOf("") }
    val moving = operation == BrushBatchGroupOperation.MOVE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (moving) tr("Move selected brushes", "移动所选笔刷") else tr("Copy selected brushes", "复制所选笔刷")) },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Text("$count ${tr("brushes selected", "支笔刷已选择")}", color = MutedInk, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                    label = { Text(tr("Destination group", "目标分组")) },
                    supportingText = { Text(tr("Enter a new name or choose an existing group", "输入新名称或选择已有分组")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (groups.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 8.dp)) {
                    groups.forEach { group -> item(group) { FilterChip(selected = groupName.equals(group, true), onClick = { groupName = group }, label = { Text(group, maxLines = 1) }) } }
                }
                TextButton(onClick = { onApply("") }) { Text(if (moving) tr("Move to Ungrouped", "移到未分组") else tr("Copy to Ungrouped", "复制到未分组")) }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(groupName) }, enabled = BrushGroupOperations.normalize(groupName).isNotBlank()) {
                Text(if (moving) tr("Move selected", "移动所选") else tr("Copy selected", "复制所选"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun BrushGroupManagerDialog(groups: List<String>, onDismiss: () -> Unit, onRename: (String, String) -> Unit, onMove: (String, Int) -> Unit, onCreate: (String) -> Unit) {
    var selected by remember { mutableStateOf(groups.firstOrNull().orEmpty()) }
    LaunchedEffect(groups) { if (groups.none { it == selected }) selected = groups.firstOrNull().orEmpty() }
    var replacement by remember(selected) { mutableStateOf(selected) }
    var newGroupPath by remember { mutableStateOf("") }
    val normalizedNewGroup = BrushGroupOperations.normalize(newGroupPath)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Manage groups", "管理分组")) },
        text = {
            Column(Modifier.widthIn(min = 320.dp, max = 440.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGroupPath,
                        onValueChange = { newGroupPath = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                        label = { Text(tr("New group path", "新建分组路径")) },
                        supportingText = { Text(tr("Use / to create nested groups", "使用 / 创建嵌套分组")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "New brush group path" },
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onCreate(normalizedNewGroup); selected = normalizedNewGroup; newGroupPath = "" },
                        enabled = normalizedNewGroup.isNotBlank() && groups.none { it.equals(normalizedNewGroup, true) },
                    ) { Text(tr("Create", "创建")) }
                }
                Spacer(Modifier.height(8.dp))
                if (groups.isEmpty()) {
                    Text(tr("No groups yet. Create an empty group above or move a brush into a new path.", "还没有分组。可在上方创建空组，或将笔刷移动到新路径。"), color = MutedInk)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        groups.forEach { group -> item(group) { FilterChip(selected = selected == group, onClick = { selected = group }, label = { Text(group, maxLines = 1) }) } }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val selectedIndex = groups.indexOf(selected)
                        OutlinedButton(onClick = { onMove(selected, -1) }, enabled = selectedIndex > 0) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(tr("Earlier", "前移")) }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(onClick = { onMove(selected, 1) }, enabled = selectedIndex in 0 until groups.lastIndex) { Text(tr("Later", "后移")); Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.ArrowForward, null, Modifier.size(16.dp)) }
                    }
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it.take(BrushGroupOperations.MAX_PATH_LENGTH) },
                        label = { Text(tr("Rename selected group", "重命名所选分组")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(tr("Renaming to an existing group merges both groups.", "重命名为已有分组会合并两个分组。"), color = MutedInk, fontSize = 11.sp)
                    TextButton(onClick = { onRename(selected, "") }) { Text(tr("Remove group and keep brushes", "移除分组并保留笔刷")) }
                }
            }
        },
        confirmButton = { Button(onClick = { onRename(selected, replacement) }, enabled = selected.isNotBlank() && BrushGroupOperations.normalize(replacement).isNotBlank()) { Text(tr("Rename", "重命名")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

@Composable
private fun BrushVersionHistoryDialog(
    brush: ImportedBrush,
    versions: List<BrushVersion>,
    onDismiss: () -> Unit,
    onRestore: (BrushVersion) -> Unit,
    onDelete: (BrushVersion) -> Unit,
) {
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Version history", "版本历史")) },
        text = {
            Column(Modifier.widthIn(min = 340.dp, max = 480.dp)) {
                Text(brush.name, fontSize = 13.sp)
                Text(tr("Inkloom saves the previous preset before every brush edit.", "Inkloom 会在每次编辑笔刷前保存旧预设。"), color = MutedInk, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                if (versions.isEmpty()) {
                    Text(tr("No previous versions yet. Edit and save this brush to create one.", "还没有历史版本。编辑并保存此笔刷即可创建。"), color = MutedInk)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(versions, key = { it.versionId }) { version ->
                            OutlinedCard {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BrushTipPreview(version.snapshot, Modifier.size(42.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(version.snapshot.name, maxLines = 1, fontSize = 13.sp)
                                        Text(formatter.format(Date(version.savedAt)), color = MutedInk, fontSize = 10.sp)
                                        Text(
                                            "${version.snapshot.defaultSize.oneDecimal()} px · ${(version.snapshot.defaultOpacity * 100f).toInt()}% ${tr("opacity", "不透明度")} · ${version.snapshot.spacing.oneDecimal()} ${tr("spacing", "间距")}",
                                            color = MutedInk,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                        )
                                    }
                                    TextButton(onClick = { onRestore(version) }) { Text(tr("Restore", "恢复")) }
                                    IconButton(onClick = { onDelete(version) }) { Icon(Icons.Outlined.Delete, tr("Delete this brush version", "删除此笔刷版本")) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}

private fun Float.oneDecimal(): String = String.format(java.util.Locale.ROOT, "%.1f", this)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportedBrushRow(
    brush: ImportedBrush,
    selected: Boolean,
    multiSelecting: Boolean,
    multiSelected: Boolean,
    onToggleSelection: () -> Unit,
    onBeginSelection: () -> Unit,
    onSelect: () -> Unit,
    onReport: () -> Unit,
    onEdit: () -> Unit,
    onHistory: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onReorder: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dragDistance by remember(brush.id) { mutableFloatStateOf(0f) }
    val rowDescription = "${tr("Brush preset", "笔刷预设")} ${brush.name}"
    Card(
        modifier = Modifier.semantics { contentDescription = rowDescription }.combinedClickable(
            onClick = { if (multiSelecting) onToggleSelection() else onSelect() },
            onLongClick = onBeginSelection,
        ),
        colors = CardDefaults.cardColors(containerColor = if (selected || multiSelected) SageContainer else Workspace),
        border = if (selected || multiSelected) CardDefaults.outlinedCardBorder().copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(Sage)) else CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(8.dp).heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
            if (multiSelecting) {
                Icon(if (multiSelected) Icons.Default.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, if (multiSelected) tr("Selected", "已选择") else tr("Not selected", "未选择"), Modifier.size(24.dp), tint = if (multiSelected) Sage else MutedInk)
            } else {
                Icon(
                    Icons.Outlined.DragIndicator,
                    tr("Drag to reorder brush", "拖动调整笔刷顺序"),
                    Modifier.size(24.dp).pointerInput(brush.id) {
                        detectDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onDragEnd = {
                                val delta = (dragDistance / 56.dp.toPx()).toInt()
                                if (delta != 0) onReorder(delta)
                                dragDistance = 0f
                            },
                            onDragCancel = { dragDistance = 0f },
                        ) { change, amount -> change.consume(); dragDistance += amount.y }
                    },
                    tint = MutedInk,
                )
            }
            Spacer(Modifier.width(4.dp))
            BrushTipPreview(brush, Modifier.size(52.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(brush.name, maxLines = 1, fontSize = 13.sp)
                Text(listOf(brush.format.label, brush.groupName.takeIf(String::isNotBlank), brush.compatibility.label).filterNotNull().joinToString(" · "), color = MutedInk, fontSize = 10.sp, maxLines = 1)
                Canvas(Modifier.fillMaxWidth().height(12.dp)) { drawLine(Sage, Offset(2f, size.height / 2), Offset(size.width - 2f, size.height / 2), strokeWidth = (brush.defaultSize / 8f).coerceIn(2f, 10f), cap = StrokeCap.Round) }
            }
            if (!multiSelecting) {
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) { Icon(Icons.Outlined.Tune, tr("Edit brush", "编辑笔刷"), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onFavorite, modifier = Modifier.size(30.dp)) { Icon(if (brush.favorite) Icons.Default.Star else Icons.Outlined.StarBorder, if (brush.favorite) tr("Remove favorite", "取消收藏") else tr("Favorite", "收藏"), modifier = Modifier.size(18.dp), tint = if (brush.favorite) Sage else Ink) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.MoreVert, tr("Brush actions", "笔刷操作"), modifier = Modifier.size(18.dp)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(tr("Conversion report", "转换报告")) }, onClick = { menu = false; onReport() })
                        DropdownMenuItem(text = { Text(tr("Version history", "版本历史")) }, onClick = { menu = false; onHistory() })
                        DropdownMenuItem(text = { Text(tr("Move to group", "移到分组")) }, onClick = { menu = false; onMove() })
                        DropdownMenuItem(text = { Text(tr("Export Inkloom preset", "导出 Inkloom 预设")) }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text(if (brush.hidden) tr("Unhide", "取消隐藏") else tr("Hide", "隐藏")) }, onClick = { menu = false; onHide() })
                        DropdownMenuItem(text = { Text(tr("Remove from library", "从笔刷库移除")) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushTipPreview(brush: ImportedBrush, modifier: Modifier = Modifier) {
    val bitmap = remember(brush.tipPreviewPath) { brush.tipPreviewPath?.let { android.graphics.BitmapFactory.decodeFile(it) } }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, OutlineSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), brush.name, Modifier.fillMaxSize().padding(4.dp))
        else Canvas(Modifier.fillMaxSize().padding(7.dp)) { drawLine(Ink, Offset(2f, size.height * .7f), Offset(size.width - 2f, size.height * .3f), strokeWidth = 7f, cap = StrokeCap.Round) }
    }
}

@Composable
private fun BrushEditorDialog(brush: ImportedBrush, settings: AppSettings, onDismiss: () -> Unit, onSave: (ImportedBrush) -> Unit) {
    var draft by remember(brush.id) { mutableStateOf(brush) }
    var tagText by remember(brush.id) { mutableStateOf(brush.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { BrushTipPreview(draft, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Column { Text(draft.name, maxLines = 1); Text(tr("Brush Studio", "笔刷工作室"), color = MutedInk, fontSize = 11.sp) } } },
        text = {
            LazyColumn(Modifier.widthIn(min = 360.dp, max = 520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text(tr("Brush name", "笔刷名称")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(draft.groupName, { draft = draft.copy(groupName = it.take(BrushGroupOperations.MAX_PATH_LENGTH)) }, label = { Text(tr("Group path", "分组路径")) }, supportingText = { Text(tr("Use / for nested groups; leave empty for Ungrouped", "使用 / 创建嵌套分组；留空则归入“未分组”")) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(tagText, { tagText = it }, label = { Text(tr("Tags", "标签")) }, supportingText = { Text(tr("Separate tags with commas", "使用逗号分隔标签")) }, modifier = Modifier.fillMaxWidth()) }
                item { BrushTestArea(draft, settings) }
                item { BrushSettingSlider("Size", draft.defaultSize, .5f..400f) { draft = draft.copy(defaultSize = it) } }
                item { BrushSettingSlider("Stabilization", draft.stabilization, 0f..0.9f) { draft = draft.copy(stabilization = it) } }
                item { SettingSwitch("Adjust stabilization by speed", "Uses the imported CSP correction behavior when available.", draft.stabilizationBySpeed) { draft = draft.copy(stabilizationBySpeed = it) } }
                if (draft.stabilizationBySpeed) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.stabilizationBySpeedMode == StrokeStabilization.INCREASE_WHEN_SLOW, onClick = { draft = draft.copy(stabilizationBySpeedMode = StrokeStabilization.INCREASE_WHEN_SLOW) }, label = { Text(tr("More when slow", "慢速时增强")) }); FilterChip(selected = draft.stabilizationBySpeedMode == StrokeStabilization.REDUCE_WHEN_FAST, onClick = { draft = draft.copy(stabilizationBySpeedMode = StrokeStabilization.REDUCE_WHEN_FAST) }, label = { Text(tr("Less when fast", "快速时减弱")) }) } }
                item { BrushSettingSlider("Default opacity", draft.defaultOpacity, .01f..1f) { draft = draft.copy(defaultOpacity = it) } }
                item { SettingSwitch("Use as eraser", "Selects the eraser tool and uses this tip shape to remove ink.", draft.isEraser) { draft = draft.copy(isEraser = it) } }
                item { SettingSwitch("Pressure controls size", "Uses the imported CSP curve when available.", draft.sizePressureEnabled) { draft = draft.copy(sizePressureEnabled = it) } }
                if (draft.sizePressureEnabled) item { BrushSettingSlider("Minimum size", draft.sizePressureMinimum, 0f..1f) { draft = draft.copy(sizePressureMinimum = it) } }
                if (draft.sizePressureEnabled) item { PressureCurveEditor("Size pressure", draft.sizePressureCurve) { draft = draft.copy(sizePressureCurve = it) } }
                item { SecondaryDynamicsEditor("Size", draft.sizeSecondaryDynamics) { draft = draft.copy(sizeSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls opacity", null, draft.opacityPressureEnabled) { draft = draft.copy(opacityPressureEnabled = it) } }
                if (draft.opacityPressureEnabled) item { BrushSettingSlider("Minimum opacity", draft.opacityPressureMinimum, 0f..1f) { draft = draft.copy(opacityPressureMinimum = it) } }
                if (draft.opacityPressureEnabled) item { PressureCurveEditor("Opacity pressure", draft.opacityPressureCurve) { draft = draft.copy(opacityPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Opacity", draft.opacitySecondaryDynamics) { draft = draft.copy(opacitySecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls flow", null, draft.flowPressureEnabled) { draft = draft.copy(flowPressureEnabled = it) } }
                if (draft.flowPressureEnabled) item { BrushSettingSlider("Minimum flow", draft.flowPressureMinimum, 0f..1f) { draft = draft.copy(flowPressureMinimum = it) } }
                if (draft.flowPressureEnabled) item { PressureCurveEditor("Flow pressure", draft.flowPressureCurve) { draft = draft.copy(flowPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Flow", draft.flowSecondaryDynamics) { draft = draft.copy(flowSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls spacing", "Imported from CSP Interval dynamics when available.", draft.spacingPressureEnabled) { draft = draft.copy(spacingPressureEnabled = it) } }
                if (draft.spacingPressureEnabled) item { BrushSettingSlider("Minimum spacing", draft.spacingPressureMinimum, 0f..1f) { draft = draft.copy(spacingPressureMinimum = it) } }
                if (draft.spacingPressureEnabled) item { PressureCurveEditor("Spacing pressure", draft.spacingPressureCurve) { draft = draft.copy(spacingPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Spacing", draft.spacingSecondaryDynamics) { draft = draft.copy(spacingSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls thickness", null, draft.thicknessPressureEnabled) { draft = draft.copy(thicknessPressureEnabled = it) } }
                if (draft.thicknessPressureEnabled) item { BrushSettingSlider("Minimum thickness", draft.thicknessPressureMinimum, 0f..1f) { draft = draft.copy(thicknessPressureMinimum = it) } }
                if (draft.thicknessPressureEnabled) item { PressureCurveEditor("Thickness pressure", draft.thicknessPressureCurve) { draft = draft.copy(thicknessPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Thickness", draft.thicknessSecondaryDynamics) { draft = draft.copy(thicknessSecondaryDynamics = it) } }
                item { SettingSwitch("Pressure controls blur", null, draft.blurPressureEnabled) { draft = draft.copy(blurPressureEnabled = it) } }
                if (draft.blurPressureEnabled) item { BrushSettingSlider("Minimum blur", draft.blurPressureMinimum, 0f..1f) { draft = draft.copy(blurPressureMinimum = it) } }
                if (draft.blurPressureEnabled) item { PressureCurveEditor("Blur pressure", draft.blurPressureCurve) { draft = draft.copy(blurPressureCurve = it) } }
                item { SecondaryDynamicsEditor("Blur", draft.blurSecondaryDynamics) { draft = draft.copy(blurSecondaryDynamics = it) } }
                item { Text(tr("TAPER", "收尖"), color = Sage, fontSize = 11.sp) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { BrushTaperMode.entries.forEach { mode -> item { FilterChip(selected = draft.taperMode == mode, onClick = { val toDistance = mode == BrushTaperMode.DISTANCE && draft.taperMode != BrushTaperMode.DISTANCE; val fromDistance = mode != BrushTaperMode.DISTANCE && draft.taperMode == BrushTaperMode.DISTANCE; draft = draft.copy(taperMode = mode, taperStartEnabled = if (mode == BrushTaperMode.FADE) false else draft.taperStartEnabled, taperStartLength = when { toDistance -> draft.taperStartLength * 100f; fromDistance -> draft.taperStartLength / 100f; else -> draft.taperStartLength }, taperEndLength = when { toDistance -> draft.taperEndLength * 100f; fromDistance -> draft.taperEndLength / 100f; else -> draft.taperEndLength }) }, label = { Text(localizedTaperMode(mode)) }) } } } }
                if (draft.taperMode != BrushTaperMode.FADE) item { SettingSwitch("Starting taper", "Gradually grows from the configured minimum over the start of the stroke.", draft.taperStartEnabled) { draft = draft.copy(taperStartEnabled = it) } }
                if (draft.taperStartEnabled) item { BrushSettingSlider("Start length", draft.taperStartLength, if (draft.taperMode == BrushTaperMode.DISTANCE) 1f..400f else .01f..1f) { draft = draft.copy(taperStartLength = it) } }
                if (draft.taperStartEnabled && draft.taperMode == BrushTaperMode.DISTANCE) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.taperStartLengthUnit == 0, onClick = { draft = draft.copy(taperStartLengthUnit = 0) }, label = { Text("px") }); FilterChip(selected = draft.taperStartLengthUnit == 2, onClick = { draft = draft.copy(taperStartLengthUnit = 2) }, label = { Text("mm") }) } }
                if (draft.taperStartEnabled) item { BrushSettingSlider("Start minimum size", draft.taperStartMinimum, 0f..1f) { draft = draft.copy(taperStartMinimum = it) } }
                item { SettingSwitch(if (draft.taperMode == BrushTaperMode.FADE) "Fade" else "Ending taper", if (draft.taperMode == BrushTaperMode.FADE) "Fades from full size toward the configured minimum." else "Gradually narrows toward the configured minimum at stroke end.", draft.taperEndEnabled) { draft = draft.copy(taperEndEnabled = it) } }
                if (draft.taperEndEnabled) item { BrushSettingSlider(if (draft.taperMode == BrushTaperMode.FADE) "Fade length" else "End length", draft.taperEndLength, if (draft.taperMode == BrushTaperMode.DISTANCE) 1f..400f else .01f..1f) { draft = draft.copy(taperEndLength = it) } }
                if (draft.taperEndEnabled && draft.taperMode == BrushTaperMode.DISTANCE) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.taperEndLengthUnit == 0, onClick = { draft = draft.copy(taperEndLengthUnit = 0) }, label = { Text("px") }); FilterChip(selected = draft.taperEndLengthUnit == 2, onClick = { draft = draft.copy(taperEndLengthUnit = 2) }, label = { Text("mm") }) } }
                if (draft.taperEndEnabled) item { BrushSettingSlider("End minimum size", draft.taperEndMinimum, 0f..1f) { draft = draft.copy(taperEndMinimum = it) } }
                item { BrushGapModeEditor(tr("Brush-tip gap", "笔尖间隔"), draft.spacingMode) { draft = draft.copy(spacingMode = it) } }
                if (draft.spacingMode == CspBrushGap.FIXED) item { BrushSettingSlider("Spacing", draft.spacing, .01f..2f) { draft = draft.copy(spacing = it) } }
                item { BrushSettingSlider("Flow", draft.flow, 0f..1f) { draft = draft.copy(flow = it) } }
                item { BrushSettingSlider("Hardness", draft.hardness, 0f..1f) { draft = draft.copy(hardness = it) } }
                item {
                    Text(tr("Anti-alias", "抗锯齿"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(CspAntiAlias.NONE, CspAntiAlias.WEAK, CspAntiAlias.MEDIUM, CspAntiAlias.STRONG).forEach { mode ->
                            item { FilterChip(selected = draft.antiAliasMode == mode, onClick = { draft = draft.copy(antiAliasMode = mode) }, label = { Text(when (mode) { CspAntiAlias.NONE -> tr("Off", "关闭"); CspAntiAlias.WEAK -> tr("Weak", "弱"); CspAntiAlias.STRONG -> tr("Strong", "强"); else -> tr("Medium", "中") }) }) }
                        }
                    }
                }
                item { BrushSettingSlider("Brush blur", draft.blur, 0f..80f) { draft = draft.copy(blur = it) } }
                if (draft.blur > 0f) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.blurUnit == 0, onClick = { draft = draft.copy(blurUnit = 0) }, label = { Text(tr("Blur px", "模糊 px")) }); FilterChip(selected = draft.blurUnit == 2, onClick = { draft = draft.copy(blurUnit = 2) }, label = { Text(tr("Blur mm", "模糊 mm")) }) } }
                item { SettingSwitch("Link blur to brush size", "Scales the imported CSP blur when brush size changes.", draft.blurLinkedToSize) { draft = draft.copy(blurLinkedToSize = it) } }
                if (draft.grainPreviewPath != null) item { BrushGrainPreview(draft.grainPreviewPath!!, draft.grainDensity) }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture density", draft.grainDensity, 0f..1f) { draft = draft.copy(grainDensity = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture scale", draft.grainScale, 1f..400f) { draft = draft.copy(grainScale = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture rotation", draft.grainRotationDegrees, -180f..180f) { draft = draft.copy(grainRotationDegrees = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture brightness", draft.grainBrightness, -100f..100f) { draft = draft.copy(grainBrightness = it) } }
                if (draft.grainPreviewPath != null) item { BrushSettingSlider("Paper texture contrast", draft.grainContrast, -100f..100f) { draft = draft.copy(grainContrast = it) } }
                if (draft.grainPreviewPath != null) item { SettingSwitch("Invert paper texture", null, draft.grainReversed) { draft = draft.copy(grainReversed = it) } }
                if (draft.grainPreviewPath != null) item { SettingSwitch("Emphasize paper texture", null, draft.grainEmphasized) { draft = draft.copy(grainEmphasized = it) } }
                if (draft.grainPreviewPath != null) item { SettingSwitch("Jitter grain origin per stroke", "Keeps each stroke deterministic while avoiding an identical repeated grain start.", draft.grainOffsetJitter) { draft = draft.copy(grainOffsetJitter = it) } }
                if (draft.grainPreviewPath != null) item { BrushTextureCompositeEditor(draft.grainCompositeMode) { draft = draft.copy(grainCompositeMode = it) } }
                item { BrushSettingSlider("Thickness", draft.thickness, .05f..2f) { draft = draft.copy(thickness = it) } }
                item { SettingSwitch("Vertical thickness", "Applies thickness along the imported tip's vertical axis.", draft.thicknessVertical) { draft = draft.copy(thicknessVertical = it) } }
                item { SettingSwitch("Ribbon stroke", "Preserves the tip aspect ratio and continuously follows the stroke direction.", draft.ribbon) { draft = draft.copy(ribbon = it) } }
                item { BrushTipFlipModeEditor(tr("Horizontal tip flip", "笔尖水平翻转"), draft.tipFlipHorizontalMode) { draft = draft.copy(tipFlipHorizontalMode = it) } }
                item { BrushTipFlipModeEditor(tr("Vertical tip flip", "笔尖垂直翻转"), draft.tipFlipVerticalMode) { draft = draft.copy(tipFlipVerticalMode = it) } }
                item { SettingSwitch(tr("Blend overlapping tips with Darken", "以变暗方式叠加笔尖"), tr("Matches CSP's per-stroke brush-tip overlap mode.", "匹配 CSP 的单笔画笔尖叠加模式。"), draft.blendTipsWithDarken) { draft = draft.copy(blendTipsWithDarken = it) } }
                item { BrushSettingSlider("Rotation", draft.rotationDegrees, -180f..180f) { draft = draft.copy(rotationDegrees = it) } }
                item { SettingSwitch("Follow stylus orientation", "Uses the pen direction reported by Android.", draft.followStylusOrientation) { draft = draft.copy(followStylusOrientation = it) } }
                item { SettingSwitch("Follow stroke direction", "Rotates the tip along the drawn path.", draft.followStrokeDirection) { draft = draft.copy(followStrokeDirection = it) } }
                item { BrushSettingSlider("Random rotation", draft.randomRotationDegrees, 0f..180f) { draft = draft.copy(randomRotationDegrees = it) } }
                item { BrushSettingSlider("Random start rotation", draft.strokeRotationJitterDegrees, 0f..180f) { draft = draft.copy(strokeRotationJitterDegrees = it) } }
                item { BrushSettingSlider("Stamp count", draft.stampCount.toFloat(), 1f..16f) { draft = draft.copy(stampCount = it.toInt().coerceIn(1, 16)) } }
                item { BrushSettingSlider("Stamp count jitter", draft.stampCountJitter, 0f..1f) { draft = draft.copy(stampCountJitter = it) } }
                item { SettingSwitch(tr("Continuous spraying", "连续喷涂"), tr("Holding the stylus in one place keeps depositing the brush tip, matching CSP slow-stroke density behavior.", "笔尖停留时继续叠加笔刷印章，匹配 CSP 慢速笔画的浓淡行为。"), draft.continuousPlot) { draft = draft.copy(continuousPlot = it) } }
                item { BrushSettingSlider(tr("Tip hue jitter", "笔尖色相抖动"), draft.colorJitterHueDegrees, -180f..180f) { draft = draft.copy(colorJitterHueDegrees = it) } }
                if (draft.colorJitterHueDegrees != 0f) item { ColorDynamicsEditor(tr("Hue jitter", "色相抖动"), draft.colorJitterHueDynamics) { draft = draft.copy(colorJitterHueDynamics = it) } }
                item { BrushSettingSlider(tr("Tip saturation jitter", "笔尖饱和度抖动"), draft.colorJitterSaturation, -100f..100f) { draft = draft.copy(colorJitterSaturation = it) } }
                if (draft.colorJitterSaturation != 0f) item { ColorDynamicsEditor(tr("Saturation jitter", "饱和度抖动"), draft.colorJitterSaturationDynamics) { draft = draft.copy(colorJitterSaturationDynamics = it) } }
                item { BrushSettingSlider(tr("Tip brightness jitter", "笔尖明度抖动"), draft.colorJitterValue, -100f..100f) { draft = draft.copy(colorJitterValue = it) } }
                if (draft.colorJitterValue != 0f) item { ColorDynamicsEditor(tr("Brightness jitter", "明度抖动"), draft.colorJitterValueDynamics) { draft = draft.copy(colorJitterValueDynamics = it) } }
                item { SettingSwitch("Watercolor approximation", "Uses translucent layered stamps.", draft.watercolor) { draft = draft.copy(watercolor = it) } }
                item { BrushSettingSlider("Color mix", draft.colorMix, 0f..1f) { draft = draft.copy(colorMix = it) } }
                if (draft.watercolor) item { SettingSwitch("Pressure controls color mix", null, draft.colorMixPressureEnabled) { draft = draft.copy(colorMixPressureEnabled = it) } }
                if (draft.watercolor && draft.colorMixPressureEnabled) item { BrushSettingSlider("Minimum color mix", draft.colorMixPressureMinimum, 0f..1f) { draft = draft.copy(colorMixPressureMinimum = it) } }
                if (draft.watercolor && draft.colorMixPressureEnabled) item { PressureCurveEditor("Color mix pressure", draft.colorMixPressureCurve) { draft = draft.copy(colorMixPressureCurve = it) } }
                if (draft.watercolor) item { SecondaryDynamicsEditor("Color mix", draft.colorMixSecondaryDynamics) { draft = draft.copy(colorMixSecondaryDynamics = it) } }
                item { BrushSettingSlider("Paint mix", draft.paintMix, 0f..1f) { draft = draft.copy(paintMix = it) } }
                if (draft.watercolor) item { SettingSwitch("Pressure controls paint load", null, draft.paintMixPressureEnabled) { draft = draft.copy(paintMixPressureEnabled = it) } }
                if (draft.watercolor && draft.paintMixPressureEnabled) item { BrushSettingSlider("Minimum paint load", draft.paintMixPressureMinimum, 0f..1f) { draft = draft.copy(paintMixPressureMinimum = it) } }
                if (draft.watercolor && draft.paintMixPressureEnabled) item { PressureCurveEditor("Paint load pressure", draft.paintMixPressureCurve) { draft = draft.copy(paintMixPressureCurve = it) } }
                if (draft.watercolor) item { SecondaryDynamicsEditor("Paint load", draft.paintMixSecondaryDynamics) { draft = draft.copy(paintMixSecondaryDynamics = it) } }
                item { SettingSwitch("Watercolor edge", "Adds the CSP-style translucent edge pass before the main stamp.", draft.waterEdgeEnabled) { draft = draft.copy(waterEdgeEnabled = it) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge radius", draft.waterEdgeRadius, 0f..80f) { draft = draft.copy(waterEdgeRadius = it) } }
                if (draft.waterEdgeEnabled) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.waterEdgeRadiusUnit == 0, onClick = { draft = draft.copy(waterEdgeRadiusUnit = 0) }, label = { Text("px") }); FilterChip(selected = draft.waterEdgeRadiusUnit == 2, onClick = { draft = draft.copy(waterEdgeRadiusUnit = 2) }, label = { Text("mm") }) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge opacity", draft.waterEdgeOpacity, 0f..1f) { draft = draft.copy(waterEdgeOpacity = it) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge darkness", draft.waterEdgeDarkness, 0f..1f) { draft = draft.copy(waterEdgeDarkness = it) } }
                if (draft.waterEdgeEnabled) item { BrushSettingSlider("Edge blur", draft.waterEdgeBlur, 0f..80f) { draft = draft.copy(waterEdgeBlur = it) } }
                if (draft.waterEdgeEnabled && draft.waterEdgeBlur > 0f) item { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = draft.waterEdgeBlurUnit == 0, onClick = { draft = draft.copy(waterEdgeBlurUnit = 0) }, label = { Text("Blur px") }); FilterChip(selected = draft.waterEdgeBlurUnit == 2, onClick = { draft = draft.copy(waterEdgeBlurUnit = 2) }, label = { Text("Blur mm") }) } }
                item { SettingSwitch("Spray/scatter", null, draft.spray) { draft = draft.copy(spray = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle size", draft.sprayParticleSize, .01f..400f) { draft = draft.copy(sprayParticleSize = it) } }
                if (draft.spray) item { SettingSwitch("Particle size follows brush", "When enabled, particle size is a percentage of the current brush size.", draft.sprayParticleSizeSyncBrushSize) { draft = draft.copy(sprayParticleSizeSyncBrushSize = it) } }
                if (draft.spray) item { SettingSwitch("Pressure controls particle size", null, draft.sprayParticleSizePressureEnabled) { draft = draft.copy(sprayParticleSizePressureEnabled = it) } }
                if (draft.spray && draft.sprayParticleSizePressureEnabled) item { BrushSettingSlider("Minimum particle size", draft.sprayParticleSizePressureMinimum, 0f..1f) { draft = draft.copy(sprayParticleSizePressureMinimum = it) } }
                if (draft.spray && draft.sprayParticleSizePressureEnabled) item { PressureCurveEditor("Particle size pressure", draft.sprayParticleSizePressureCurve) { draft = draft.copy(sprayParticleSizePressureCurve = it) } }
                if (draft.spray) item { SecondaryDynamicsEditor("Particle size", draft.sprayParticleSizeSecondaryDynamics) { draft = draft.copy(sprayParticleSizeSecondaryDynamics = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle density", draft.sprayDensity, 1f..100f) { draft = draft.copy(sprayDensity = it) } }
                if (draft.spray) item { SettingSwitch("Pressure controls particle density", null, draft.sprayDensityPressureEnabled) { draft = draft.copy(sprayDensityPressureEnabled = it) } }
                if (draft.spray && draft.sprayDensityPressureEnabled) item { BrushSettingSlider("Minimum particle density", draft.sprayDensityPressureMinimum, 0f..1f) { draft = draft.copy(sprayDensityPressureMinimum = it) } }
                if (draft.spray && draft.sprayDensityPressureEnabled) item { PressureCurveEditor("Particle density pressure", draft.sprayDensityPressureCurve) { draft = draft.copy(sprayDensityPressureCurve = it) } }
                if (draft.spray) item { SecondaryDynamicsEditor("Particle density", draft.sprayDensitySecondaryDynamics) { draft = draft.copy(sprayDensitySecondaryDynamics = it) } }
                if (draft.spray) item { BrushSettingSlider("Spray center bias", draft.sprayBias, -100f..100f) { draft = draft.copy(sprayBias = it) } }
                if (draft.spray) item { BrushSettingSlider("Scatter spread", draft.spraySpread, 0f..4f) { draft = draft.copy(spraySpread = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle rotation", draft.sprayParticleRotationDegrees, -180f..180f) { draft = draft.copy(sprayParticleRotationDegrees = it) } }
                if (draft.spray) item { SettingSwitch("Particles follow stylus", "Uses the independent CSP particle-direction setting.", draft.sprayParticleFollowStylusOrientation) { draft = draft.copy(sprayParticleFollowStylusOrientation = it) } }
                if (draft.spray) item { SettingSwitch("Particles follow stroke", "Aligns particles with the local path direction.", draft.sprayParticleFollowStrokeDirection) { draft = draft.copy(sprayParticleFollowStrokeDirection = it) } }
                if (draft.spray) item { BrushSettingSlider("Particle random rotation", draft.sprayParticleRandomRotationDegrees, 0f..180f) { draft = draft.copy(sprayParticleRandomRotationDegrees = it) } }
                item { SettingSwitch("Dual brush", "Uses the imported secondary tip as an independent stamp stream.", draft.dualBrush) { draft = draft.copy(dualBrush = it) } }
                if (draft.dualBrush && draft.dualTipPreviewPaths.isNotEmpty()) item { BrushTipPreview(tr("Secondary tip · ${draft.dualTipPreviewPaths.size} material${if (draft.dualTipPreviewPaths.size == 1) "" else "s"}", "辅助笔尖 · ${draft.dualTipPreviewPaths.size} 个素材"), draft.dualTipPreviewPaths.first()) }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary size", draft.dualSizeScale, .01f..4f) { draft = draft.copy(dualSizeScale = it) } }
                if (draft.dualBrush) item { BrushGapModeEditor(tr("Secondary brush-tip gap", "副笔尖间隔"), draft.dualSpacingMode) { draft = draft.copy(dualSpacingMode = it) } }
                if (draft.dualBrush && draft.dualSpacingMode == CspBrushGap.FIXED) item { BrushSettingSlider("Secondary spacing", draft.dualSpacing, .01f..2f) { draft = draft.copy(dualSpacing = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary flow", draft.dualFlow, 0f..1f) { draft = draft.copy(dualFlow = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary hardness", draft.dualHardness, 0f..1f) { draft = draft.copy(dualHardness = it) } }
                if (draft.dualBrush) item {
                    Text(tr("Secondary anti-alias", "辅助笔尖抗锯齿"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(CspAntiAlias.NONE, CspAntiAlias.WEAK, CspAntiAlias.MEDIUM, CspAntiAlias.STRONG).forEach { mode ->
                            item { FilterChip(selected = draft.dualAntiAliasMode == mode, onClick = { draft = draft.copy(dualAntiAliasMode = mode) }, label = { Text(when (mode) { CspAntiAlias.NONE -> tr("Off", "关闭"); CspAntiAlias.WEAK -> tr("Weak", "弱"); CspAntiAlias.STRONG -> tr("Strong", "强"); else -> tr("Medium", "中") }) }) }
                        }
                    }
                }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary thickness", draft.dualThickness, .05f..2f) { draft = draft.copy(dualThickness = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary vertical thickness", null, draft.dualThicknessVertical) { draft = draft.copy(dualThicknessVertical = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary rotation", draft.dualRotationDegrees, -180f..180f) { draft = draft.copy(dualRotationDegrees = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary follows stylus", null, draft.dualFollowStylusOrientation) { draft = draft.copy(dualFollowStylusOrientation = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary follows stroke", null, draft.dualFollowStrokeDirection) { draft = draft.copy(dualFollowStrokeDirection = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary random rotation", draft.dualRandomRotationDegrees, 0f..180f) { draft = draft.copy(dualRandomRotationDegrees = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary random start rotation", draft.dualStrokeRotationJitterDegrees, 0f..180f) { draft = draft.copy(dualStrokeRotationJitterDegrees = it) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary stamp count", draft.dualStampCount.toFloat(), 1f..16f) { draft = draft.copy(dualStampCount = it.toInt().coerceIn(1, 16)) } }
                if (draft.dualBrush) item { BrushSettingSlider("Secondary count jitter", draft.dualStampCountJitter, 0f..1f) { draft = draft.copy(dualStampCountJitter = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary Ribbon", null, draft.dualRibbon) { draft = draft.copy(dualRibbon = it) } }
                if (draft.dualBrush) item { BrushTipFlipModeEditor(tr("Secondary horizontal flip", "辅助笔尖水平翻转"), draft.dualTipFlipHorizontalMode) { draft = draft.copy(dualTipFlipHorizontalMode = it) } }
                if (draft.dualBrush) item { BrushTipFlipModeEditor(tr("Secondary vertical flip", "辅助笔尖垂直翻转"), draft.dualTipFlipVerticalMode) { draft = draft.copy(dualTipFlipVerticalMode = it) } }
                if (draft.dualBrush) item { SettingSwitch(tr("Darken secondary tip overlaps", "辅助笔尖以变暗方式叠加"), null, draft.dualBlendTipsWithDarken) { draft = draft.copy(dualBlendTipsWithDarken = it) } }
                if (draft.dualBrush) item { SettingSwitch("Secondary spray/scatter", null, draft.dualSpray) { draft = draft.copy(dualSpray = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle size", draft.dualSprayParticleSize, .01f..400f) { draft = draft.copy(dualSprayParticleSize = it) } }
                if (draft.dualBrush && draft.dualSpray) item { SettingSwitch("Secondary particles follow brush", null, draft.dualSprayParticleSizeSyncBrushSize) { draft = draft.copy(dualSprayParticleSizeSyncBrushSize = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle density", draft.dualSprayDensity, 1f..100f) { draft = draft.copy(dualSprayDensity = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary center bias", draft.dualSprayBias, -100f..100f) { draft = draft.copy(dualSprayBias = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary scatter spread", draft.dualSpraySpread, 0f..4f) { draft = draft.copy(dualSpraySpread = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle rotation", draft.dualSprayParticleRotationDegrees, -180f..180f) { draft = draft.copy(dualSprayParticleRotationDegrees = it) } }
                if (draft.dualBrush && draft.dualSpray) item { SettingSwitch("Secondary particles follow stylus", null, draft.dualSprayParticleFollowStylusOrientation) { draft = draft.copy(dualSprayParticleFollowStylusOrientation = it) } }
                if (draft.dualBrush && draft.dualSpray) item { SettingSwitch("Secondary particles follow stroke", null, draft.dualSprayParticleFollowStrokeDirection) { draft = draft.copy(dualSprayParticleFollowStrokeDirection = it) } }
                if (draft.dualBrush && draft.dualSpray) item { BrushSettingSlider("Secondary particle random rotation", draft.dualSprayParticleRandomRotationDegrees, 0f..180f) { draft = draft.copy(dualSprayParticleRandomRotationDegrees = it) } }
                if (draft.dualBrush) item {
                    val mode = CspDualBrushMode.fromCode(draft.dualCompositeMode)
                    Text(
                        tr(
                            "CSP composite: ${mode?.displayName ?: "Unknown ${draft.dualCompositeMode}"}${if (mode?.nativeBlendSupported == true) " · rendered in an isolated stroke layer" else " · retained with Normal fallback"}${if (draft.dualApplyRgb) " · secondary RGB flag retained" else " · alpha only"}",
                            "CSP 合成：${mode?.displayName ?: "未知 ${draft.dualCompositeMode}"}${if (mode?.nativeBlendSupported == true) " · 在独立笔画层中渲染" else " · 已保留并使用普通模式回退"}${if (draft.dualApplyRgb) " · 已保留辅助 RGB 标记" else " · 仅透明度"}",
                        ),
                        color = MutedInk,
                        fontSize = 10.sp,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(draft.copy(name = draft.name.trim().ifBlank { brush.name }, groupName = BrushGroupOperations.normalize(draft.groupName), tags = tagText.split(',').map(String::trim).filter(String::isNotBlank).distinct().take(24))) }) { Text(tr("Save preset", "保存预设")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}

@Composable
private fun BrushTestArea(brush: ImportedBrush, settings: AppSettings) {
    var strokes by remember(brush.id) { mutableStateOf(emptyList<InkStroke>()) }
    var canvasView by remember(brush.id) { mutableStateOf<InkCanvasView?>(null) }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, OutlineSoft, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("LIVE TEST AREA", "实时试笔区"), color = Sage, fontSize = 11.sp)
                Text(tr("Uses the current Inkloom brush engine. Test strokes are never added to the note.", "使用当前 Inkloom 笔刷引擎；试笔不会写入笔记。"), color = MutedInk, fontSize = 10.sp)
            }
            IconButton(onClick = { canvasView?.undo() }, enabled = strokes.isNotEmpty(), modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Undo, tr("Undo test stroke", "撤销试笔"), Modifier.size(18.dp)) }
            IconButton(onClick = { strokes = emptyList(); canvasView?.setDocumentStrokes(emptyList(), "brush-test:${brush.id}:clear") }, enabled = strokes.isNotEmpty(), modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.DeleteSweep, tr("Clear test area", "清空试笔区"), Modifier.size(18.dp)) }
        }
        AndroidView(
            factory = { context -> InkCanvasView(context).also { view ->
                canvasView = view
                view.activeLayerId = LayerIds.INK
                view.pageModelWidth = 900f
                view.pageModelHeight = 240f
                view.onStrokeCommitted = { stroke -> strokes = strokes + stroke }
                view.onStrokeRemoved = { id -> strokes = strokes.filterNot { it.id == id } }
                view.onStrokesReplaced = { removed, replacements, _ -> strokes = strokes.filterNot { it.id in removed.toSet() } + replacements }
            } },
            update = { view ->
                canvasView = view
                view.applyBrushConfiguration(EditorTool.PEN, settings, brush, brush.defaultSize, brush.defaultOpacity, AndroidColor.rgb(17, 17, 17), EraserPreset.NORMAL_FREE)
                view.activeLayerId = LayerIds.INK
                view.pageModelWidth = 900f
                view.pageModelHeight = 240f
                view.setDocumentStrokes(strokes, "brush-test:${brush.id}")
                view.setProtectedStrokeIds(emptySet())
                view.setProtectedLayerIds(emptySet())
                view.onStrokeCommitted = { stroke -> strokes = strokes + stroke }
                view.onStrokeRemoved = { id -> strokes = strokes.filterNot { it.id == id } }
                view.onStrokesReplaced = { removed, replacements, _ -> strokes = strokes.filterNot { it.id in removed.toSet() } + replacements }
            },
            modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
        )
        Text(tr("Stylus draws here. Finger input follows the app's finger-drawing setting.", "请用触控笔在此试画；手指输入遵循应用的“手指书写”设置。"), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun BrushSettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column { Row { Text(localizedBrushLabel(label), Modifier.weight(1f), fontSize = 12.sp); Text(if (range.endInclusive > 10f) "%.1f".format(value) else "%.2f".format(value), color = MutedInk, fontSize = 11.sp) }; Slider(value, onValue, valueRange = range) }
}

@Composable
private fun BrushTipFlipModeEditor(label: String, value: Int, onValue: (Int) -> Unit) {
    val modes = listOf(
        BrushTipFlip.OFF to tr("Off", "关闭"),
        BrushTipFlip.FLIP to tr("Flip", "翻转"),
        BrushTipFlip.RANDOM to tr("Random", "随机"),
        BrushTipFlip.ON_REVERSED_SEQUENCE to tr("On reverse", "反向序列时"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MutedInk, fontSize = 11.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(modes, key = { it.first }) { (mode, text) ->
                FilterChip(selected = value == mode, onClick = { onValue(mode) }, label = { Text(text) })
            }
        }
    }
}

@Composable
private fun BrushTextureCompositeEditor(value: Int, onValue: (Int) -> Unit) {
    val modes = listOf(
        CspTextureComposite.MULTIPLY to tr("Multiply / legacy", "正片叠底 / 兼容"),
        CspTextureComposite.COMPARE to tr("Compare (approx.)", "比较（近似）"),
        CspTextureComposite.HEIGHT to tr("Height", "高度"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Paper texture mode", "纸张纹理模式"), color = MutedInk, fontSize = 11.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(modes, key = { it.first }) { (mode, label) ->
                FilterChip(selected = value == mode, onClick = { onValue(mode) }, label = { Text(label) })
            }
        }
        if (value !in modes.map { it.first }) Text(
            tr("Imported mode $value is retained; edit only after comparing the preview.", "已保留导入模式 $value；请在对比预览后再修改。"),
            color = MutedInk,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun BrushGapModeEditor(label: String, value: Int, onValue: (Int) -> Unit) {
    val modes = listOf(
        CspBrushGap.FIXED to tr("Fixed", "固定"),
        CspBrushGap.WIDE to tr("Wide", "宽"),
        CspBrushGap.NORMAL to tr("Normal", "普通"),
        CspBrushGap.NARROW to tr("Narrow", "窄"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = MutedInk, fontSize = 11.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(modes, key = { it.first }) { (mode, text) ->
                FilterChip(selected = CspBrushGap.normalize(value) == mode, onClick = { onValue(mode) }, label = { Text(text) })
            }
        }
        if (value != CspBrushGap.FIXED) Text(tr("Automatic gap ignores the saved Fixed percentage.", "自动间隔不会使用已保存的固定百分比。"), color = MutedInk, fontSize = 10.sp)
    }
}

@Composable
private fun SecondaryDynamicsEditor(label: String, value: BrushSecondaryDynamics, onValue: (BrushSecondaryDynamics) -> Unit) {
    val activeSources = mutableListOf<String>()
    if (value.tiltEnabled) activeSources += localizedBrushLabel("Tilt")
    if (value.velocityEnabled) activeSources += localizedBrushLabel("Velocity")
    if (value.randomEnabled) activeSources += localizedBrushLabel("Random")
    val noSecondarySource = tr("No secondary source", "无辅助输入源")
    var expanded by remember(label) { mutableStateOf(activeSources.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, OutlineSoft, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(tr("$label input dynamics", "${localizedBrushLabel(label)}输入动态"), color = Sage, fontSize = 11.sp); Text(activeSources.joinToString().ifBlank { noSecondarySource }, color = MutedInk, fontSize = 10.sp) }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) tr("Collapse", "折叠") else tr("Expand", "展开"))
        }
        if (expanded) {
            SettingSwitch("Tilt", null, value.tiltEnabled) { onValue(value.copy(tiltEnabled = it)) }
            if (value.tiltEnabled) BrushSettingSlider("Tilt minimum", value.tiltMinimum, 0f..1f) { onValue(value.copy(tiltMinimum = it)) }
            SettingSwitch("Velocity", null, value.velocityEnabled) { onValue(value.copy(velocityEnabled = it)) }
            if (value.velocityEnabled) BrushSettingSlider("Velocity minimum", value.velocityMinimum, 0f..1f) { onValue(value.copy(velocityMinimum = it)) }
            if (value.velocityEnabled) PressureCurveEditor("Velocity response", value.velocityCurve) { onValue(value.copy(velocityCurve = it)) }
            SettingSwitch("Random", "Deterministic per stamp, so export matches the canvas.", value.randomEnabled) { onValue(value.copy(randomEnabled = it)) }
            if (value.randomEnabled) BrushSettingSlider("Random minimum", value.randomMinimum, 0f..1f) { onValue(value.copy(randomMinimum = it)) }
        }
    }
}

@Composable
private fun ColorDynamicsEditor(label: String, value: BrushColorDynamics, onValue: (BrushColorDynamics) -> Unit) {
    val active = buildList { if (value.pressureEnabled) add(tr("Pressure", "压力")); if (value.tiltEnabled) add(tr("Tilt", "倾斜")); if (value.velocityEnabled) add(tr("Velocity", "速度")); if (value.randomEnabled) add(tr("Random", "随机")) }
    var expanded by remember(label) { mutableStateOf(active.isNotEmpty()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, OutlineSoft, RoundedCornerShape(10.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(tr("$label input dynamics", "$label 输入动态"), color = Sage, fontSize = 11.sp); Text(active.joinToString().ifBlank { tr("Constant", "固定") }, color = MutedInk, fontSize = 10.sp) }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded) {
            SettingSwitch(tr("Pressure", "压力"), null, value.pressureEnabled) { onValue(value.copy(pressureEnabled = it)) }
            if (value.pressureEnabled) BrushSettingSlider(tr("Pressure minimum", "压力下限"), value.pressureMinimum, 0f..1f) { onValue(value.copy(pressureMinimum = it)) }
            if (value.pressureEnabled) PressureCurveEditor(tr("Pressure response", "压力响应"), value.pressureCurve) { onValue(value.copy(pressureCurve = it)) }
            SettingSwitch(tr("Tilt", "倾斜"), null, value.tiltEnabled) { onValue(value.copy(tiltEnabled = it)) }
            if (value.tiltEnabled) BrushSettingSlider(tr("Tilt minimum", "倾斜下限"), value.tiltMinimum, 0f..1f) { onValue(value.copy(tiltMinimum = it)) }
            SettingSwitch(tr("Velocity", "速度"), null, value.velocityEnabled) { onValue(value.copy(velocityEnabled = it)) }
            if (value.velocityEnabled) BrushSettingSlider(tr("Velocity minimum", "速度下限"), value.velocityMinimum, 0f..1f) { onValue(value.copy(velocityMinimum = it)) }
            if (value.velocityEnabled) PressureCurveEditor(tr("Velocity response", "速度响应"), value.velocityCurve) { onValue(value.copy(velocityCurve = it)) }
            SettingSwitch(tr("Random per tip", "逐笔尖随机"), tr("Uses a stable seed so canvas and export match.", "使用稳定随机种子，确保画布与导出一致。"), value.randomEnabled) { onValue(value.copy(randomEnabled = it)) }
            if (value.randomEnabled) BrushSettingSlider(tr("Signed random minimum", "随机有符号下限"), value.randomMinimum, -1f..1f) { onValue(value.copy(randomMinimum = it)) }
        }
    }
}

@Composable
private fun BrushGrainPreview(path: String, density: Float) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    Column { Text(tr("Paper texture · ${(density * 100).toInt()}%", "纸张纹理 · ${(density * 100).toInt()}%"), fontSize = 11.sp, color = MutedInk); if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), tr("Paper texture", "纸张纹理"), Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) }
}

@Composable
private fun BrushTipPreview(label: String, path: String) {
    val bitmap = remember(path) { android.graphics.BitmapFactory.decodeFile(path) }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    Column {
        Text(label, fontSize = 11.sp, color = MutedInk)
        if (bitmap != null) androidx.compose.foundation.Image(bitmap.asImageBitmap(), label, Modifier.fillMaxWidth().height(90.dp).background(Color.White, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
    }
}

@Composable
private fun PressureCurveEditor(label: String, points: List<BrushCurvePoint>, onPoints: (List<BrushCurvePoint>) -> Unit) {
    val shown = points.ifEmpty { listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f)) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(tr("$label · ${shown.size} points", "${localizedBrushLabel(label)} · ${shown.size} 个控制点"), fontSize = 11.sp, color = MutedInk, modifier = Modifier.weight(1f)); TextButton(onClick = { onPoints(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(1f, 1f))) }) { Text(tr("Linear", "线性")) }; TextButton(onClick = { onPoints(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(.35f, .62f), BrushCurvePoint(1f, 1f))) }) { Text(tr("Soft", "柔和")) }; TextButton(onClick = { onPoints(listOf(BrushCurvePoint(0f, 0f), BrushCurvePoint(.65f, .25f), BrushCurvePoint(1f, 1f))) }) { Text(tr("Firm", "硬朗")) } }
        Canvas(Modifier.fillMaxWidth().height(72.dp).background(Workspace, RoundedCornerShape(8.dp)).padding(8.dp)) {
            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, 0f), 1f)
            shown.zipWithNext().forEach { (start, end) -> drawLine(Sage, Offset(start.x * size.width, (1f - start.y) * size.height), Offset(end.x * size.width, (1f - end.y) * size.height), 3f, cap = StrokeCap.Round) }
            shown.forEach { drawCircle(Sage, 4f, Offset(it.x * size.width, (1f - it.y) * size.height)) }
        }
    }
}

@Composable
private fun SettingsDialog(
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
    var shortcutEditorOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
        title = { Text(tr("Inkloom Settings", "Inkloom 设置")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 320.dp, max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(tr("LANGUAGE", "语言"), color = Sage, fontSize = 12.sp) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = settings.language == UiLanguage.ENGLISH, onClick = { onChange(settings.copy(language = UiLanguage.ENGLISH)) }, label = { Text("English") })
                    FilterChip(selected = settings.language == UiLanguage.SIMPLIFIED_CHINESE, onClick = { onChange(settings.copy(language = UiLanguage.SIMPLIFIED_CHINESE)) }, label = { Text("简体中文") })
                } }
                item { Text(tr("INPUT", "输入"), color = Sage, fontSize = 12.sp) }
                item { SettingSwitch(tr("Draw with finger", "手指书写"), tr("Off by default. Fingers navigate and zoom.", "默认关闭；手指用于移动和缩放。"), settings.fingerDrawing) { onChange(settings.copy(fingerDrawing = it)) } }
                item { SettingSwitch(tr("Single-finger double tap to undo", "单指双击撤销"), tr("Off by default. Only blank-canvas taps trigger it; drawing, dragging and links are excluded.", "默认关闭；仅空白画布短触生效，书写、拖动和链接均不会触发。"), settings.singleFingerDoubleTapUndo) { onChange(settings.copy(singleFingerDoubleTapUndo = it)) } }
                item { SettingSwitch(tr("Two-finger double tap to undo", "双指双击撤销"), tr("Matches common notebook gestures.", "符合常见笔记应用手势。"), settings.twoFingerDoubleTapUndo) { onChange(settings.copy(twoFingerDoubleTapUndo = it)) } }
                item { SettingSwitch(tr("Three-finger tap to redo", "三指轻点重做"), tr("Matches common drawing-app gestures.", "符合常见绘画应用手势。"), settings.threeFingerTapRedo) { onChange(settings.copy(threeFingerTapRedo = it)) } }
                item { SettingSwitch(tr("Two-finger canvas rotation", "双指旋转画布"), tr("Off by default to prevent accidental rotation; R remains available.", "默认关闭以避免误旋转；仍可使用 R 快捷键。"), settings.twoFingerRotation) { onChange(settings.copy(twoFingerRotation = it)) } }
                item { SettingSwitch(tr("Show input diagnostics", "显示输入诊断"), tr("Displays stylus data plus Toolbox keys, scan codes, rotary axes, pointer buttons, source and device identity.", "显示手写笔数据，以及 Toolbox 的按键、扫描码、旋钮轴、侧键、来源和设备标识。"), settings.showInputDiagnostics) { onChange(settings.copy(showInputDiagnostics = it)) } }
                item { Text("${tr("Pressure sensitivity", "压感灵敏度")}  ${"%.2f".format(settings.pressureSensitivity)}×") }
                item { Slider(settings.pressureSensitivity, { onChange(settings.copy(pressureSensitivity = it)) }, valueRange = .25f..2.5f) }
                item { Text("${tr("Pressure onset", "起始压力")}  ${(settings.pressureMinimumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { Slider(settings.pressureMinimumInput, { value -> onChange(settings.copy(pressureMinimumInput = value.coerceAtMost(settings.pressureMaximumInput - .05f))) }, valueRange = 0f..0.95f) }
                item { Text("${tr("Full pressure point", "满压位置")}  ${(settings.pressureMaximumInput * 100).toInt()}%", color = MutedInk, fontSize = 12.sp) }
                item { Slider(settings.pressureMaximumInput, { value -> onChange(settings.copy(pressureMaximumInput = value.coerceAtLeast(settings.pressureMinimumInput + .05f))) }, valueRange = 0.05f..1f) }
                item { PressureCurvePreview(settings.pressureMinimumInput, settings.pressureMaximumInput, settings.pressureSensitivity) }
                item { Text("${tr("Stroke stabilization", "笔触防抖")}  ${(settings.stabilization * 100).toInt()}%") }
                item { Slider(settings.stabilization, { onChange(settings.copy(stabilization = it)) }, valueRange = 0f..0.9f) }
                item { Text(tr("DEFAULT TOOL PRESETS", "默认工具预设"), color = Sage, fontSize = 12.sp) }
                item { BrushSettingSlider(tr("Pen size", "钢笔大小"), settings.penSize, .5f..400f) { onChange(settings.copy(penSize = it)) } }
                item { BrushSettingSlider(tr("Pen opacity", "钢笔不透明度"), settings.penOpacity, .05f..1f) { onChange(settings.copy(penOpacity = it)) } }
                item { BrushSettingSlider(tr("Marker size", "标记笔大小"), settings.highlighterSize, .5f..400f) { onChange(settings.copy(highlighterSize = it)) } }
                item { BrushSettingSlider(tr("Marker opacity", "标记笔不透明度"), settings.highlighterOpacity, .05f..1f) { onChange(settings.copy(highlighterOpacity = it)) } }
                item { BrushSettingSlider(tr("Eraser size", "橡皮大小"), settings.eraserSize, .5f..400f) { onChange(settings.copy(eraserSize = it)) } }
                item { OutlinedButton(onClick = { onChange(settings.copy(penSize = 5f, penOpacity = 1f, highlighterSize = 28f, highlighterOpacity = .35f, eraserSize = 24f, eraserPreset = EraserPreset.NORMAL_FREE)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset tool presets", "重置工具预设")) } }
                item { Text(tr("PEN BUTTONS", "笔按键"), color = Sage, fontSize = 12.sp) }
                item { Text(tr("Hold temporarily switches tools and restores the previous brush, size, opacity and color on release. Tap keeps the selected tool active. The pen-tail eraser always erases only while touching the screen.", "“按住”会临时切换工具，松开后恢复原笔刷、大小、不透明度和颜色；“单击”会保持切换后的工具。笔尾橡皮只在接触屏幕时擦除。"), color = MutedInk, fontSize = 11.sp) }
                item { StylusActionRow(tr("Primary button", "主按键"), settings.stylusPrimaryButton, settings.stylusPrimaryTrigger, { onChange(settings.copy(stylusPrimaryButton = it)) }, { onChange(settings.copy(stylusPrimaryTrigger = it)) }) }
                item { StylusActionRow(tr("Secondary button", "副按键"), settings.stylusSecondaryButton, settings.stylusSecondaryTrigger, { onChange(settings.copy(stylusSecondaryButton = it)) }, { onChange(settings.copy(stylusSecondaryTrigger = it)) }) }
                item { Text(tr("TOOLBOX & KEYBOARD", "TOOLBOX 与键盘"), color = Sage, fontSize = 12.sp) }
                item { Text(tr("CSP-compatible defaults are active. Customize them for Wacom ExpressKeys, a keyboard, or another Toolbox controller.", "已启用兼容 CSP 的默认键位。可为 Wacom ExpressKey、键盘或其他 Toolbox 控制器自定义。"), color = MutedInk, fontSize = 12.sp) }
                item { OutlinedButton(onClick = { shortcutEditorOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Keyboard, null); Spacer(Modifier.width(8.dp)); Text(tr("Customize shortcuts", "自定义快捷键")) } }
                item { Text(tr("INTERFACE", "界面"), color = Sage, fontSize = 12.sp) }
                item { SettingSwitch(tr("Show page sidebar", "显示页面侧栏"), null, settings.showPageRail) { onChange(settings.copy(showPageRail = it)) } }
                item { SettingSwitch(tr("Show floating toolbar", "显示浮动工具栏"), null, settings.showToolbar) { onChange(settings.copy(showToolbar = it)) } }
                item { Text(tr("Toolbar docking", "工具栏停靠"), color = MutedInk, fontSize = 11.sp); LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ToolbarDock.entries.forEach { dock -> item { FilterChip(selected = settings.toolbarDock == dock, onClick = { onChange(settings.copy(toolbarDock = dock, verticalToolbar = if (dock in setOf(ToolbarDock.LEFT, ToolbarDock.RIGHT)) true else if (dock in setOf(ToolbarDock.TOP, ToolbarDock.BOTTOM)) false else settings.verticalToolbar)) }, label = { Text(when (dock) { ToolbarDock.FREE -> tr("Free", "自由"); ToolbarDock.TOP -> tr("Top", "顶部"); ToolbarDock.BOTTOM -> tr("Bottom", "底部"); ToolbarDock.LEFT -> tr("Left", "左侧"); ToolbarDock.RIGHT -> tr("Right", "右侧") }) }) } } } }
                if (settings.toolbarDock == ToolbarDock.FREE) item { SettingSwitch(tr("Vertical toolbar", "垂直工具栏"), tr("Useful on a wide tablet canvas.", "适合宽屏平板画布。"), settings.verticalToolbar) { onChange(settings.copy(verticalToolbar = it)) } }
                item { OutlinedButton(onClick = { onChange(settings.copy(toolbarDock = ToolbarDock.FREE, toolbarOffsetXDp = 16f, toolbarOffsetYDp = 16f)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CenterFocusStrong, null); Spacer(Modifier.width(8.dp)); Text(tr("Reset toolbar position", "重置工具栏位置")) } }
                item { Text(tr("DATA & BACKUP", "数据与备份"), color = Sage, fontSize = 12.sp) }
                item { Text(tr("A full backup includes notes, folders, imported brushes and sources, PDFs, images, audio, input settings, panel layouts, Toolbox shortcuts, quick slots and the custom color palette. Content is restored with new IDs so existing work is never overwritten.", "完整备份包含笔记、文件夹、导入笔刷及源文件、PDF、图片、音频、输入设置、面板布局、Toolbox 快捷键、快速槽与自定义色板。内容恢复时会生成新 ID，不覆盖现有作品。"), color = MutedInk, fontSize = 12.sp) }
                item { Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Backup, null); Spacer(Modifier.width(8.dp)); Text(tr("Export full library backup", "导出完整资料库备份")) } }
                item { OutlinedButton(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Restore, null); Spacer(Modifier.width(8.dp)); Text(tr("Restore library backup", "恢复资料库备份")) } }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Toolbox shortcuts", "Toolbox 快捷键")) },
        text = {
            LazyColumn(
                Modifier.widthIn(min = 400.dp, max = 580.dp).heightIn(max = 540.dp)
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
                item { Text(tr("Tap Change, then press a Toolbox or keyboard combination. Rotary, touch-ring, wheel and pointer-button mappings are configured below.", "点击“更改”，然后按下 Toolbox 或键盘组合键。旋钮、触控环、滚轮与侧键映射可在下方配置。"), color = MutedInk, fontSize = 12.sp) }
                item {
                    OutlinedTextField(
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
                        item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text(tr("All", "全部")) }) }
                        items(ShortcutCategory.entries, key = { it.name }) { category ->
                            FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = if (selectedCategory == category) null else category }, label = { Text(shortcutCategoryLabel(category, language)) })
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("Capture mode", "捕获方式"), color = MutedInk, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = captureMode == ShortcutMatchMode.KEY_CODE, onClick = { captureMode = ShortcutMatchMode.KEY_CODE }, label = { Text(tr("Logical key", "逻辑键")) })
                            FilterChip(selected = captureMode == ShortcutMatchMode.SCAN_CODE, onClick = { captureMode = ShortcutMatchMode.SCAN_CODE }, label = { Text(tr("Physical scan code", "物理扫描码")) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = captureTrigger == ShortcutTrigger.PRESS, onClick = { captureTrigger = ShortcutTrigger.PRESS }, label = { Text(tr("Tap to switch", "单击切换")) })
                            FilterChip(selected = captureTrigger == ShortcutTrigger.HOLD, onClick = { captureTrigger = ShortcutTrigger.HOLD }, label = { Text(tr("Hold, release to return", "按住，松开返回")) })
                        }
                        SettingSwitch(tr("Only this input device", "仅此输入设备"), tr("Keeps the global CSP key while adding a Toolbox-specific mapping.", "保留全局 CSP 键位，同时添加此 Toolbox 的专用映射。"), deviceSpecific) { deviceSpecific = it }
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
                        Text(tr("Choose the real groups used by CSP-style P and B shortcuts. Automatic keeps name-based detection.", "选择 CSP 风格 P、B 快捷键实际使用的笔刷组；“自动”会继续按名称识别。"), color = MutedInk, fontSize = 11.sp)
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
                            supportingContent = { Text(if (listeningFor == action) tr("Press a key combination…", "请按组合键…") else chords.joinToString(" / ").ifBlank { tr("Not assigned", "未分配") }, color = if (listeningFor == action) Sage else MutedInk) },
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
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImportShortcuts, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.FileOpen, null); Spacer(Modifier.width(6.dp)); Text(tr("Import profile", "导入配置")) }
                        OutlinedButton(onClick = onExportShortcuts, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(6.dp)); Text(tr("Export profile", "导出配置")) }
                    }
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
            SettingSwitch(tr("Reverse direction", "反转方向"), null, binding.reversed) {
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
                FilterChip(selected = binding.trigger == ShortcutTrigger.PRESS, onClick = { onChange(settings.copy(shortcutButtonBindings = ToolboxMotionResolver.replaceButton(settings.shortcutButtonBindings, binding.copy(trigger = ShortcutTrigger.PRESS)))) }, label = { Text(tr("Tap", "单击")) })
                FilterChip(selected = binding.trigger == ShortcutTrigger.HOLD, onClick = { onChange(settings.copy(shortcutButtonBindings = ToolboxMotionResolver.replaceButton(settings.shortcutButtonBindings, binding.copy(trigger = ShortcutTrigger.HOLD)))) }, label = { Text(tr("Hold / release", "按住／松开")) })
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
                DropdownMenuItem(
                    text = { Text(tr("Automatic", "自动")) },
                    onClick = { onSelect(""); expanded = false },
                )
                groupPaths.forEach { path ->
                    DropdownMenuItem(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Toolbox quick slots", "Toolbox 快速槽")) },
        text = {
            LazyColumn(Modifier.widthIn(min = 380.dp, max = 560.dp).heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        tr("Bind a Toolbox key to Quick slot 1–8 in the shortcut list, then choose what the slot restores here.", "先在快捷键列表中为快速槽 1–8 绑定 Toolbox 按键，再在此选择快速槽恢复的内容。"),
                        color = MutedInk,
                        fontSize = 12.sp,
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToolboxQuickSlots.COUNT) { index ->
                            FilterChip(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index },
                                label = { Text(tr("Slot ${index + 1}", "槽 ${index + 1}")) },
                            )
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = slot.label,
                        onValueChange = { updateSlot(slot.copy(label = it)) },
                        label = { Text(tr("Slot label", "快速槽名称")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(tr("RESTORE", "恢复内容"), color = MutedInk, fontSize = 11.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(ToolboxQuickSlotKind.entries, key = { it.name }) { kind ->
                            FilterChip(
                                selected = slot.kind == kind,
                                onClick = { updateSlot(slot.copy(kind = kind)) },
                                label = { Text(toolboxQuickSlotKindLabel(kind)) },
                            )
                        }
                    }
                }
                when (slot.kind) {
                    ToolboxQuickSlotKind.EMPTY -> item { Text(tr("This slot is empty. Its shortcut is ignored safely.", "此快速槽为空；对应快捷键会被安全忽略。"), color = MutedInk, fontSize = 12.sp) }
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
                            OutlinedTextField(
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
                    ToolboxQuickSlotKind.WORKSPACE -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (slot.workspace == null) tr("No workspace captured yet.", "尚未保存工作区。") else tr("A complete panel and toolbar layout is stored in this slot.", "此快速槽已保存完整面板与工具栏布局。"),
                                color = MutedInk,
                                fontSize = 12.sp,
                            )
                            Button(onClick = { updateSlot(slot.copy(workspace = settings.captureToolboxWorkspace())) }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.PhotoCamera, null)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("Capture current workspace", "保存当前工作区"))
                            }
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
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
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
    ToolboxQuickSlotKind.WORKSPACE -> tr("Workspace", "工作区")
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
    ShortcutAction.AUDIO_TOOL -> language.text("Record audio note", "录音笔记")
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
    ShortcutAction.TOGGLE_RULER -> language.text("Show / hide ruler", "显示／隐藏直尺")
    ShortcutAction.TOGGLE_GRID_SNAP -> language.text("Show / hide snap grid", "显示／隐藏吸附网格")
    ShortcutAction.TOGGLE_ALIGNMENT_GUIDES -> language.text("Show / hide alignment guides", "显示／隐藏对齐参考线")
    ShortcutAction.PREVIOUS_PAGE -> language.text("Previous page", "上一页")
    ShortcutAction.NEXT_PAGE -> language.text("Next page", "下一页")
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
private fun PressureCurvePreview(minimumInput: Float, maximumInput: Float, sensitivity: Float) {
    Column {
        Text(tr("Calibrated response", "校准响应曲线"), color = MutedInk, fontSize = 11.sp)
        Canvas(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(8.dp)).background(Workspace).padding(10.dp)) {
            drawLine(OutlineSoft, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            drawLine(OutlineSoft, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
            val path = androidx.compose.ui.graphics.Path()
            for (index in 0..100) {
                val input = index / 100f
                val output = PressureCalibration.apply(input, minimumInput, maximumInput, sensitivity)
                val x = input * size.width
                val y = size.height - output * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Sage, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(localizedBrushLabel(title)); if (subtitle != null) Text(localizedBrushLabel(subtitle), color = MutedInk, fontSize = 11.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun StylusActionRow(title: String, selected: StylusButtonAction, trigger: ShortcutTrigger, onSelected: (StylusButtonAction) -> Unit, onTrigger: (ShortcutTrigger) -> Unit) {
    Column {
        Text(title, fontSize = 13.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(StylusButtonAction.entries, key = { it.name }) { action ->
                FilterChip(selected = selected == action, onClick = { onSelected(action) }, label = { Text(when (action) {
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
                FilterChip(selected = trigger == ShortcutTrigger.HOLD, onClick = { onTrigger(ShortcutTrigger.HOLD) }, label = { Text(tr("Hold temporarily", "按住临时切换")) })
                FilterChip(selected = trigger == ShortcutTrigger.PRESS, onClick = { onTrigger(ShortcutTrigger.PRESS) }, label = { Text(tr("Tap to switch", "单击切换")) })
            }
        } else if (selected in setOf(StylusButtonAction.UNDO, StylusButtonAction.REDO)) {
            Text(tr("Runs once when pressed", "按下时执行一次"), color = MutedInk, fontSize = 10.sp)
        }
    }
}
