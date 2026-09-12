package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.inkloom.document.*
import app.inkloom.settings.text
import app.inkloom.ui.theme.Ink
import app.inkloom.ui.theme.OutlineSoft

@Composable
internal fun CustomPageTemplateDialog(
    base: PageTemplate,
    language: app.inkloom.settings.UiLanguage,
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int, PageBackground.Paper) -> PageTemplate?,
    onCreated: (PageTemplate) -> Unit,
) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf(base.width.toInt().toString()) }
    var height by remember { mutableStateOf(base.height.toInt().toString()) }
    var paper by remember { mutableStateOf(base.background as? PageBackground.Paper ?: PageBackground.Paper()) }
    var hex by remember { mutableStateOf("%06X".format(paper.colorArgb and 0xffffff)) }
    var error by remember { mutableStateOf(false) }
    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val color = hex.takeIf { it.length == 6 }?.toLongOrNull(16)?.or(0xff000000L)
    val valid = w != null && h != null && w in 32..20_000 && h in 32..20_000 && color != null
    val defaultName = tr("Custom paper", "自定义纸张")
    val displayed = paper.copy(colorArgb = color ?: paper.colorArgb)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(defaultName) },
        text = {
            Column(Modifier.widthIn(max = 440.dp).heightIn(max = 510.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(80); error = false }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tr("Template name", "模板名称")) }, placeholder = { Text(defaultName) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(width, { width = it.filter(Char::isDigit).take(5); error = false }, Modifier.weight(1f),
                        label = { Text(tr("Width (px)", "宽度（px）")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = w == null || w !in 32..20_000)
                    IconButton(onClick = { val previous = width; width = height; height = previous }) {
                        Icon(Icons.Outlined.SwapHoriz, tr("Swap width and height", "交换宽高"))
                    }
                    OutlinedTextField(height, { height = it.filter(Char::isDigit).take(5); error = false }, Modifier.weight(1f),
                        label = { Text(tr("Height (px)", "高度（px）")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = h == null || h !in 32..20_000)
                }
                Text(tr("32–20,000 px per side", "每边 32–20,000 px"), style = MaterialTheme.typography.bodySmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaperPattern.entries.forEach { pattern -> item {
                        FilterChip(selected = paper.pattern == pattern, onClick = {
                            paper = paper.copy(pattern = pattern,
                                density = if (paper.pattern == PaperPattern.PLAIN) PaperDensity.MEDIUM else paper.density)
                        }, label = { Text(when (pattern) {
                            PaperPattern.PLAIN -> tr("Blank", "空白")
                            PaperPattern.RULED -> tr("Ruled", "横线")
                            PaperPattern.GRID -> tr("Grid", "方格")
                            PaperPattern.DOT -> tr("Dots", "点阵")
                            PaperPattern.CORNELL -> tr("Cornell", "康奈尔")
                        }) })
                    } }
                }
                if (paper.pattern != PaperPattern.PLAIN) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaperDensity.entries.forEach { density -> item {
                        FilterChip(paper.density == density, { paper = paper.copy(density = density) }, label = {
                            Text(when (density) {
                                PaperDensity.DENSE -> tr("Dense", "密集")
                                PaperDensity.MEDIUM -> tr("Medium", "适中")
                                PaperDensity.SPACIOUS -> tr("Spacious", "宽松")
                            })
                        })
                    } }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperColorCatalog.options.forEach { option ->
                        Box(Modifier.size(32.dp).background(Color(option.colorArgb))
                            .border(if (color == option.colorArgb) 2.dp else .5.dp, if (color == option.colorArgb) Ink else OutlineSoft)
                            .semantics { contentDescription = "${tr("Paper color", "纸张颜色")} #${"%06X".format(option.colorArgb and 0xffffff)}" }
                            .clickable { hex = "%06X".format(option.colorArgb and 0xffffff) })
                    }
                }
                OutlinedTextField(hex, { hex = it.removePrefix("#").filter { c -> c in "0123456789abcdefABCDEF" }.take(6).uppercase() }, Modifier.fillMaxWidth(),
                    label = { Text(tr("Paper color (RGB hex)", "纸色（RGB 十六进制）")) }, prefix = { Text("#") }, singleLine = true, isError = color == null)
                Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = tr("Custom paper preview", "自定义纸张预览") }) {
                    val pw = (w ?: base.width.toInt()).coerceAtLeast(1).toFloat()
                    val ph = (h ?: base.height.toInt()).coerceAtLeast(1).toFloat()
                    val scale = minOf(size.width / pw, size.height / ph)
                    val sw = pw * scale; val sh = ph * scale
                    translate((size.width - sw) / 2f, (size.height - sh) / 2f) {
                        drawRect(Color(displayed.colorArgb), size = androidx.compose.ui.geometry.Size(sw, sh))
                        drawIntoCanvas { PaperPatternRenderer.draw(it.nativeCanvas, displayed.pattern, displayed.density, sw, sh) }
                    }
                }
                if (error) Text(tr("Could not save this template", "无法保存此模板"))
            }
        },
        confirmButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(enabled = valid, onClick = {
                focus.clearFocus(force = true); keyboard?.hide()
                val template = runCatching { onCreate(name.ifBlank { "$defaultName $width × $height" }, w!!, h!!, displayed) }.getOrNull()
                if (template != null) onCreated(template) else error = true
            }) { Text(tr("Save template", "保存模板")) }
        },
        dismissButton = {
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            TextButton(onClick = { focus.clearFocus(force = true); keyboard?.hide(); onDismiss() }) { Text(tr("Cancel", "取消")) }
        },
    )
}
