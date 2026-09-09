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
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SavedSelectionsPanel(
    entries: List<SavedSelection>, assets: DocumentAssetService, language: UiLanguage,
    canSave: Boolean, onSave: () -> Unit, onLoad: (SavedSelection) -> Unit,
    onDelete: (SavedSelection) -> Unit, onDismiss: () -> Unit,
) {
    fun t(en: String, zh: String) = language.text(en, zh)
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
                    Row(Modifier.fillMaxWidth().clickable { onLoad(entry) }.padding(vertical = 6.dp),
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
}
