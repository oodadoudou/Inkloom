package app.inkloom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.text
import app.inkloom.ui.theme.*

/**
 * What an empty Library section says, so a blank area never reads as a loading failure. It points to
 * the header's New Note and Import instead of repeating those buttons beside them.
 */
internal enum class LibraryEmptyReason { NO_NOTEBOOKS, NO_MATCHES, FOLDER, RECENT, FAVORITES, ARCHIVED, TRASH, PDF }

internal fun libraryEmptyReason(filter: LibraryFilter, inFolder: Boolean, searching: Boolean): LibraryEmptyReason = when {
    searching -> LibraryEmptyReason.NO_MATCHES
    filter == LibraryFilter.TRASH -> LibraryEmptyReason.TRASH
    filter == LibraryFilter.ARCHIVED -> LibraryEmptyReason.ARCHIVED
    filter == LibraryFilter.FAVORITES -> LibraryEmptyReason.FAVORITES
    filter == LibraryFilter.PDF -> LibraryEmptyReason.PDF
    filter == LibraryFilter.RECENT -> LibraryEmptyReason.RECENT
    inFolder -> LibraryEmptyReason.FOLDER
    else -> LibraryEmptyReason.NO_NOTEBOOKS
}

@Composable
internal fun LibraryEmptyState(reason: LibraryEmptyReason, search: String, modifier: Modifier = Modifier) {
    val language = LocalUiLanguage.current
    fun tr(en: String, zh: String) = language.text(en, zh)
    val title = when (reason) {
        LibraryEmptyReason.NO_NOTEBOOKS -> tr("No notebooks yet", "还没有笔记本")
        LibraryEmptyReason.NO_MATCHES -> tr("No notebooks match “${search.trim()}”", "没有与“${search.trim()}”匹配的笔记本")
        LibraryEmptyReason.FOLDER -> tr("This folder is empty", "此文件夹是空的")
        LibraryEmptyReason.RECENT -> tr("No recent notebooks", "没有最近打开的笔记本")
        LibraryEmptyReason.FAVORITES -> tr("No favorites yet", "还没有收藏")
        LibraryEmptyReason.ARCHIVED -> tr("Nothing archived", "没有归档的笔记本")
        LibraryEmptyReason.TRASH -> tr("Trash is empty", "废纸篓是空的")
        LibraryEmptyReason.PDF -> tr("No PDF notebooks", "没有 PDF 笔记本")
    }
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp).semantics { contentDescription = title },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.MenuBook, null, Modifier.size(40.dp), tint = MutedInk)
        Text(title, color = Ink, fontSize = 20.sp, textAlign = TextAlign.Center)
    }
}
