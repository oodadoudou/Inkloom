package app.inkloom.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.inkloom.document.*
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.engine.v5.surface.MaskComposeMode
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SavedSelectionsPanel(
    entries: List<SavedSelection>, assets: DocumentAssetService, language: UiLanguage,
    canSave: Boolean, onSave: () -> Unit, onLoad: (SavedSelection, MaskComposeMode) -> Unit,
    onRename: (SavedSelection, String) -> Unit,
    onDelete: (SavedSelection) -> Unit, onDismiss: () -> Unit,
) {
    fun t(en: String, zh: String) = language.text(en, zh)
    var renaming by remember { mutableStateOf<SavedSelection?>(null) }
    var renameValue by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Selections", "选区"), Modifier.weight(1f))
            IconButton(onClick = onSave, enabled = canSave,
                modifier = Modifier.semantics { contentDescription = t("Save current selection", "保存当前选区") }) {
                Icon(Icons.Outlined.Add, null)
            }
        } },
        text = {
            // Keep the footer stationary when the first saved selection replaces the empty state.
            Box(Modifier.fillMaxWidth().height(240.dp)) {
            if (entries.isEmpty()) Text(t("Select an area, then tap + to save it.", "选取一个区域后，点 + 保存。"))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = SavedSelection::id) { entry ->
                    var actionMenu by remember(entry.id) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clickable { onLoad(entry, MaskComposeMode.REPLACE) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val mask by produceState<MaskTileSurfaceSnapshot?>(null, entry.surface, entry.rotationQuarterTurns) {
                            value = withContext(Dispatchers.IO) { runCatching {
                                SavedSelectionGeometry.rotated(assets.loadMaskTileSurface(entry.surface).snapshot(), entry.rotationQuarterTurns)
                            }.getOrNull() }
                        }
                        Canvas(Modifier.size(64.dp).background(Color(0xffe6e6e6))) {
                            mask?.let { snapshot -> drawIntoCanvas { target ->
                                app.inkloom.engine.v5.android.MaskTileCanvasRenderer.draw(snapshot, target.nativeCanvas,
                                    android.graphics.RectF(0f, 0f, size.width, size.height),
                                    android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                            } }
                        }
                        Text(entry.name, Modifier.weight(1f).padding(horizontal = 12.dp))
                        Box {
                            TextButton(onClick = { actionMenu = true }, modifier = Modifier.semantics {
                                contentDescription = t("Selection actions", "选区操作") + " " + entry.name
                            }) { Text("⋮") }
                            DropdownMenu(actionMenu, { actionMenu = false }) {
                                listOf(
                                    MaskComposeMode.REPLACE to t("Replace selection", "替换选区"),
                                    MaskComposeMode.ADD to t("Add to selection", "添加到选区"),
                                    MaskComposeMode.SUBTRACT to t("Subtract from selection", "从选区减去"),
                                    MaskComposeMode.INTERSECT to t("Intersect selection", "与选区交集"),
                                ).forEach { (mode, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { actionMenu = false; onLoad(entry, mode) })
                                }
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text(t("Rename", "重命名")) }, onClick = {
                                    actionMenu = false; renaming = entry; renameValue = entry.name
                                })
                            }
                        }
                        IconButton(onClick = { onDelete(entry) },
                            modifier = Modifier.semantics { contentDescription = t("Delete saved selection", "删除保存的选区") + " " + entry.name }) {
                            Icon(Icons.Outlined.DeleteOutline, null)
                        }
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Done", "完成")) } })
    renaming?.let { entry ->
        AlertDialog(onDismissRequest = { renaming = null },
            title = { Text(t("Rename selection", "重命名选区")) },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true,
                label = { Text(t("Name", "名称")) }) },
            confirmButton = { TextButton(onClick = {
                onRename(entry, renameValue.trim()); renaming = null
            }, enabled = renameValue.trim().isNotBlank()) { Text(t("Save", "保存")) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(t("Cancel", "取消")) } })
    }
}
