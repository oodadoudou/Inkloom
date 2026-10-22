package app.inkloom.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.inkloom.engine.v5.tools.SelectionTransformMode
import app.inkloom.engine.v5.tools.SelectionAlignment
import app.inkloom.engine.v5.tools.SelectionDistributionMode
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
    onDimensions: (Float, Float, Float, Float, Float) -> Unit,
    onResetPivot: () -> Unit,
    onPivotEditing: (Boolean) -> Unit,
    onSkewBy: (Float, Float) -> Unit,
    onMeshDensity: (Int, Int) -> Unit,
    onClearPuppetPins: () -> Unit,
    onReference: (SelectionAlignmentReference) -> Unit,
    onAlign: (SelectionAlignment) -> Unit,
    onDistribute: (Boolean, SelectionDistributionMode) -> Unit,
    onFlip: (Boolean) -> Unit,
    onReset: () -> Unit,
    onSnapping: (Boolean, Boolean, Float) -> Unit,
    onNudge: (Float, Float) -> Unit,
    onFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun t(en: String, zh: String) = language.text(en, zh)
    var dimensionsOpen by remember { mutableStateOf(false) }
    var skewOpen by remember { mutableStateOf(false) }
    Surface(modifier.widthIn(max = 640.dp), shape = RoundedCornerShape(14.dp), color = app.inkloom.ui.theme.Paper, shadowElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                SelectionTransformMode.entries.forEach { mode ->
                    TextButton(onClick = { onMode(mode) }, modifier = Modifier.widthIn(min = 72.dp).height(48.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = if (state.mode == mode) app.inkloom.ui.theme.SageContainer else Color.Transparent, contentColor = if (state.mode == mode) app.inkloom.ui.theme.Ink else app.inkloom.ui.theme.MutedInk)) {
                        Text(when (mode) { SelectionTransformMode.FREEFORM -> t("Freeform", "自由变换"); SelectionTransformMode.UNIFORM -> t("Uniform", "等比变换"); SelectionTransformMode.DISTORT -> t("Distort", "扭曲"); SelectionTransformMode.SKEW -> t("Skew", "歪斜"); SelectionTransformMode.WARP -> t("Warp", "网格变形"); SelectionTransformMode.PUPPET -> t("Puppet", "操控变形") }, fontSize = 12.sp)
                    }
                }
                IconButton(onClick = { onFinish(false) }) { Icon(Icons.Outlined.Close, t("Cancel transform", "取消变换")) }
                IconButton(onClick = { onFinish(true) }) { Icon(Icons.Outlined.Check, t("Apply transform", "应用变换")) }
            }
            if(state.mixed) Text(t("Text and images remain editable after deformation.", "文字和图片变形后仍可编辑。"),fontSize=11.sp,color=app.inkloom.ui.theme.MutedInk)
            HorizontalDivider(Modifier.widthIn(max = 620.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (state.mode == SelectionTransformMode.WARP || state.mode == SelectionTransformMode.PUPPET) Box {
                var meshOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { meshOpen = true }) {
                    Text(t("Grid ${state.pose.meshColumns - 1}×${state.pose.meshRows - 1}", "网格 ${state.pose.meshColumns - 1}×${state.pose.meshRows - 1}"))
                }
                DropdownMenu(meshOpen, { meshOpen = false }) {
                    (2..8).forEach { divisions ->
                        StudioMenuItem(
                            text = { Text("$divisions × $divisions") },
                            leadingIcon = { StudioRadioButton(state.pose.meshColumns == divisions + 1 && state.pose.meshRows == divisions + 1, null) },
                            onClick = { onMeshDensity(divisions + 1, divisions + 1); meshOpen = false },
                        )
                    }
                }
            }
            if (state.mode == SelectionTransformMode.PUPPET) TextButton(onClick = onClearPuppetPins) {
                Text(t("Clear pins", "清除控制点"))
            }
            if (state.mode == SelectionTransformMode.SKEW) TextButton(onClick = { skewOpen = true }) {
                Text(t("Skew by pixels", "数值歪斜"))
            }
            Box {
                var alignOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { alignOpen = true }) { Text(t("Align", "对齐")) }
                DropdownMenu(alignOpen, { alignOpen = false }) {
                    val automaticTarget = when (state.resolvedAlignmentReference) {
                        SelectionAlignmentReference.ACTIVE_LAYER -> t("active layer", "当前图层")
                        SelectionAlignmentReference.GUIDES -> t("guides", "辅助线")
                        else -> t("canvas", "画布")
                    }
                    StudioMenuItem(
                        text = { Text("${t("Auto", "自动")} · $automaticTarget") },
                        leadingIcon = { StudioRadioButton(state.alignmentReference == SelectionAlignmentReference.AUTO, null) },
                        onClick = { onReference(SelectionAlignmentReference.AUTO) },
                    )
                    StudioMenuItem(
                        text = { Text(t("Canvas reference", "参考画布")) },
                        leadingIcon = { StudioRadioButton(state.alignmentReference == SelectionAlignmentReference.CANVAS, null) },
                        onClick = { onReference(SelectionAlignmentReference.CANVAS) },
                    )
                    StudioMenuItem(
                        text = { Text(t("Active layer reference", "参考当前图层")) },
                        leadingIcon = { StudioRadioButton(state.alignmentReference == SelectionAlignmentReference.ACTIVE_LAYER, null) },
                        enabled = state.layerCount >= 2,
                        onClick = { onReference(SelectionAlignmentReference.ACTIVE_LAYER) },
                    )
                    StudioMenuItem(text = { Text(t("Selection reference", "参考选区")) },
                        leadingIcon = { StudioRadioButton(state.alignmentReference == SelectionAlignmentReference.SELECTION, null) },
                        onClick = { onReference(SelectionAlignmentReference.SELECTION) })
                    StudioMenuItem(text = { Text(t("Alignment guides reference", "参考辅助线")) },
                        leadingIcon = { StudioRadioButton(state.alignmentReference == SelectionAlignmentReference.GUIDES, null) },
                        onClick = { onReference(SelectionAlignmentReference.GUIDES) })
                    HorizontalDivider()
                    SelectionAlignment.entries.forEach { alignment ->
                        StudioMenuItem(text = { Text(when (alignment) {
                            SelectionAlignment.LEFT -> t("Align left", "左对齐")
                            SelectionAlignment.HORIZONTAL_CENTER -> t("Center horizontally", "水平居中")
                            SelectionAlignment.RIGHT -> t("Align right", "右对齐")
                            SelectionAlignment.TOP -> t("Align top", "顶部对齐")
                            SelectionAlignment.VERTICAL_CENTER -> t("Center vertically", "垂直居中")
                            SelectionAlignment.BOTTOM -> t("Align bottom", "底部对齐")
                        }) }, onClick = { onAlign(alignment); alignOpen = false })
                    }
                    HorizontalDivider()
                    StudioMenuItem(text = { Text(t("Distribute left edges", "左边缘均分")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(true, SelectionDistributionMode.START_EDGE); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Distribute horizontal centers", "水平中心均分")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(true, SelectionDistributionMode.CENTER); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Distribute right edges", "右边缘均分")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(true, SelectionDistributionMode.END_EDGE); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Distribute top edges", "上边缘均分")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(false, SelectionDistributionMode.START_EDGE); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Distribute vertical centers", "垂直中心均分")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(false, SelectionDistributionMode.CENTER); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Distribute bottom edges", "下边缘均分")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(false, SelectionDistributionMode.END_EDGE); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Equal horizontal gaps", "水平等间距")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(true, SelectionDistributionMode.EQUAL_GAP); alignOpen = false })
                    StudioMenuItem(text = { Text(t("Equal vertical gaps", "垂直等间距")) }, enabled = state.layerCount >= 3,
                        onClick = { onDistribute(false, SelectionDistributionMode.EQUAL_GAP); alignOpen = false })
                }
            }
            Box {
                var snapOpen by remember { mutableStateOf(false) }
                TextButton(onClick={ snapOpen=true }) { Text(t("Snapping", "吸附")) }
                DropdownMenu(snapOpen,{snapOpen=false}) {
                    StudioMenuItem(text={Text(t("Snapping", "吸附"))},leadingIcon={StudioCheckbox(state.snapping,null)},onClick={onSnapping(!state.snapping,state.magnetics,state.snapDistance)})
                    StudioMenuItem(text={Text(t("Magnetics", "磁力方向"))},leadingIcon={StudioCheckbox(state.magnetics,null)},onClick={onSnapping(state.snapping,!state.magnetics,state.snapDistance)})
                    Text(t("Distance", "距离")+" ${state.snapDistance.toInt()} px",Modifier.padding(horizontal=16.dp))
                    Slider(state.snapDistance,{onSnapping(state.snapping,state.magnetics,it)},valueRange=1f..50f,modifier=Modifier.width(240.dp).padding(horizontal=16.dp))
                }
            }
            listOf(Triple("←",-1f,0f),Triple("↑",0f,-1f),Triple("↓",0f,1f),Triple("→",1f,0f)).forEach { (label,x,y) ->
                TextButton(onClick={onNudge(x,y)},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(48.dp).semantics { contentDescription=t("Nudge $label 1 px", "微移 $label 1 像素") }) { Text(label) }
            }
            Box {
                var samplingOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { samplingOpen = true }) { Text(t("Interpolation", "插值")) }
                DropdownMenu(samplingOpen, { samplingOpen = false }) {
                    app.inkloom.engine.v5.tools.SelectionInterpolation.entries.forEach { method ->
                        StudioMenuItem(text = { Text(when (method) {
                            app.inkloom.engine.v5.tools.SelectionInterpolation.NEAREST -> t("Nearest neighbor", "最近邻")
                            app.inkloom.engine.v5.tools.SelectionInterpolation.BILINEAR -> t("Bilinear", "双线性")
                            app.inkloom.engine.v5.tools.SelectionInterpolation.BICUBIC -> t("Bicubic", "双三次")
                        }) }, leadingIcon = { StudioRadioButton(selected = method == interpolation, onClick = null) },
                            onClick = { onInterpolation(method); samplingOpen = false })
                    }
                }
            }
            TextButton(onClick = { dimensionsOpen = true }, modifier = Modifier.semantics { contentDescription = t("Transform dimensions", "变换尺寸") }) {
                Text(t("Dimensions", "尺寸"), fontSize = 12.sp)
            }
            TextButton(onClick = { onPivotEditing(!state.pivotEditing) },
                colors = ButtonDefaults.textButtonColors(containerColor = if (state.pivotEditing) app.inkloom.ui.theme.SageContainer else Color.Transparent)) {
                Text(t("Move pivot", "移动基准点"), fontSize = 12.sp)
            }
            IconButton(onClick = { onFlip(true) }) { Icon(Icons.Outlined.Flip, t("Flip horizontal", "水平翻转"), Modifier.size(20.dp)) }
            IconButton(onClick = { onFlip(false) }) { Icon(Icons.Outlined.Flip, t("Flip vertical", "垂直翻转"), Modifier.size(20.dp).then(Modifier.graphicsLayer { rotationZ = 90f })) }
            IconButton(onClick = onReset) { Icon(Icons.Outlined.RestartAlt, t("Reset transform", "重置变换"), Modifier.size(20.dp)) }
            }
        }
    }
    if (skewOpen) {
        var horizontal by remember { mutableStateOf("0") }
        var vertical by remember { mutableStateOf("0") }
        val dx = horizontal.toFloatOrNull(); val dy = vertical.toFloatOrNull()
        val valid = dx?.isFinite() == true && dy?.isFinite() == true &&
            dx in -65536f..65536f && dy in -65536f..65536f
        StudioDialog(onDismissRequest = { skewOpen = false }, title = { Text(t("Skew by pixels", "数值歪斜")) },
            text = { Column { StudioOutlinedTextField(horizontal, { horizontal = it },
                label = { Text(t("Horizontal (px)", "水平（像素）")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                StudioOutlinedTextField(vertical, { vertical = it },
                    label = { Text(t("Vertical (px)", "垂直（像素）")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } },
            confirmButton = { TextButton(enabled = valid, onClick = { onSkewBy(dx!!, dy!!); skewOpen = false }) { Text(t("Apply", "应用")) } },
            dismissButton = { TextButton(onClick = { skewOpen = false }) { Text(t("Cancel", "取消")) } })
    }
    if (dimensionsOpen) {
        fun number(value: Float) = String.format(Locale.ROOT, "%.2f", value)
        var x by remember { mutableStateOf(number(state.pose.centerX - state.pose.width / 2f)) }
        var y by remember { mutableStateOf(number(state.pose.centerY - state.pose.height / 2f)) }
        var width by remember { mutableStateOf(number(state.pose.width)) }
        var height by remember { mutableStateOf(number(state.pose.height)) }
        var angle by remember { mutableStateOf(number(state.pose.angle)) }
        val ratio = remember { state.pose.width / state.pose.height }
        var linked by remember { mutableStateOf(state.mode == SelectionTransformMode.UNIFORM) }
        val px = x.toFloatOrNull(); val py = y.toFloatOrNull()
        val w = width.toFloatOrNull(); val h = height.toFloatOrNull(); val a = angle.toFloatOrNull()
        val valid = px != null && py != null && w != null && h != null && a != null &&
            listOf(px, py, w, h, a).all(Float::isFinite) && px in -65536f..65536f && py in -65536f..65536f &&
            w in 1f..65536f && h in 1f..65536f
        val submit = { if (valid) { onDimensions(px!!, py!!, w!!, h!!, a!!); dimensionsOpen = false } }
        Dialog(onDismissRequest = { dimensionsOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 360.dp), shape = MaterialTheme.shapes.extraLarge, color = app.inkloom.ui.theme.Paper) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(t("Transform dimensions", "变换尺寸"), style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(t("Enable Move pivot, then drag the crosshair on the canvas.", "开启「移动基准点」后，拖动画布上的十字准星。"), fontSize = 11.sp)
                            StudioOutlinedTextField(x, { x = it }, label = { Text(t("Position X (px)", "位置 X（像素）")) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            StudioOutlinedTextField(y, { y = it }, label = { Text(t("Position Y (px)", "位置 Y（像素）")) },
                                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            TextButton(onClick = onResetPivot) {
                                Text(t("Reset reference point to center", "基准点回到中心"))
                            }
                            StudioOutlinedTextField(width, { value -> width = value; if (linked) value.toFloatOrNull()?.takeIf(Float::isFinite)?.let { height = number(it / ratio) } },
                                label = { Text(t("Width (px)", "宽度（像素）")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            StudioOutlinedTextField(height, { value -> height = value; if (linked) value.toFloatOrNull()?.takeIf(Float::isFinite)?.let { width = number(it * ratio) } },
                                label = { Text(t("Height (px)", "高度（像素）")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StudioCheckbox(linked, { linked = it; if (it) width.toFloatOrNull()?.takeIf(Float::isFinite)?.let { value -> height = number(value / ratio) } })
                                Text(t("Keep aspect ratio", "保持宽高比"))
                            }
                            StudioOutlinedTextField(angle, { angle = it }, label = { Text(t("Rotation (degrees)", "旋转角度")) },
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
