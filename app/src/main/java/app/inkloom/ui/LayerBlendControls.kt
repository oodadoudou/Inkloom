package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.InkLayer
import app.inkloom.document.LayerBlendMode
import app.inkloom.ui.theme.*
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

/** Only executable modes belong here. Grouping is presentation, never a rendering fallback. */
internal val layerBlendGroups = listOf(
    listOf(LayerBlendMode.NORMAL),
    listOf(LayerBlendMode.DARKEN, LayerBlendMode.MULTIPLY, LayerBlendMode.COLOR_BURN),
    listOf(LayerBlendMode.LIGHTEN, LayerBlendMode.SCREEN, LayerBlendMode.COLOR_DODGE, LayerBlendMode.ADD),
    listOf(LayerBlendMode.OVERLAY, LayerBlendMode.SOFT_LIGHT, LayerBlendMode.HARD_LIGHT),
    listOf(LayerBlendMode.DIFFERENCE, LayerBlendMode.EXCLUSION),
    listOf(LayerBlendMode.HUE, LayerBlendMode.SATURATION, LayerBlendMode.COLOR, LayerBlendMode.LUMINOSITY),
)

@Composable
internal fun LayerBlendControls(layer: InkLayer, language: UiLanguage, onOpacity: (LayerOpacityAction) -> Unit, onMode: (LayerBlendMode) -> Unit) {
    val orderedModes = layerBlendGroups.flatMap { it + listOf<LayerBlendMode?>(null) }
    val scroll = rememberLazyListState((orderedModes.indexOf(layer.blendMode) - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxWidth().background(SageContainer)) {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(language.text("Opacity", "不透明度"), Modifier.width(68.dp), fontSize = 12.sp)
            LayerOpacitySlider(layer.opacity, language.text("Layer opacity", "图层不透明度"), onOpacity, Modifier.weight(1f))
            Text("${(layer.opacity * 100).toInt()}%", Modifier.padding(start = 8.dp), fontSize = 12.sp)
        }
        LazyColumn(Modifier.fillMaxWidth().height(176.dp).semantics { contentDescription = "Layer blend modes" }, state = scroll) {
            layerBlendGroups.forEachIndexed { index, modes ->
                if (index > 0) item { HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = OutlineSoft) }
                items(modes, key = { it.name }) { mode ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)
                        .background(if (mode == layer.blendMode) Sage else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onMode(mode) }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(LayerBlendModeDisplayName.of(mode, language), color = if (mode == layer.blendMode) androidx.compose.ui.graphics.Color.White else Ink, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
