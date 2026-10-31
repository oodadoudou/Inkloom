package app.inkloom.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import app.inkloom.document.FontEmbedding
import app.inkloom.document.FontLibrary
import app.inkloom.document.ImportedFont
import app.inkloom.document.TextFonts
import app.inkloom.document.TextTypefaces
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * T16's font choice for a text object: Android's families and fonts the user imports. A font's
 * licence is shown as it says, and text whose font is missing on this device uses the default.
 */
@Composable
internal fun TextFontField(reference: String?, language: UiLanguage, onReference: (String?) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember(context) { FontLibrary(context.filesDir) }
    var fonts by remember { mutableStateOf(library.list()) }
    var open by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)!!.use { input ->
                        input.readBytes().also { require(it.size <= FontLibrary.MAX_BYTES) { "This font is too large" } }
                    }
                    library.add(uri.lastPathSegment.orEmpty().substringAfterLast('/').substringBeforeLast('.'), bytes).also { font ->
                        // Android must be able to draw with it, not only read its tables.
                        android.graphics.Typeface.createFromFile(library.file(font))
                    }
                }
            }
            TextTypefaces.forget()
            fonts = library.list()
            result.onSuccess { font ->
                onReference(TextFonts.IMPORTED + font.id)
                message = if (font.embedding == FontEmbedding.RESTRICTED) tr("This font's licence restricts embedding it in documents. Check it before sharing PDFs; images are unaffected.",
                    "此字体的授权限制嵌入文档。分享 PDF 前请确认授权；导出图片不受影响。") else null
            }.onFailure { message = tr("This font could not be added: ${it.message}", "无法添加此字体：${it.message}") }
        }
    }
    val imported = library.find(reference)
    val missing = reference != null && reference.startsWith(TextFonts.IMPORTED) && imported == null
    val current = when {
        reference == null -> systemFontName(null, language)
        reference.startsWith(TextFonts.SYSTEM) -> systemFontName(reference.removePrefix(TextFonts.SYSTEM), language)
        else -> imported?.name ?: tr("Missing font", "缺失的字体")
    }
    Column {
        StudioDropdownField(current, open, { open = it }, Modifier.fillMaxWidth(), label = tr("Font", "字体")) {
            (listOf<String?>(null) + TextFonts.systemFamilies.drop(1)).forEach { family ->
                StudioMenuItem(text = { Text(systemFontName(family, language), fontFamily = family?.let { FontFamily(android.graphics.Typeface.create(it, android.graphics.Typeface.NORMAL)) }) },
                    onClick = { onReference(family?.let { TextFonts.SYSTEM + it }); open = false })
            }
            fonts.forEach { font ->
                StudioMenuItem(text = { Text("${font.name} · ${licenceName(font, language)}",
                    fontFamily = TextTypefaces.base(context.filesDir, TextFonts.IMPORTED + font.id)?.let { FontFamily(it) }) },
                    onClick = { onReference(TextFonts.IMPORTED + font.id); open = false },
                    trailingIcon = { IconButton({
                        fonts = library.remove(font.id); TextTypefaces.forget()
                        if (reference == TextFonts.IMPORTED + font.id) onReference(null)
                    }) { Icon(Icons.Outlined.Close, tr("Remove ${font.name}", "移除 ${font.name}")) } })
            }
            StudioMenuItem(text = { Text(tr("Import font…", "导入字体…")) }, onClick = {
                open = false
                importer.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream"))
            })
        }
        if (missing) StudioNote(tr("Font not on this device; showing the default.", "此设备没有该字体，以默认字体显示。"))
        message?.let { StudioNote(it) }
    }
}

private fun systemFontName(family: String?, language: UiLanguage) = when (family) {
    null, "sans-serif" -> language.text("Default (sans-serif)", "默认（无衬线）")
    "sans-serif-condensed" -> language.text("Sans condensed", "窄无衬线")
    "serif" -> language.text("Serif", "衬线")
    "monospace" -> language.text("Monospace", "等宽")
    "cursive" -> language.text("Handwriting", "手写")
    else -> family
}

private fun licenceName(font: ImportedFont, language: UiLanguage) = when (font.embedding) {
    FontEmbedding.INSTALLABLE -> language.text("installable", "可安装")
    FontEmbedding.EDITABLE -> language.text("editable embedding", "可编辑嵌入")
    FontEmbedding.PREVIEW_AND_PRINT -> language.text("preview & print", "仅预览与打印")
    FontEmbedding.RESTRICTED -> language.text("restricted licence", "授权受限")
    FontEmbedding.UNKNOWN -> language.text("licence unknown", "授权未知")
}
