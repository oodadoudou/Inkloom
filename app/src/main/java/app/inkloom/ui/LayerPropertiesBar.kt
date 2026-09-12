package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.document.InkLayer
import app.inkloom.document.LayerBlendMode
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import app.inkloom.ui.theme.*
import kotlin.math.roundToInt

/** Only executable modes belong here. Grouping is presentation, never a rendering fallback. */
internal val layerBlendGroups = listOf(
    listOf(LayerBlendMode.NORMAL),
    listOf(LayerBlendMode.DARKEN, LayerBlendMode.MULTIPLY, LayerBlendMode.COLOR_BURN),
    listOf(LayerBlendMode.LIGHTEN, LayerBlendMode.SCREEN, LayerBlendMode.COLOR_DODGE, LayerBlendMode.ADD),
    listOf(LayerBlendMode.OVERLAY, LayerBlendMode.SOFT_LIGHT, LayerBlendMode.HARD_LIGHT),
    listOf(LayerBlendMode.DIFFERENCE, LayerBlendMode.EXCLUSION),
    listOf(LayerBlendMode.HUE, LayerBlendMode.SATURATION, LayerBlendMode.COLOR, LayerBlendMode.LUMINOSITY),
)

/** Stays above the scrolling layer list; every control addresses the current layer explicitly. */
@Composable
internal fun LayerPropertiesBar(
    layer: InkLayer?,
    editable: Boolean,
    language: UiLanguage,
    onOpacity: (LayerOpacityAction) -> Unit,
    onMode: (LayerBlendMode) -> Unit,
) {
    var modesOpen by remember(layer?.id) { mutableStateOf(false) }
    var numberOpen by remember(layer?.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 6.dp)
        .semantics { contentDescription = language.text("Layer properties", "图层属性") },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1.1f)) {
            TextButton(onClick = { modesOpen = true }, enabled = editable,
                modifier = Modifier.fillMaxWidth().height(40.dp).semantics {
                    contentDescription = language.text("Layer blend mode", "图层混合模式")
                }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(layer?.let { LayerBlendModeDisplayName.of(it.blendMode, language) } ?: "—",
                    Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
            }
            DropdownMenu(modesOpen && editable, { modesOpen = false },
                Modifier.widthIn(min = 172.dp).heightIn(max = 320.dp).semantics {
                    contentDescription = language.text("Layer blend modes", "图层混合模式列表")
                }) {
                layerBlendGroups.forEachIndexed { index, group ->
                    if (index > 0) HorizontalDivider(color = OutlineSoft)
                    group.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(LayerBlendModeDisplayName.of(mode, language), fontSize = 13.sp) },
                            trailingIcon = { if (mode == layer?.blendMode) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) },
                            onClick = { modesOpen = false; onMode(mode) },
                        )
                    }
                }
            }
        }
        if (editable && layer != null) key(layer.id) {
            LayerOpacitySlider(layer.opacity, language.text("Layer opacity", "图层不透明度"), onOpacity, Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        TextButton({ numberOpen = true }, enabled = editable,
            modifier = Modifier.width(46.dp).height(40.dp).semantics {
                contentDescription = language.text("Edit layer opacity", "输入图层不透明度")
            }, contentPadding = PaddingValues(0.dp)) {
            Text(layer?.let { "${(it.opacity * 100).roundToInt()}%" } ?: "—", fontSize = 12.sp, maxLines = 1)
        }
    }
    if (numberOpen && editable && layer != null) LayerOpacityNumberDialog(
        layer, language, onOpacity, onClose = { numberOpen = false },
    )
}

@Composable
private fun LayerOpacityNumberDialog(
    layer: InkLayer,
    language: UiLanguage,
    onAction: (LayerOpacityAction) -> Unit,
    onClose: () -> Unit,
) {
    var input by remember(layer.id) {
        val text = (layer.opacity * 100).roundToInt().toString()
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    val valid = input.text.toIntOrNull()?.takeIf { it in 0..100 }
    val latest by rememberUpdatedState(onAction)
    DisposableEffect(layer.id) { onDispose { latest(LayerOpacityAction.Cancel) } }
    fun cancel() { onAction(LayerOpacityAction.Cancel); onClose() }
    fun apply() {
        val percent = valid ?: return
        onAction(LayerOpacityAction.Preview(percent / 100f))
        onAction(LayerOpacityAction.Commit)
        onClose()
    }
    AlertDialog(
        onDismissRequest = ::cancel,
        modifier = Modifier.widthIn(max = 320.dp),
        title = { Text(language.text("Layer opacity", "图层不透明度")) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            val focus = LocalFocusManager.current
            OutlinedTextField(input, { next ->
                if (next.text.length <= 3 && next.text.all(Char::isDigit)) {
                    input = next
                    next.text.toIntOrNull()?.takeIf { it in 0..100 }?.let { onAction(LayerOpacityAction.Preview(it / 100f)) }
                }
            }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(language.text("Opacity (%)", "不透明度（%）")) },
                isError = input.text.isNotEmpty() && valid == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(force = true); keyboard?.hide(); apply() }))
        },
        confirmButton = { LayerPropertyDialogButton(language.text("OK", "确定"),
            language.text("Apply layer opacity", "应用图层不透明度"), valid != null, ::apply) },
        dismissButton = { LayerPropertyDialogButton(language.text("Cancel", "取消"),
            language.text("Cancel layer opacity", "取消图层不透明度"), true, ::cancel) },
    )
}

/** The button runs inside the dialog window and releases that window's keyboard owner. */
@Composable
private fun LayerPropertyDialogButton(label: String, description: String, enabled: Boolean, action: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    TextButton({ focus.clearFocus(force = true); keyboard?.hide(); action() }, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }) { Text(label) }
}
