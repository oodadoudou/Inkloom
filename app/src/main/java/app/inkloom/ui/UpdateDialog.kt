package app.inkloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.theme.MutedInk
import app.inkloom.settings.text
import app.inkloom.update.AppRelease
import app.inkloom.update.AppUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One check per app process; a newer GitHub release offers Update or Skip this version. */
@Composable
internal fun UpdatePrompt(language: UiLanguage) {
    val context = LocalContext.current
    val updater = remember(context) { AppUpdater(context) }
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<AppRelease?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var download by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) {
        if (!AppUpdater.checksEnabled || UpdateCheckSession.checked) return@LaunchedEffect
        UpdateCheckSession.checked = true
        release = updater.findUpdate()
    }
    val offered = release ?: return
    fun close() { download?.cancel(); download = null; release = null }
    StudioDialog(
        onDismissRequest = ::close,
        title = { Text("Inkloom ${offered.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (offered.notes.isNotEmpty()) Text(offered.notes.replace(MARKDOWN_MARKS, ""), fontSize = 13.sp,
                    modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()))
                progress?.let { value ->
                    LinearProgressIndicator({ value }, Modifier.fillMaxWidth())
                    Text("${(value * 100).toInt()}%", fontSize = 12.sp, color = MutedInk)
                }
                failure?.let { Text(it, fontSize = 12.sp, color = MutedInk) }
            }
        },
        dismissButton = {
            TextButton(onClick = { updater.skip(offered); close() }) { Text(language.text("Skip this version", "跳过此版本")) }
        },
        confirmButton = {
            Button(enabled = download == null, onClick = {
                failure = null
                progress = 0f
                download = scope.launch {
                    try {
                        val apk = updater.download(offered) { value -> progress = value.coerceIn(0f, 1f) }
                        updater.install(apk)
                        release = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.w("InkloomUpdate", "Update download failed", error)
                        failure = language.text("The update could not be downloaded", "更新下载失败")
                        progress = null
                    } finally {
                        download = null
                    }
                }
            }) { Text(language.text("Update", "更新")) }
        },
    )
}

private object UpdateCheckSession { var checked = false }

private val MARKDOWN_MARKS = Regex("""(?m)^#+\s*|\*\*|`""")
