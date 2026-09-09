package app.inkloom.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.engine.v5.tools.SelectionTransformMode
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import java.util.Locale

@Composable
internal fun SelectionTransformToolbar(
    state: SelectionTransformUiState,
    language: UiLanguage,
    onMode: (SelectionTransformMode) -> Unit,
    interpolation: app.inkloom.engine.v5.tools.SelectionInterpolation,
    onInterpolation: (app.inkloom.engine.v5.tools.SelectionInterpolation) -> Unit,
    onDimensions: (Float, Float, Float) -> Unit,
    onFlip: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSnapping: (Boolean, Boolean, Float) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun t(en: String, zh: String) = language.text(en, zh)
    var dimensionsOpen by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), color = Color.White, shadowElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                SelectionTransformMode.entries.forEach { mode ->
                    TextButton(onClick = { onMode(mode) }, modifier = Modifier.widthIn(min = 100.dp).height(36.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = if (state.mode == mode) Color(0xff1688f8) else Color(0xff303030))) {
                        Text(when (mode) { SelectionTransformMode.FREEFORM -> t("Freeform", "自由变换"); SelectionTransformMode.UNIFORM -> t("Uniform", "等比变换"); SelectionTransformMode.DISTORT -> t("Distort", "扭曲"); SelectionTransformMode.WARP -> t("Warp", "网格变形") })
                    }
                }
                TextButton(onClick = { onFinish(false) }) { Text(t("Cancel transform", "取消变换")) }
                TextButton(onClick = { onFinish(true) }) { Text(t("Apply transform", "应用变换")) }
            }
            if(state.mixed) Text(t("Apply converts this layer’s images and objects to editable pixels. Undo restores the originals.", "应用后，本图层的图片和对象转为可绘制像素；撤销可恢复原对象。"),fontSize=11.sp,color=Color.DarkGray)
            HorizontalDivider(Modifier.widthIn(max = 880.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Box {
                var snapOpen by remember { mutableStateOf(false) }
                TextButton(onClick={ snapOpen=true }) { Text(t("Snapping", "吸附")) }
                DropdownMenu(snapOpen,{snapOpen=false}) {
                    DropdownMenuItem(text={Text(t("Snapping", "吸附"))},leadingIcon={Checkbox(state.snapping,null)},onClick={onSnapping(!state.snapping,state.magnetics,state.snapDistance)})
                    DropdownMenuItem(text={Text(t("Magnetics", "磁力方向"))},leadingIcon={Checkbox(state.magnetics,null)},onClick={onSnapping(state.snapping,!state.magnetics,state.snapDistance)})
                    Text(t("Distance", "距离")+" ${state.snapDistance.toInt()} px",Modifier.padding(horizontal=16.dp))
                    Slider(state.snapDistance,{onSnapping(state.snapping,state.magnetics,it)},valueRange=1f..50f,modifier=Modifier.width(240.dp).padding(horizontal=16.dp))
                }
            }
            listOf(Triple("←",-1f,0f),Triple("↑",0f,-1f),Triple("↓",0f,1f),Triple("→",1f,0f)).forEach { (label,x,y) ->
                TextButton(onClick={onNudge(x,y)},modifier=Modifier.semantics { contentDescription=t("Nudge $label 1 px", "微移 $label 1 像素") }) { Text(label) }
            }
            Box {
                var samplingOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { samplingOpen = true }) { Text(t("Interpolation", "插值")) }
                DropdownMenu(samplingOpen, { samplingOpen = false }) {
                    app.inkloom.engine.v5.tools.SelectionInterpolation.entries.forEach { method ->
                        DropdownMenuItem(text = { Text(when (method) {
                            app.inkloom.engine.v5.tools.SelectionInterpolation.NEAREST -> t("Nearest neighbor", "最近邻")
                            app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR -> t("Bilinear", "双线性")
                            app.inkloom.engine.v5.tools.SelectionInterpolation.BICUBIC -> t("Bicubic", "双三次")
                        }) }, leadingIcon = { RadioButton(selected = method == interpolation, onClick = null) },
                            onClick = { onInterpolation(method); samplingOpen = false })
                    }
                }
            }
            TextButton(onClick = { dimensionsOpen = true }, modifier = Modifier.semantics { contentDescription = t("Transform dimensions", "变换尺寸") }) {
                Text(t("Dimensions", "尺寸") + " · ${state.pose.width.toInt()} × ${state.pose.height.toInt()} · ${state.pose.angle.toInt()}°", fontSize = 12.sp)
            }
            TextButton(onClick = { onFlip(true) }) { Text(t("Flip horizontal", "水平翻转")) }
            TextButton(onClick = { onFlip(false) }) { Text(t("Flip vertical", "垂直翻转")) }
            TextButton(onClick = onReset) { Text(t("Reset transform", "重置变换")) }
            }
        }
    }
    if (dimensionsOpen) {
        fun number(value: Float) = String.format(Locale.ROOT, "%.2f", value)
        var width by remember { mutableStateOf(number(state.pose.width)) }
        var height by remember { mutableStateOf(number(state.pose.height)) }
        var angle by remember { mutableStateOf(number(state.pose.angle)) }
        val ratio = remember { state.pose.width / state.pose.height }
        var linked by remember { mutableStateOf(state.mode == SelectionTransformMode.UNIFORM) }
        val w = width.toFloatOrNull(); val h = height.toFloatOrNull(); val a = angle.toFloatOrNull()
        val valid = w != null && h != null && a != null && w.isFinite() && h.isFinite() && a.isFinite() && w in 1f..65536f && h in 1f..65536f
        val submit = { if (valid) { onDimensions(w!!, h!!, a!!); dimensionsOpen = false } }
        Dialog(onDismissRequest = { dimensionsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 360.dp), shape = MaterialTheme.shapes.extraLarge, color = Color.White) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(t("Transform dimensions", "变换尺寸"), style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(width, { value -> width = value; if (linked) value.toFloatOrNull()?.takeIf(Float::isFinite)?.let { height = number(it / ratio) } },
                                label = { Text(t("Width (px)", "宽度（像素）")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(height, { value -> height = value; if (linked) value.toFloatOrNull()?.takeIf(Float::isFinite)?.let { width = number(it * ratio) } },
                                label = { Text(t("Height (px)", "高度（像素）")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(linked, { linked = it; if (it) width.toFloatOrNull()?.takeIf(Float::isFinite)?.let { value -> height = number(value / ratio) } })
                                Text(t("Keep aspect ratio", "保持宽高比"))
                            }
                            OutlinedTextField(angle, { angle = it }, label = { Text(t("Rotation (degrees)", "旋转角度")) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }))
                            if (!valid) Text(t("Enter finite dimensions from 1 to 65536 pixels and a rotation angle.", "请输入 1 至 65536 像素的有效尺寸和旋转角度。"), color = MaterialTheme.colorScheme.error)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { dimensionsOpen = false }) { Text(t("Cancel", "取消")) }
                            TextButton(enabled = valid, onClick = submit) { Text(t("Update dimensions", "更新尺寸")) }
                        }
                    }
                }
            }
        }
    }
}
