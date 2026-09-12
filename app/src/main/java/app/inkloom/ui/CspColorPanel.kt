package app.inkloom.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import app.inkloom.color.ColorTriangle
import app.inkloom.engine.v5.diagnostics.ColorPickerInteractionTrace
import app.inkloom.settings.text
import app.inkloom.settings.UiLanguage
import app.inkloom.ui.theme.*
import kotlin.math.*

internal data class ColorPairControls(val foreground: Int, val background: Int, val secondarySelected: Boolean, val select: (Boolean) -> Unit)

/** The wheel, marker and pointer inverse share one fixed HLS triangle / HSV square model. */
@Composable
internal fun CspColorPanel(
    color: Int,
    onColorPreview: (Int) -> Unit,
    onColorCommit: (Int) -> Unit,
    initiallyExpanded: Boolean = true,
    onResizeWidth: ((Float) -> Unit)? = null,
    showHeader: Boolean = true,
    initialDraft: ColorPickerDraft? = null,
    onDraftChange: (ColorPickerDraft) -> Unit = {},
    maximumHeight: Dp = 420.dp,
    colorPair: ColorPairControls? = null,
    language: UiLanguage = UiLanguage.ENGLISH,
) {
    SideEffect { ColorPickerInteractionTrace.panelComposition() }
    fun tr(en: String, zh: String) = language.text(en, zh)
    val context = LocalContext.current
    val density = LocalDensity.current
    val preferences = remember(context) { context.getSharedPreferences("inkloom_color_palette", Context.MODE_PRIVATE) }
    var triangleMode by remember { mutableStateOf(preferences.getBoolean("wheelHlsTriangle", true)) }
    var rgbValues by remember { mutableStateOf(preferences.getBoolean("wheelRgbValues", true)) }
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var draft by remember { mutableStateOf(initialDraft ?: ColorPickerDraft.fromColor(color)) }
    var editStart by remember { mutableStateOf<ColorPickerDraft?>(null) }
    val preview by rememberUpdatedState(onColorPreview)
    val commit by rememberUpdatedState(onColorCommit)
    val draftChange by rememberUpdatedState(onDraftChange)
    LaunchedEffect(color) { draft = draft.withColor(color) }
    fun update(next: ColorPickerDraft) {
        val changed = draft.color != next.color
        draft = next; draftChange(next)
        if (changed) { ColorPickerInteractionTrace.previewWrite(); preview(next.color) }
    }
    fun publish(next: ColorPickerDraft) {
        ColorPickerInteractionTrace.workspaceCommit(); commit(next.color)
    }
    val updateLatest by rememberUpdatedState<(ColorPickerDraft) -> Unit>(::update)
    val publishLatest by rememberUpdatedState<(ColorPickerDraft) -> Unit>(::publish)
    val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(draft.hue, 1f, 1f)))
    val spectrum = remember {
        ColorPickerInteractionTrace.hueRingAllocation()
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    }
    val saturation = remember(hueColor) {
        ColorPickerInteractionTrace.saturationGradientAllocation()
        Brush.horizontalGradient(listOf(Color.White, hueColor))
    }
    val darkness = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black)) }
    val vertexPaint = remember { Paint().apply { this.color = Color.White } }
    val ringLabel = tr("Hue ring", "色相环")
    val fieldLabel = tr("Saturation and brightness field", "饱和度和明度区域")
    Column(Modifier.fillMaxWidth().background(Paper)) {
        if (showHeader) Row(Modifier.fillMaxWidth().height(32.dp).clickable { expanded = !expanded }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Color wheel", "色环"), Modifier.weight(1f), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp), tint = MutedInk)
        }
        if (expanded) {
            var visibleWheelSize by remember { mutableStateOf(IntSize.Zero) }
            val wheelInput = Modifier.onSizeChanged { visibleWheelSize = it }.pointerInput(triangleMode, visibleWheelSize) {
                if (size.width == 0 || size.height == 0) return@pointerInput
                val side = size.height.toFloat()
                val ringCenter = Offset(size.width / 2f, side / 2f)
                val outer = side / 2f - 3.dp.toPx()
                val radius = side * .392f
                val fieldWidth = (radius * if (triangleMode) 1.5f else 1.36f).roundToInt().toFloat()
                val fieldHeight = if (triangleMode) (radius * sqrt(3f)).roundToInt().toFloat() else fieldWidth
                val fieldOrigin = Offset((size.width - fieldWidth) / 2f + if (triangleMode) (radius * .25f).roundToInt() else 0,
                    (side - fieldHeight) / 2f)
                val triangle = ColorTriangle(fieldWidth, fieldHeight)
                var hueGesture = false
                pickerGesture({ draft }, { point ->
                    hueGesture = (point - ringCenter).getDistance() in outer * .80f..outer + 5.dp.toPx()
                    val local = point - fieldOrigin
                    hueGesture || if (triangleMode) triangle.distanceTo(local.x, local.y) <= 4.dp.toPx()
                        else local.x in 0f..fieldWidth && local.y in 0f..fieldHeight
                }, { base, point ->
                    if (hueGesture) {
                        val relative = point - ringCenter
                        val angle = atan2(relative.y, relative.x) * 180f / PI.toFloat()
                        base.withHsv(hue = ((angle - 210f) % 360f + 360f) % 360f)
                    } else {
                        val local = point - fieldOrigin
                        if (triangleMode) {
                            val weights = triangle.weights(local.x, local.y)
                            base.withHsv(saturation = if (weights.value > 0f) weights.saturation else base.saturation, value = weights.value)
                        } else base.withHsv(saturation = local.x / fieldWidth, value = 1f - local.y / fieldHeight)
                    }
                }, { updateLatest(it) }, { publishLatest(it) })
            }
            ColorWheelLayout((maximumHeight - 42.dp).coerceAtLeast(132.dp), triangleMode, wheelInput) {
                    Canvas(Modifier.semantics { contentDescription = ringLabel }) {
                        val outer = size.minDimension / 2f - 3.dp.toPx()
                        val thickness = outer * .17f
                        rotate(210f) { drawCircle(spectrum, outer - thickness / 2f, style = Stroke(thickness)) }
                        drawCircle(Color(0xff222222), outer, style = Stroke(.75.dp.toPx()))
                        drawCircle(Color(0xff222222), outer - thickness, style = Stroke(.75.dp.toPx()))
                        rotate(draft.hue + 210f) {
                            val marker = Offset(center.x + outer - thickness - 1.dp.toPx(), center.y - 3.dp.toPx())
                            drawRect(Color.Black, marker, Size(thickness + 2.dp.toPx(), 6.dp.toPx()))
                            drawRect(Color.White, marker + Offset(1.dp.toPx(), 1.dp.toPx()), Size(thickness, 4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    Canvas(Modifier.semantics { contentDescription = fieldLabel }) {
                        val marker: Offset
                        if (triangleMode) {
                            val points = listOf(Offset.Zero, Offset(size.width, size.height / 2f), Offset(0f, size.height))
                            drawContext.canvas.drawVertices(Vertices(VertexMode.Triangles, points, textureCoordinates = points, colors = listOf(Color.White, hueColor, Color.Black), indices = listOf(0, 1, 2)), BlendMode.Modulate, vertexPaint)
                            val outline = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }
                            drawPath(outline, Color(0xff222222), style = Stroke(.8.dp.toPx()))
                            val p = ColorTriangle(size.width, size.height).position(draft.saturation, draft.value)
                            marker = Offset(p.x, p.y)
                        } else {
                            drawRect(saturation); drawRect(darkness)
                            drawRect(Color(0xff222222), style = Stroke(.8.dp.toPx()))
                            marker = Offset(draft.saturation * size.width, (1f - draft.value) * size.height)
                        }
                        drawCircle(Color.Black, 5.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
                        drawCircle(Color.White, 4.dp.toPx(), marker, style = Stroke(1.dp.toPx()))
                    }
            }
            Row(Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val pair = colorPair
                Box(Modifier.size(44.dp, 32.dp).pointerInput(pair) {
                    if (pair != null) detectTapGestures { point ->
                        // Resolve the overlapping swatches once, without two expanded touch targets.
                        pair.select(!(point.x <= 23.dp.toPx() && point.y <= 23.dp.toPx()))
                    }
                }) {
                    if (pair != null) {
                        val primary = if (!pair.secondarySelected) draft.color else pair.foreground
                        val secondary = if (pair.secondarySelected) draft.color else pair.background
                        Box(Modifier.align(Alignment.BottomEnd).size(23.dp).background(Color(secondary), RoundedCornerShape(2.dp))
                            .border(if (pair.secondarySelected) 2.dp else 1.dp, if (pair.secondarySelected) Ink else MutedInk, RoundedCornerShape(2.dp))
                            .semantics { contentDescription = tr("Edit secondary color", "编辑副色"); onClick { pair.select(true); true } })
                        Box(Modifier.align(Alignment.TopStart).size(23.dp).background(Color(primary), RoundedCornerShape(2.dp))
                            .border(if (!pair.secondarySelected) 2.dp else 1.dp, if (!pair.secondarySelected) Ink else MutedInk, RoundedCornerShape(2.dp))
                            .semantics { contentDescription = tr("Edit primary color", "编辑主色"); onClick { pair.select(false); true } })
                    } else Box(Modifier.align(Alignment.Center).size(24.dp).background(Color(draft.color)).border(1.dp, MutedInk))
                }
                Row(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = tr("Color channel values", "颜色通道数值") }
                    .combinedClickable(onClick = { rgbValues = !rgbValues; preferences.edit { putBoolean("wheelRgbValues", rgbValues) } }, onLongClick = { editStart = draft }),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    val values = if (rgbValues) listOf("R" to AndroidColor.red(draft.color).toString(), "G" to AndroidColor.green(draft.color).toString(), "B" to AndroidColor.blue(draft.color).toString())
                        else if (triangleMode) listOf("H" to "${draft.hue.roundToInt()}°", "L" to "${(draft.lightness * 100).roundToInt()}", "S" to "${(draft.hlsSaturation * 100).roundToInt()}")
                        else listOf("H" to "${draft.hue.roundToInt()}°", "S" to "${(draft.saturation * 100).roundToInt()}", "V" to "${(draft.value * 100).roundToInt()}")
                    values.forEach { (label, value) -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = MutedInk, fontSize = 10.sp); Spacer(Modifier.width(4.dp)); Text(value, color = Ink, fontSize = 11.sp)
                    } }
                }
                IconButton(onClick = { editStart = draft }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Tune, tr("Edit color values", "编辑颜色数值"), Modifier.size(17.dp), tint = MutedInk)
                }
                IconButton(onClick = { triangleMode = !triangleMode; preferences.edit { putBoolean("wheelHlsTriangle", triangleMode) } }, modifier = Modifier.size(28.dp)) {
                    Icon(if (triangleMode) Icons.Outlined.CropSquare else Icons.Outlined.ChangeHistory,
                        if (triangleMode) tr("Use HSV square", "切换 HSV 方形") else tr("Use HLS triangle", "切换 HLS 三角形"), Modifier.size(18.dp), tint = MutedInk)
                }
                if (onResizeWidth != null) Box(Modifier.size(20.dp, 30.dp).semantics { contentDescription = tr("Resize color picker", "调整色环大小") }
                    .pointerInput(onResizeWidth) { detectDragGestures { change, amount -> change.consume(); onResizeWidth(amount.x / density.density) } }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.DragIndicator, null, Modifier.size(14.dp), tint = MutedInk)
                }
            }
        }
    }
    editStart?.let { original ->
        fun cancel() {
            draft = original
            draftChange(original)
            ColorPickerInteractionTrace.previewWrite()
            preview(original.color)
            editStart = null
        }
        AlertDialog(onDismissRequest = ::cancel, title = { Text(tr("Color values", "颜色数值")) }, text = {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rgbValues) {
                    listOf("R" to 16, "G" to 8, "B" to 0).forEach { (label, shift) ->
                        ColorValueInput(label, (draft.color ushr shift and 255).toFloat(), 255f) { value ->
                            update(draft.withColor((draft.color and (255 shl shift).inv()) or (value.roundToInt() shl shift)))
                        }
                    }
                } else {
                    ColorValueInput("H", draft.hue, 360f) { update(draft.withHsv(hue = it)) }
                    if (triangleMode) {
                        ColorValueInput("L", draft.lightness * 100, 100f) { update(draft.withHls(lightness = it / 100)) }
                        ColorValueInput("S", draft.hlsSaturation * 100, 100f) { update(draft.withHls(saturation = it / 100)) }
                    } else {
                        ColorValueInput("S", draft.saturation * 100, 100f) { update(draft.withHsv(saturation = it / 100)) }
                        ColorValueInput("V", draft.value * 100, 100f) { update(draft.withHsv(value = it / 100)) }
                    }
                }
            }
        }, confirmButton = {
            ColorDialogButton(tr("Done", "完成")) { publish(draft); editStart = null }
        }, dismissButton = { ColorDialogButton(tr("Cancel", "取消"), ::cancel) })
    }
}

