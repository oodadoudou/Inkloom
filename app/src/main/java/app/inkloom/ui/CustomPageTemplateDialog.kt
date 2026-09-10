package app.inkloom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.inkloom.document.*
import app.inkloom.settings.text

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
    var error by remember { mutableStateOf(false) }
    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val valid = w != null && h != null && w in 32..20_000 && h in 32..20_000
    val defaultName = tr("Custom page", "自定义页面")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Custom page size", "自定义页面尺寸")) },
        text = {
            Column(Modifier.widthIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(80); error = false }, singleLine = true,
                    label = { Text(tr("Template name", "模板名称")) }, placeholder = { Text(defaultName) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PaperPattern.PLAIN, PaperPattern.RULED, PaperPattern.GRID, PaperPattern.DOT).forEach { pattern ->
                        FilterChip(selected = paper.pattern == pattern, onClick = { paper = paper.copy(pattern = pattern) }, label = {
                            Text(when (pattern) {
                                PaperPattern.PLAIN -> tr("Blank", "空白")
                                PaperPattern.RULED -> tr("Ruled", "横线")
                                PaperPattern.GRID -> tr("Grid", "方格")
                                else -> tr("Dots", "点阵")
                            })
                        })
                    }
                }
                if (error) Text(tr("Could not save. Remove an unused custom template and try again.", "无法保存，请删除一个不用的自定义模板后重试。"))
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = {
            val template = onCreate(name.ifBlank { "$defaultName $width × $height" }, w!!, h!!, paper)
            if (template != null) onCreated(template) else error = true
        }) { Text(tr("Save template", "保存模板")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "取消")) } },
    )
}
