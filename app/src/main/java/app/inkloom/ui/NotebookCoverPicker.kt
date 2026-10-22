package app.inkloom.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

@Composable
internal fun NotebookCoverPicker(
    title: String,
    covers: List<NotebookCoverTemplate>,
    style: CoverStyle,
    coverId: String?,
    importing: Boolean,
    language: UiLanguage,
    onSelect: (CoverStyle, String?) -> Unit,
    onImport: () -> Unit,
    onBrowse: () -> Unit,
    onDelete: (String) -> Unit,
) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var mine by remember { mutableStateOf(coverId != null) }
    LaunchedEffect(coverId) { if (coverId != null) mine = true }
    var deleting by remember { mutableStateOf<NotebookCoverTemplate?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Cover", "封面"), Modifier.weight(1f), color = MutedInk, fontSize = 12.sp)
            IconButton(onImport, enabled = !importing) { Icon(Icons.Outlined.AddPhotoAlternate, tr("Import cover image", "导入封面图片")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioFilterChip(!mine, { onBrowse(); mine = false }, label = { Text(tr("Preset covers", "预设封面")) })
            StudioFilterChip(mine, { onBrowse(); mine = true }, label = { Text(tr("My covers", "我的封面")) })
        }
        if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mine) items(covers, key = NotebookCoverTemplate::id) { cover ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SelectionRing(coverId == cover.id) {
                        Card(Modifier.size(CoverWidth, CoverHeight).semantics { contentDescription = "${tr("Use cover", "使用封面")} ${cover.name}" }
                            .selectable(selected = coverId == cover.id, onClick = { onSelect(CoverStyle.CUSTOM_IMAGE, cover.id) }),
                            border = CardDefaults.outlinedCardBorder()) {
                            NotebookCover(InkDocument(title = title, coverStyle = CoverStyle.CUSTOM_IMAGE, coverImagePath = cover.assetPath), Modifier.fillMaxSize())
                        }
                    }
                    Row(Modifier.width(CoverWidth + 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cover.name, Modifier.weight(1f), fontSize = 10.sp, maxLines = 1)
                        IconButton({ deleting = cover }, Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "${tr("Delete cover", "删除封面")} ${cover.name}", Modifier.size(16.dp))
                        }
                    }
                }
            } else items(NotebookCoverCatalog.creationStyles) { candidate ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SelectionRing(coverId == null && style == candidate) {
                        Card(Modifier.size(CoverWidth, CoverHeight)
                            .semantics { contentDescription = "${tr("Use cover", "使用封面")} ${NotebookCoverCatalog.displayName(candidate, language)}" }
                            .selectable(selected = coverId == null && style == candidate, onClick = { onSelect(candidate, null) }),
                            border = CardDefaults.outlinedCardBorder()) {
                            NotebookCover(InkDocument(title = title, coverStyle = candidate, coverArgb = NotebookCoverCatalog.defaultArgb(candidate)), Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(NotebookCoverCatalog.displayName(candidate, language), fontSize = 11.sp,
                        color = if (coverId == null && style == candidate) Ink else MutedInk)
                }
            }
        }
        if (mine && covers.isEmpty() && !importing) Text(tr("Import an image to save a reusable cover", "导入图片，保存为可复用封面"), fontSize = 12.sp, color = MutedInk)
    }
    deleting?.let { cover ->
        StudioDialog(onDismissRequest = { deleting = null }, title = { Text(tr("Delete cover", "删除封面")) },
            text = { Text(tr("Remove ${cover.name} from My covers? Existing notebooks keep their cover.", "从我的封面中移除 ${cover.name}？已使用它的笔记会保留原封面。")) },
            confirmButton = { TextButton({ onDelete(cover.id); deleting = null }) { Text(tr("Delete", "删除")) } },
            dismissButton = { TextButton({ deleting = null }) { Text(tr("Cancel", "取消")) } })
    }
}

private val CoverWidth = 80.dp
private val CoverHeight = 104.dp

/**
 * Marks the chosen cover with a ring separated by a gap, so the selection reads on light and dark
 * covers alike instead of blending into a cover of the same tone.
 */
@Composable
private fun SelectionRing(selected: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.border(2.dp, if (selected) Ink else Color.Transparent, RoundedCornerShape(15.dp)).padding(3.dp)) { content() }
}
