package app.inkloom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.settings.EditorPanel
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.MutedInk
import app.inkloom.ui.theme.OutlineSoft

internal fun editorPanelTitle(panel: EditorPanel, language: UiLanguage): String = language.text(
    when (panel) {
        EditorPanel.PAGES -> "Pages"; EditorPanel.BRUSHES -> "Brushes"; EditorPanel.LAYERS -> "Layers"
        EditorPanel.SUBTOOLS -> "Sub tools"; EditorPanel.PROPERTIES -> "Tool property"; EditorPanel.NAVIGATOR -> "Navigator"; EditorPanel.SUBVIEW -> "Sub view"
        EditorPanel.WHEEL -> "Wheel"; EditorPanel.PALETTES -> "Palettes"
        EditorPanel.MIX -> "Mix"; EditorPanel.NEARBY -> "Nearby"
    },
    when (panel) {
        EditorPanel.PAGES -> "页面"; EditorPanel.BRUSHES -> "笔刷"; EditorPanel.LAYERS -> "图层"
        EditorPanel.SUBTOOLS -> "子工具"; EditorPanel.PROPERTIES -> "工具属性"; EditorPanel.NAVIGATOR -> "导航器"; EditorPanel.SUBVIEW -> "子视图"
        EditorPanel.WHEEL -> "色环"; EditorPanel.PALETTES -> "色板"
        EditorPanel.MIX -> "混色"; EditorPanel.NEARBY -> "近似色"
    },
)

internal fun editorPanelIcon(panel: EditorPanel): ImageVector = when (panel) {
    EditorPanel.PAGES -> Icons.Outlined.Description
    EditorPanel.BRUSHES -> Icons.Outlined.Brush
    EditorPanel.LAYERS -> Icons.Outlined.Layers
    EditorPanel.SUBTOOLS -> Icons.Outlined.GridView
    EditorPanel.PROPERTIES -> Icons.Outlined.Tune
    EditorPanel.NAVIGATOR -> Icons.Outlined.Explore
    EditorPanel.SUBVIEW -> Icons.Outlined.PhotoLibrary
    EditorPanel.WHEEL, EditorPanel.PALETTES -> Icons.Outlined.Palette
    EditorPanel.MIX -> Icons.Outlined.BlurOn
    EditorPanel.NEARBY -> Icons.Outlined.Colorize
}

/** A persistent window list: closing a panel never removes its slot from the saved layout. */
@Composable
internal fun EditorViewManagerDialog(
    openPanels: Set<EditorPanel>,
    onToggle: (EditorPanel, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalUiLanguage.current
    fun tr(en: String, zh: String) = language.text(en, zh)
    StudioDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 400.dp),
        title = { Text(tr("Views", "视图")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                listOf(
                    tr("Notebook", "笔记") to listOf(EditorPanel.PAGES, EditorPanel.NAVIGATOR, EditorPanel.SUBVIEW),
                    tr("Painting", "绘画") to listOf(EditorPanel.BRUSHES, EditorPanel.LAYERS, EditorPanel.SUBTOOLS, EditorPanel.PROPERTIES),
                    tr("Colors", "颜色") to listOf(EditorPanel.WHEEL, EditorPanel.PALETTES, EditorPanel.MIX, EditorPanel.NEARBY),
                ).forEachIndexed { index, (group, panels) ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 7.dp), color = OutlineSoft)
                    Text(group, Modifier.padding(start = 12.dp, top = 3.dp, bottom = 3.dp), color = MutedInk, fontSize = 11.sp)
                    panels.forEach { panel ->
                        val shown = panel in openPanels
                        val name = editorPanelTitle(panel, language)
                        Row(
                            Modifier.fillMaxWidth()
                                .semantics { contentDescription = tr(if (shown) "Hide $name view" else "Show $name view", if (shown) "隐藏${name}视图" else "显示${name}视图") }
                                .clickable { onToggle(panel, !shown) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(editorPanelIcon(panel), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(name, Modifier.weight(1f), fontSize = 13.sp)
                            if (shown) Icon(Icons.Outlined.Check, tr("Shown", "已显示"), Modifier.size(17.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Done", "完成")) } },
    )
}