/** Release the dialog's IME owner before disposing it, so the palette does not inherit its resize. */
@Composable
private fun ColorDialogButton(label: String, action: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    TextButton(onClick = { focus.clearFocus(force = true); keyboard?.hide(); action() }) { Text(label) }
}

@Composable
private fun ColorValueInput(label: String, value: Float, maximum: Float, onValue: (Float) -> Unit) {
    var entry by remember { mutableStateOf(value.roundToInt().toString()) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(value) { if (entry.toFloatOrNull()?.let { abs(it - value) > .005f } != false) entry = value.roundToInt().toString() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(entry, { text -> entry = text; text.toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..maximum }?.let(onValue) },
            Modifier.width(88.dp).semantics { contentDescription = "Color value $label" }, label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
        Spacer(Modifier.width(12.dp))
        BrushParameterSlider(value, { entry = it.roundToInt().toString(); onValue(it) }, Modifier.weight(1f), valueRange = 0f..maximum)
    }
}

/** Explicit intrinsic sizes let this real palette live in Material popup or dock hosts. */
@Composable
private fun ColorWheelLayout(preferredSize: Dp, triangle: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val preferred = with(LocalDensity.current) { preferredSize.roundToPx() }
    val policy = remember(preferred, triangle) { object : MeasurePolicy {
        override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
            val width = constraints.constrainWidth(preferred)
            val side = min(width, preferred).coerceAtLeast(1)
            val ring = measurables[0].measure(Constraints.fixed(side, side))
            val radius = side * .392f
            val fieldWidth = (radius * if (triangle) 1.5f else 1.36f).roundToInt().coerceAtLeast(1)
            val fieldHeight = if (triangle) (radius * sqrt(3f)).roundToInt().coerceAtLeast(1) else fieldWidth
            val field = measurables[1].measure(Constraints.fixed(fieldWidth, fieldHeight))
            return layout(width, side) {
                ring.place((width - side) / 2, 0)
                field.place((width - fieldWidth) / 2 + if (triangle) (radius * .25f).roundToInt() else 0, (side - fieldHeight) / 2)
            }
        }
        override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = min(preferred, height)
        override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = min(preferred, height)
        override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = min(preferred, width)
        override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = min(preferred, width)
    } }
    Layout(content, modifier.fillMaxWidth(), measurePolicy = policy)
}

/** A cancelled palette gesture restores its transient preview without a workspace write. */
private suspend fun PointerInputScope.pickerGesture(
    current: () -> ColorPickerDraft,
    accepts: (Offset) -> Boolean,
    at: (ColorPickerDraft, Offset) -> ColorPickerDraft,
    preview: (ColorPickerDraft) -> Unit,
    commit: (ColorPickerDraft) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!accepts(down.position)) return@awaitEachGesture
    val original = current()
    var latest = original
    var committed = false
    try {
        fun update(point: Offset) { ColorPickerInteractionTrace.pointerSample(); latest = at(original, point); preview(latest) }
        down.consume(); update(down.position)
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } > 1) break
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) break
            if (!change.pressed) {
                update(change.position); commit(latest); committed = true; break
            }
            change.consume(); update(change.position)
        }
    } finally { if (!committed) preview(original) }
}
