package app.inkloom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text

internal object StandardColorHex {
    fun format(color: Int): String = if (color ushr 24 == 255) "#%06X".format(color and 0xffffff)
        else "#%08X".format(color)

    fun parse(text: String): Int? {
        val value = text.trim().removePrefix("#")
        if ((value.length != 6 && value.length != 8) || value.any { it.digitToIntOrNull(16) == null }) return null
        val number = value.toLongOrNull(16) ?: return null
        return if (value.length == 6) (0xff000000 or number).toInt() else number.toInt()
    }
}

/** A compact picker shared by all small current-color indicators. Nothing is committed until Done. */
@Composable
internal fun StandardColorPickerDialog(
    initialColor: Int,
    language: UiLanguage,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var draft by remember(initialColor) { mutableIntStateOf(initialColor) }
    var hex by remember(initialColor) { mutableStateOf(StandardColorHex.format(initialColor)) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun close(action: () -> Unit) { focus.clearFocus(force = true); keyboard?.hide(); action() }
    fun update(color: Int) { draft = color; hex = StandardColorHex.format(color) }
    AlertDialog(
        onDismissRequest = { close(onDismiss) },
        modifier = Modifier.widthIn(max = 390.dp),
        title = { Text(tr("Choose color", "选择颜色")) },
        text = {
            Column {
                CspColorPanel(
                    color = draft,
                    onColorPreview = ::update,
                    onColorCommit = ::update,
                    showHeader = false,
                    maximumHeight = 250.dp,
                    language = language,
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        if (input.length <= 9) {
                            hex = input
                            StandardColorHex.parse(input)?.let { draft = it }
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = tr("Hex color", "十六进制颜色") },
                    label = { Text(tr("Hex · RGB or ARGB", "十六进制 · RGB 或 ARGB")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
            }
        },
        confirmButton = { TextButton(onClick = { close { onConfirm(draft) } }) { Text(tr("Apply", "应用")) } },
        dismissButton = { TextButton(onClick = { close(onDismiss) }) { Text(tr("Cancel", "取消")) } },
    )
}
