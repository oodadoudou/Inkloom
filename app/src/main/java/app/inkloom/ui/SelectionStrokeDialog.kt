package app.inkloom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.inkloom.engine.v5.selection.SelectionStrokePlacement
import app.inkloom.engine.v5.surface.MaskTileSurfaceSnapshot
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

internal data class SelectionStrokeRequest(
    val selection: MaskTileSurfaceSnapshot, val rasterWidth: Int, val rasterHeight: Int,
    val widthPixels: Float, val placement: SelectionStrokePlacement, val colorArgb: Int, val opacity: Float,
)

/** Pixel width is independent of brush dynamics and view zoom. Cancel never touches the selection. */
@Composable
internal fun SelectionStrokeDialog(
    initialColor: Int, initialOpacity: Float, language: UiLanguage,
    onApply: (Float, SelectionStrokePlacement, Int, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var widthText by rememberSaveable { mutableStateOf("3") }
    var placement by rememberSaveable { mutableStateOf(SelectionStrokePlacement.CENTER) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    var opacity by rememberSaveable { mutableFloatStateOf(initialOpacity.coerceIn(0f, 1f)) }
    // Dialog content runs in its own window with its own focus owner. Reading these here would
    // clear focus in the activity window and leave the text field focused, so closing a nested
    // picker could re-raise the keyboard and move the dialog's buttons under the user's finger.
    var dialogFocus by remember { mutableStateOf<FocusManager?>(null) }
    var dialogKeyboard by remember { mutableStateOf<SoftwareKeyboardController?>(null) }
    fun close(action: () -> Unit) { dialogFocus?.clearFocus(force = true); dialogKeyboard?.hide(); action() }
    var pickerOpen by remember { mutableStateOf(false) }
    val width = widthText.toFloatOrNull()?.takeIf { it.isFinite() && it in .25f..256f }
    if (pickerOpen) StandardColorPickerDialog(color, language,
        onConfirm = { color = it; pickerOpen = false }, onDismiss = { pickerOpen = false })
    StudioDialog(
        modifier = Modifier.imePadding(),
        properties = DialogProperties(decorFitsSystemWindows = false),
        onDismissRequest = { close(onDismiss) },
        title = { Text(tr("Stroke selection", "选区描边")) },
        text = {
            val focus = LocalFocusManager.current; val keyboard = LocalSoftwareKeyboardController.current
            SideEffect { dialogFocus = focus; dialogKeyboard = keyboard }
            Column(Modifier.widthIn(max = 380.dp).heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioOutlinedTextField(widthText, { widthText = it }, label = { Text(tr("Width (px)", "宽度（像素）")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { close {} }), singleLine = true,
                isError = width == null, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Stroke width" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionStrokePlacement.entries.forEach { option ->
                    val label = when (option) {
                        SelectionStrokePlacement.INSIDE -> tr("Inside", "内侧")
                        SelectionStrokePlacement.CENTER -> tr("Center", "居中")
                        SelectionStrokePlacement.OUTSIDE -> tr("Outside", "外侧")
                    }
                    StudioFilterChip(placement == option, { placement = option }, label = { Text(label) },
                        modifier = Modifier.semantics { contentDescription = "Stroke ${option.name.lowercase()}" })
                }
            }
            TextButton(onClick = { pickerOpen = true }, modifier = Modifier.semantics { contentDescription = "Stroke color" }) {
                Box(Modifier.size(24.dp).background(Color(color)))
                Spacer(Modifier.width(10.dp)); Text(tr("Color…", "颜色…"))
            }
            Text("${tr("Opacity", "不透明度")} ${(opacity * 100).toInt()}%")
            BrushParameterSlider(opacity, { opacity = it }, valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Stroke opacity" })
        } },
        confirmButton = { TextButton(onClick = { width?.let { close { onApply(it, placement, color, opacity) } } },
            enabled = width != null && opacity > 0f && color ushr 24 > 0) { Text(tr("Apply stroke", "应用描边")) } },
        dismissButton = { TextButton(onClick = { close(onDismiss) }) { Text(tr("Cancel stroke", "取消描边")) } },
    )
}
