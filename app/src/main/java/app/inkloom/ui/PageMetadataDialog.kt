package app.inkloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import app.inkloom.document.InkPage
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

@Composable
internal fun PageMetadataDialog(page: InkPage, language: UiLanguage,
    onSave: (String?, List<String>) -> Unit, onDismiss: () -> Unit) {
    var number by remember(page.id, page.customNumber) { mutableStateOf(page.customNumber.orEmpty()) }
    var tags by remember(page.id, page.tags) { mutableStateOf(page.tags.joinToString(", ")) }
    fun text(en: String, zh: String) = language.text(en, zh)
    StudioDialog(onDismissRequest = onDismiss,
        title = { Text(text("Page metadata", "页面信息")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StudioOutlinedTextField(number, { number = it }, label = { Text(text("Custom page number", "自定义页码")) }, singleLine = true)
            StudioOutlinedTextField(tags, { tags = it }, label = { Text(text("Tags", "标签")) },
                supportingText = { Text(text("Separate tags with commas", "用逗号分隔标签")) })
        } },
        confirmButton = { TextButton(onClick = { onSave(number, tags.split(',', '，')); onDismiss() }) { Text(text("Save", "保存")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text("Cancel", "取消")) } })
}
