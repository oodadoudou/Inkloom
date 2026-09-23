package app.inkloom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.inkloom.engine.v5.tools.RasterImageFilter
import app.inkloom.settings.text
import java.util.Locale

/** Compact image-filter controls. The canvas behind the dialog is the live, cancellable preview. */
@Composable
internal fun RasterFilterDialog(onPreview: (RasterImageFilter.Effect) -> Unit,
    onApply: () -> Unit, onCancel: () -> Unit) {
    val language = LocalUiLanguage.current
    var kind by remember { mutableStateOf(0) }
    var strength by remember { mutableFloatStateOf(2f) }
    var amount by remember { mutableFloatStateOf(1.2f) }
    var wavelength by remember { mutableFloatStateOf(64f) }
    var growLine by remember { mutableStateOf(true) }
    val effect = when (kind) {
        0 -> RasterImageFilter.Effect.GaussianBlur(strength)
        1 -> RasterImageFilter.Effect.UnsharpMask(strength, amount)
        2 -> RasterImageFilter.Effect.Denoise(strength.coerceIn(1f, 3f))
        3 -> RasterImageFilter.Effect.CleanLineArt(strength)
        4 -> RasterImageFilter.Effect.AdjustLineWidth(strength.coerceIn(1f, 3f), growLine)
        else -> RasterImageFilter.Effect.Wave(strength, wavelength)
    }
    LaunchedEffect(effect) { onPreview(effect) }
    val names = listOf(
        language.text("Gaussian blur", "高斯模糊"),
        language.text("Unsharp mask", "USM 锐化"),
        language.text("Denoise", "降噪"),
        language.text("Remove stray pixels", "清理杂点"),
        language.text("Correct line width", "修正线宽"),
        language.text("Wave distortion", "波形变形"),
    )
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(language.text("Image filter", "图像滤镜")) },
        text = {
            Column(Modifier.widthIn(min = 270.dp, max = 420.dp).heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())) {
                names.forEachIndexed { index, label ->
                    FilterChip(selected = kind == index, onClick = {
                        kind = index
                        strength = when (index) { 2, 3, 4 -> 1f; 5 -> 8f; else -> 2f }
                    }, label = { Text(label) }, modifier = Modifier.padding(vertical = 1.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("${language.text("Strength", "强度")}  ${String.format(Locale.ROOT, "%.1f", strength)}")
                Slider(strength, { strength = it }, valueRange = 1f..(when (kind) { 2, 4 -> 3f; 3 -> 4f; else -> 20f }))
                if (kind == 1) {
                    Text("${language.text("Amount", "锐化量")}  ${String.format(Locale.ROOT, "%.1f", amount)}")
                    Slider(amount, { amount = it }, valueRange = 0f..4f)
                }
                if (kind == 4) {
                    Row { FilterChip(growLine, { growLine = true }, label = { Text(language.text("Thicken", "加粗")) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(!growLine, { growLine = false }, label = { Text(language.text("Thin", "变细")) }) }
                }
                if (kind == 5) {
                    Text("${language.text("Wavelength", "波长")}  ${wavelength.toInt()} px")
                    Slider(wavelength, { wavelength = it }, valueRange = 16f..512f)
                }
                Text(language.text("The canvas previews the selected pixel layer. Cancel restores it without changing history.",
                    "画布预览当前像素图层；取消后恢复原图，不写入历史。"),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onApply) { Text(language.text("Apply", "应用")) } },
        dismissButton = { TextButton(onCancel) { Text(language.text("Cancel", "取消")) } },
    )
}
