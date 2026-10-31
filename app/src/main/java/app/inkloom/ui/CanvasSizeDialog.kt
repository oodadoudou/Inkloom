package app.inkloom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.inkloom.document.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

@Composable
internal fun CanvasSizeDialog(page: InkPage, language: UiLanguage, busy: Boolean,
    onDismiss: () -> Unit, onApply: (CanvasSizeRequest) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var width by remember { mutableStateOf(PageCropTransform.visibleWidth(page).toInt().toString()) }
    var height by remember { mutableStateOf(PageCropTransform.visibleHeight(page).toInt().toString()) }
    var dpi by remember { mutableStateOf(page.dpi.toInt().toString()) }
    var unit by remember { mutableStateOf(CanvasUnit.PIXELS) }
    var unitMenu by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(CanvasResizeMode.CANVAS) }
    var anchor by remember { mutableStateOf(CanvasAnchor.CENTER) }
    var superResolution by remember { mutableStateOf(false) }
    val paperBackground = page.background as? PageBackground.Paper
    var paper by remember { mutableStateOf(paperBackground?.colorArgb) }
    var paperPicker by remember { mutableStateOf(false) }
    if (paperPicker) StandardColorPickerDialog((paper ?: PaperColorCatalog.WHITE).toInt(), language,
        onConfirm = { paper = (it.toLong() and 0xFFFFFFFFL) or 0xFF000000L; paperPicker = false }, onDismiss = { paperPicker = false })
    val request = runCatching { CanvasSizeRequest(width.toFloat(), height.toFloat(), dpi.toFloat(), unit, mode, anchor,
        paperColorArgb = paper?.takeIf { it != paperBackground?.colorArgb }, superResolution = superResolution).also { it.pixels() } }.getOrNull()
    StudioDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(tr("Canvas & resolution", "画布与分辨率")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            val unitLabel = when(unit) { CanvasUnit.PIXELS -> "px"; CanvasUnit.MILLIMETERS -> "mm"; CanvasUnit.CENTIMETERS -> "cm"; CanvasUnit.INCHES -> "in" }
            fun unitName(value: CanvasUnit) = when (value) { CanvasUnit.PIXELS -> tr("Pixels", "像素"); CanvasUnit.MILLIMETERS -> tr("Millimeters", "毫米")
                CanvasUnit.CENTIMETERS -> tr("Centimeters", "厘米"); CanvasUnit.INCHES -> tr("Inches", "英寸") }
            StudioOutlinedTextField(width, { width = it.take(12) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("Width", "宽度")) },
                suffix = { Text(unitLabel) }, inlineLabelWidth = 96.dp)
            StudioOutlinedTextField(height, { height = it.take(12) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("Height", "高度")) },
                suffix = { Text(unitLabel) }, inlineLabelWidth = 96.dp)
            StudioDropdownField(unitName(unit), unitMenu, { unitMenu = it }, label = tr("Unit", "单位")) {
                CanvasUnit.entries.forEach { next ->
                    StudioMenuItem(text = { Text(unitName(next)) }, onClick = {
                        val density = dpi.toFloatOrNull() ?: page.dpi
                        width = width.toFloatOrNull()?.let { CanvasSizing.fromPixels(CanvasSizing.toPixels(it, unit, density), next, density).toString() } ?: width
                        height = height.toFloatOrNull()?.let { CanvasSizing.fromPixels(CanvasSizing.toPixels(it, unit, density), next, density).toString() } ?: height
                        unit = next; unitMenu = false
                    })
                }
            }
            StudioOutlinedTextField(dpi, { dpi = it.take(8) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("DPI") },
                suffix = { Text("dpi") }, inlineLabelWidth = 96.dp)
            StudioSegmented(listOf(tr("Crop / extend canvas", "裁剪／扩展画布"), tr("Resample image", "重采样图像")),
                if (mode == CanvasResizeMode.CANVAS) 0 else 1, { mode = if (it == 0) CanvasResizeMode.CANVAS else CanvasResizeMode.RESAMPLE },
                label = tr("Change", "方式"))
            if (mode == CanvasResizeMode.RESAMPLE) {
                StudioCheckRow(tr("Super resolution (enlarging)", "超分辨率（放大）"), superResolution, { superResolution = it })
            }
            if (mode == CanvasResizeMode.CANVAS) Row(Modifier.padding(vertical = 4.dp)) {
                Text(tr("Anchor", "锚点"), Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val labels = listOf("↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘")
                    repeat(3) { row -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { repeat(3) { col ->
                        val next = CanvasAnchor.entries[row * 3 + col]
                        Box(Modifier.size(30.dp)
                            .background(if (anchor == next) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .selectable(anchor == next, role = Role.RadioButton) { anchor = next }, contentAlignment = Alignment.Center) {
                            Text(labels[row * 3 + col], style = MaterialTheme.typography.labelMedium)
                        }
                    } } }
                }
            }
            if (paperBackground != null) Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                // CSP's basic canvas settings include the paper colour; it changes only the paper, never the layers.
                Text(tr("Paper color", "纸张颜色"), Modifier.width(96.dp), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PaperColorCatalog.options.forEach { option ->
                        val chosen = paper == option.colorArgb
                        val swatchName = tr("Paper color ${paperColorName(option)}", "纸张颜色 ${paperColorName(option)}")
                        Box(Modifier.size(24.dp).background(androidx.compose.ui.graphics.Color(option.colorArgb))
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            .selectable(chosen, role = Role.RadioButton) { paper = option.colorArgb }
                            .semantics { contentDescription = swatchName })
                    }
                    StudioButton(tr("Custom…", "自定义…"), { paperPicker = true }) {
                        Box(Modifier.size(14.dp).background(androidx.compose.ui.graphics.Color(paper ?: PaperColorCatalog.WHITE)))
                    }
                }
            }
            request?.pixels()?.let { StudioNote(tr("Result: ${it.first} × ${it.second} px", "结果：${it.first} × ${it.second} px"), Modifier.padding(top = 4.dp)) }
                ?: StudioNote(tr("32–8192 px per side; DPI 1–2400", "每边 32–8192 px；DPI 1–2400"), Modifier.padding(top = 4.dp))
            if (busy && superResolution) {
                val progress by app.inkloom.ml.SuperResolution.progress.collectAsState()
                LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
            } else if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = request != null && !busy, onClick = { request?.let(onApply) }) { Text(tr("Apply", "应用")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Cancel", "取消")) } })
}

@Composable
internal fun PageRasterExportDialog(page: InkPage, language: UiLanguage, onDismiss: () -> Unit,
    onExport: (PageRasterExportOptions) -> Unit, sharing: Boolean = false) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var width by remember { mutableStateOf(PageCropTransform.visibleWidth(page).toInt().toString()) }
    var height by remember { mutableStateOf(PageCropTransform.visibleHeight(page).toInt().toString()) }
    var background by remember { mutableStateOf(true) }
    var drafts by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(RasterFileFormat.PNG) }
    var formatMenu by remember { mutableStateOf(false) }
    val w = width.toIntOrNull(); val h = height.toIntOrNull()
    StudioDialog(onDismissRequest = onDismiss, title = { Text(if (sharing) tr("Share page", "分享页面") else tr("Export page", "导出页面")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudioOutlinedTextField(width, { width = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text(tr("Width (px)", "宽度（px）")) })
                StudioOutlinedTextField(height, { height = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text(tr("Height (px)", "高度（px）")) })
            }
            Box {
                TextButton({ formatMenu = true }) { Text(format.label) }
                DropdownMenu(formatMenu, { formatMenu = false }) { RasterFileFormat.entries.forEach { next ->
                    StudioMenuItem(text = { Text(next.label) }, onClick = { format = next; formatMenu = false })
                } }
            }
            Row { StudioCheckbox(background, { background = it }, enabled = format != RasterFileFormat.JPEG); Text(tr("Include paper", "包含纸张背景")) }
            // Layered PNG keeps draft layers with their flags; other formats ask.
            if (page.layers.any { it.draft } && format != RasterFileFormat.LAYERED_PNG) {
                Row(Modifier.toggleable(drafts, role = Role.Checkbox) { drafts = it }, verticalAlignment = Alignment.CenterVertically) {
                    StudioCheckbox(drafts, onCheckedChange = null); Text(tr("Include draft layers", "包含草稿图层"))
                }
            }
        }
    }, confirmButton = { TextButton(enabled = w != null && h != null && w in 1..8192 && h in 1..8192,
        onClick = { if (w != null && h != null) onExport(PageRasterExportOptions(w, h, background, format, includeDraftLayers = drafts && format != RasterFileFormat.LAYERED_PNG)) }) { Text(if (sharing) tr("Share", "分享") else tr("Export", "导出")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } })
}
