package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

/** One action surface with a stable category path; preferences live in Settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorActionsDialog(language: UiLanguage, section: Int, onSection: (Int) -> Unit,
    onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val labels = listOf("Notebook" to "笔记", "Canvas" to "画布", "Tools" to "工具",
        "Guides" to "辅助", "Views" to "视图", "Share" to "分享")
    StudioDialog(onDismissRequest = onDismiss, title = { Text(language.text("Actions", "操作")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(language.text("Done", "完成")) } },
        text = { Column(Modifier.widthIn(max = 520.dp)) {
            // Categories wrap on narrow windows or large fonts so none is hidden off-screen.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                labels.forEachIndexed { index, label ->
                    TextButton(onClick = { onSection(index) }, modifier = Modifier.height(44.dp)
                        .background(if (section == index) SageContainer else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
                        .semantics { contentDescription = "Actions ${label.first}"; selected = section == index }) {
                        Text(language.text(label.first, label.second), fontSize = 12.sp, color = Ink)
                    }
                }
            }
            HorizontalDivider(color = OutlineSoft)
            key(section) {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), content = content)
            }
        } })
}

@Composable
internal fun EditorToolActionGrid(tools: List<Triple<app.inkloom.model.EditorTool, androidx.compose.ui.graphics.vector.ImageVector, String>>,
    onTool: (app.inkloom.model.EditorTool) -> Unit) {
    tools.chunked(3).forEach { group ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            group.forEach { (tool, icon, label) ->
                TextButton(onClick = { onTool(tool) }, modifier = Modifier.weight(1f).heightIn(min = 68.dp), contentPadding = PaddingValues(4.dp)) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(23.dp), tint = Ink)
                        Spacer(Modifier.height(5.dp))
                        Text(label, color = Ink, fontSize = 11.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
