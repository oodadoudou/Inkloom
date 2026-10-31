package app.inkloom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.MutedInk

@Composable
internal fun BrushFolderImportProgressDialog(progress: String, language: UiLanguage, onStop: () -> Unit) {
    StudioDialog(
        onDismissRequest = {},
        title = { Text(language.text("Importing brushes", "正在导入笔刷")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(progress)
            }
        },
        confirmButton = { TextButton(onClick = onStop) { Text(language.text("Stop import", "停止导入")) } },
    )
}

@Composable
internal fun DamagedBackupRecoveryDialog(issues: List<String>, language: UiLanguage, onDismiss: () -> Unit, onRecover: () -> Unit) {
    StudioDialog(
        onDismissRequest = onDismiss,
        title = { Text(language.text("Backup is damaged", "备份已损坏")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(language.text("Nothing was restored.", "尚未恢复任何内容。"))
                issues.take(4).forEach { Text(it, color = MutedInk, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onRecover) { Text(language.text("Recover intact items", "救回完好内容")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(language.text("Cancel", "取消")) } },
    )
}
