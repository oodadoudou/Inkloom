package app.inkloom.ui

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.FileObjects
import app.inkloom.document.PageElement
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** CSP file object actions for a linked image: where it comes from, Update, Replace and Open. */
@Composable
internal fun FileObjectControls(image: PageElement.Image, onChange: (PageElement.Image) -> Unit) {
    val language = LocalUiLanguage.current
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember(context) { FileObjects(context, DocumentAssetService(context)) }
    val source = image.source ?: return
    var status by remember(image.id) { mutableStateOf<String?>(null) }
    val missingMessage = tr("The source file \"${source.name}\" is missing or can no longer be read. The notebook keeps its last copy.",
        "源文件“${source.name}”已丢失或无法读取，笔记中仍保留最后一次的副本。")
    val upToDate = tr("Already up to date", "已是最新")
    val updated = tr("Updated from the file", "已从文件更新")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                withContext(Dispatchers.IO) { files.replace(image, uri) }
            }.onSuccess { onChange(it); status = updated }.onFailure { status = it.message }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StudioSection(tr("File object", "文件对象"))
        Text(source.name, style = MaterialTheme.typography.bodyMedium)
        if (source.modified > 0) StudioNote(tr("Read ", "读取于 ") + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(source.modified)))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudioButton(tr("Update", "更新"), {
                scope.launch {
                    when (val result = withContext(Dispatchers.IO) { files.refresh(image, force = true) }) {
                        is FileObjects.Refresh.Updated -> { onChange(result.image); status = updated }
                        FileObjects.Refresh.Unchanged -> status = upToDate
                        is FileObjects.Refresh.Missing -> status = missingMessage
                    }
                }
            })
            StudioButton(tr("Replace…", "替换…"), { picker.launch(arrayOf("image/*")) })
            StudioButton(tr("Open", "打开"), {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(source.uri.toUri(), "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { Toast.makeText(context, missingMessage, Toast.LENGTH_LONG).show() }
            })
        }
        status?.let { StudioNote(it) }
    }
}
