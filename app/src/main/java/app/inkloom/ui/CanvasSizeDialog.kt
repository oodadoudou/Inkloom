package app.inkloom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val request = runCatching { CanvasSizeRequest(width.toFloat(), height.toFloat(), dpi.toFloat(), unit, mode, anchor).also { it.pixels() } }.getOrNull()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(tr("Canvas & resolution", "画布与分辨率")) },
        text = { Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(width, { width = it.take(12) }, Modifier.weight(1f), singleLine = true, label = { Text(tr("Width", "宽度")) })
                OutlinedTextField(height, { height = it.take(12) }, Modifier.weight(1f), singleLine = true, label = { Text(tr("Height", "高度")) })
            }
            Row {
                Box(Modifier.weight(1f)) {
                    TextButton({ unitMenu = true }) { Text(when(unit) {
                        CanvasUnit.PIXELS -> "px"; CanvasUnit.MILLIMETERS -> "mm"; CanvasUnit.CENTIMETERS -> "cm"; CanvasUnit.INCHES -> "in"
                    }) }
                    DropdownMenu(unitMenu, { unitMenu = false }) { CanvasUnit.entries.forEach { next ->
                        DropdownMenuItem(text = { Text(when(next) { CanvasUnit.PIXELS -> tr("Pixels", "像素"); CanvasUnit.MILLIMETERS -> tr("Millimeters", "毫米"); CanvasUnit.CENTIMETERS -> tr("Centimeters", "厘米"); CanvasUnit.INCHES -> tr("Inches", "英寸") }) }, onClick = {
                            val density = dpi.toFloatOrNull() ?: page.dpi
                            width = width.toFloatOrNull()?.let { CanvasSizing.fromPixels(CanvasSizing.toPixels(it, unit, density), next, density).toString() } ?: width
                            height = height.toFloatOrNull()?.let { CanvasSizing.fromPixels(CanvasSizing.toPixels(it, unit, density), next, density).toString() } ?: height
                            unit = next; unitMenu = false
                        })
                    } }
                }
                OutlinedTextField(dpi, { dpi = it.take(8) }, Modifier.weight(1f), singleLine = true, label = { Text("DPI") })
            }
            FilterChip(mode == CanvasResizeMode.CANVAS, { mode = CanvasResizeMode.CANVAS }, label = { Text(tr("Crop / extend canvas", "裁剪／扩展画布")) })
            FilterChip(mode == CanvasResizeMode.RESAMPLE, { mode = CanvasResizeMode.RESAMPLE }, label = { Text(tr("Resample image", "重采样图像")) })
            if (mode == CanvasResizeMode.CANVAS) {
                Text(tr("Anchor", "锚点"))
                val labels = listOf("↖", "↑", "↗", "←", "•", "→", "↙", "↓", "↘")
                repeat(3) { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(3) { col ->
                    val next = CanvasAnchor.entries[row * 3 + col]
                    FilterChip(anchor == next, { anchor = next }, label = { Text(labels[row * 3 + col]) })
                } } }
            }
            request?.pixels()?.let { Text("${it.first} × ${it.second} px", style = MaterialTheme.typography.bodySmall) }
                ?: Text(tr("32–8192 px per side; DPI 1–2400", "每边 32–8192 px；DPI 1–2400"), style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = request != null && !busy, onClick = { request?.let(onApply) }) { Text(tr("Apply", "应用")) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(tr("Cancel", "取消")) } })
}

@Composable
internal fun PageRasterExportDialog(page: InkPage, language: UiLanguage, onDismiss: () -> Unit,
    onExport: (PageRasterExportOptions) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var width by remember { mutableStateOf(PageCropTransform.visibleWidth(page).toInt().toString()) }
    var height by remember { mutableStateOf(PageCropTransform.visibleHeight(page).toInt().toString()) }
    var background by remember { mutableStateOf(true) }
    val w = width.toIntOrNull(); val h = height.toIntOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(tr("Export page", "导出页面")) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(width, { width = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text(tr("Width (px)", "宽度（px）")) })
                OutlinedTextField(height, { height = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text(tr("Height (px)", "高度（px）")) })
            }
            Row { Checkbox(background, { background = it }); Text(tr("Include paper", "包含纸张背景")) }
        }
    }, confirmButton = { TextButton(enabled = w != null && h != null && w in 1..8192 && h in 1..8192,
        onClick = { if (w != null && h != null) onExport(PageRasterExportOptions(w, h, background)) }) { Text(tr("Export", "导出")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } })
}
