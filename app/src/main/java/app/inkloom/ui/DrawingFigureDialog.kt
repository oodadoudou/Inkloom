package app.inkloom.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.inkloom.document.DrawingFigure
import app.inkloom.document.DrawingFigureRenderer
import app.inkloom.document.FigureJoint
import app.inkloom.document.FigurePose
import app.inkloom.document.FigureRig
import app.inkloom.document.FigureStyle
import app.inkloom.document.JointTurn
import app.inkloom.settings.UiLanguage
import app.inkloom.settings.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * LM-36's 3D drawing figure: pose the mannequin by tapping a joint on the preview and turning it,
 * drag elsewhere to orbit the camera, and choose poses, lens, framing, proportions and style.
 */
@Composable
internal fun DrawingFigureDialog(initial: DrawingFigure, editing: Boolean, pageAspect: Float, language: UiLanguage, drawingColor: Int, busy: Boolean,
                                 onDismiss: () -> Unit, onApply: (DrawingFigure, Boolean) -> Unit) {
    fun tr(en: String, zh: String) = language.text(en, zh)
    var figure by remember { mutableStateOf(initial) }
    var joint by remember { mutableStateOf<FigureJoint?>(FigureJoint.LEFT_UPPER_ARM) }
    var posesOpen by remember { mutableStateOf(false) }
    var ruler by remember { mutableStateOf(false) }
    val previewWidth = 280; val previewHeight = (previewWidth * pageAspect).toInt().coerceIn(120, 600)
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(figure) {
        delay(40)
        val pixels = withContext(Dispatchers.Default) { DrawingFigureRenderer.render(figure, previewWidth, previewHeight) }
        preview = androidx.core.graphics.createBitmap(previewWidth, previewHeight).also { it.setPixels(pixels, 0, previewWidth, 0, 0, previewWidth, previewHeight) }
    }
    val current by rememberUpdatedState(figure)
    StudioDialog(onDismissRequest = { if (!busy) onDismiss() }, placement = StudioDialogPlacement.SIDE,
        title = { Text(if (editing) tr("Edit 3D figure", "编辑 3D 人偶") else tr("New 3D drawing figure", "新建 3D 素描人偶")) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().height(300.dp).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outline)
                .semantics { contentDescription = tr("3D figure preview", "3D 人偶预览") }) {
                Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val (scale, offset) = fit(size.width.toFloat(), size.height.toFloat(), previewWidth, previewHeight)
                        val camera = FigureRig.Camera(current, previewWidth.toFloat(), previewHeight.toFloat())
                        joint = FigureRig.bones(current).mapNotNull { bone -> camera.project(bone.end)?.let { p -> bone.joint to Offset(offset.x + p.first * scale, offset.y + p.second * scale) } }
                            .minByOrNull { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) }?.takeIf { (_, p) -> hypot(p.x - tap.x, p.y - tap.y) < 40f }?.first ?: joint
                    }
                }.pointerInput(Unit) {
                    detectDragGestures { _, drag -> figure = current.copy(yaw = (current.yaw - drag.x * .4f) % 360f, pitch = (current.pitch + drag.y * .3f).coerceIn(-80f, 80f)) }
                }) {
                    val image = preview ?: return@Canvas
                    val (scale, offset) = fit(size.width, size.height, image.width, image.height)
                    drawImage(image.asImageBitmap(), dstOffset = IntOffset(offset.x.toInt(), offset.y.toInt()),
                        dstSize = IntSize((image.width * scale).toInt(), (image.height * scale).toInt()))
                    val camera = FigureRig.Camera(figure, previewWidth.toFloat(), previewHeight.toFloat())
                    FigureRig.bones(figure).forEach { bone ->
                        val p = camera.project(bone.end) ?: return@forEach
                        val selected = bone.joint == joint
                        drawCircle(if (selected) Color(0xFF2F80ED) else Color(0x992F80ED), if (selected) 7f else 4.5f, Offset(offset.x + p.first * scale, offset.y + p.second * scale))
                    }
                }
            }
            StudioDropdownField(tr("Pose", "姿势"), posesOpen, { posesOpen = it }, Modifier.fillMaxWidth()) {
                FigurePose.entries.forEach { pose ->
                    StudioMenuItem(text = { Text(poseName(pose, language)) }, onClick = { figure = figure.copy(turns = pose.turns); posesOpen = false })
                }
            }
            joint?.let { selected ->
                StudioSection(jointName(selected, language))
                val turn = figure.turn(selected)
                StudioSliderRow(tr("Bend", "前后弯曲"), turn.x, -180f..180f, { figure = figure.withTurn(selected, turn.copy(x = it)) }, unit = "°", decimals = 0)
                StudioSliderRow(tr("Twist", "扭转"), turn.y, -180f..180f, { figure = figure.withTurn(selected, turn.copy(y = it)) }, unit = "°", decimals = 0)
                StudioSliderRow(tr("Raise sideways", "侧向抬起"), turn.z, -180f..180f, { figure = figure.withTurn(selected, turn.copy(z = it)) }, unit = "°", decimals = 0)
                StudioButton(tr("Reset joint", "重置关节"), { figure = figure.withTurn(selected, JointTurn()) })
            }
            StudioSection(tr("Camera", "镜头"))
            StudioSliderRow(tr("Lens", "视角"), figure.fov, 15f..100f, { figure = figure.copy(fov = it) }, unit = "°", decimals = 0)
            StudioSliderRow(tr("Distance", "距离"), figure.distance, 1.6f..10f, { figure = figure.copy(distance = it) })
            StudioSection(tr("On the page", "页面位置"))
            StudioSliderRow(tr("Size", "大小"), figure.height * 100f, 10f..150f, { figure = figure.copy(height = it / 100f) }, unit = "%", decimals = 0)
            StudioSliderRow(tr("Across", "水平位置"), figure.centerX * 100f, 0f..100f, { figure = figure.copy(centerX = it / 100f) }, unit = "%", decimals = 0)
            StudioSliderRow(tr("Down", "垂直位置"), figure.centerY * 100f, 0f..100f, { figure = figure.copy(centerY = it / 100f) }, unit = "%", decimals = 0)
            StudioSection(tr("Body", "体型"))
            StudioSliderRow(tr("Head size", "头部大小"), figure.headScale * 100f, 60f..160f, { figure = figure.copy(headScale = it / 100f) }, unit = "%", decimals = 0)
            StudioSliderRow(tr("Shoulder width", "肩宽"), figure.shoulderWidth * 100f, 60f..160f, { figure = figure.copy(shoulderWidth = it / 100f) }, unit = "%", decimals = 0)
            StudioSection(tr("Drawing", "绘制"))
            StudioSegmented(listOf(tr("Shaded", "明暗"), tr("Outline", "轮廓线"), tr("Silhouette", "剪影")), figure.style.ordinal, { figure = figure.copy(style = FigureStyle.entries[it]) })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioButton(tr("Use drawing colour", "使用描绘色"), {
                    figure = if (figure.style == FigureStyle.OUTLINE) figure.copy(lineArgb = drawingColor or 0xFF000000.toInt()) else figure.copy(colorArgb = drawingColor or 0xFF000000.toInt())
                })
            }
            StudioCheckRow(tr("Perspective ruler from the camera", "根据镜头创建透视尺规"), ruler, { ruler = it })
            if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton({ onApply(figure, ruler) }, enabled = !busy) { Text(tr("OK", "确定")) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(tr("Cancel", "取消")) } })
}

private fun fit(width: Float, height: Float, imageWidth: Int, imageHeight: Int): Pair<Float, Offset> {
    val scale = minOf(width / imageWidth, height / imageHeight)
    return scale to Offset((width - imageWidth * scale) / 2f, (height - imageHeight * scale) / 2f)
}

private fun poseName(pose: FigurePose, language: UiLanguage) = when (pose) {
    FigurePose.STANDING -> language.text("Standing", "站立")
    FigurePose.T_POSE -> language.text("T-pose", "T 字")
    FigurePose.WALKING -> language.text("Walking", "行走")
    FigurePose.RUNNING -> language.text("Running", "奔跑")
    FigurePose.SITTING -> language.text("Sitting", "坐姿")
    FigurePose.ARMS_UP -> language.text("Arms up", "举手")
    FigurePose.THINKING -> language.text("Thinking", "思考")
}

private fun jointName(joint: FigureJoint, language: UiLanguage): String {
    val side = when { joint.name.startsWith("LEFT_") -> language.text("Left ", "左"); joint.name.startsWith("RIGHT_") -> language.text("Right ", "右"); else -> "" }
    val part = when (joint.name.removePrefix("LEFT_").removePrefix("RIGHT_")) {
        "PELVIS" -> language.text("pelvis", "骨盆"); "SPINE" -> language.text("spine", "脊柱"); "CHEST" -> language.text("chest", "胸部")
        "NECK" -> language.text("neck", "颈部"); "HEAD" -> language.text("head", "头部"); "SHOULDER" -> language.text("shoulder", "肩")
        "UPPER_ARM" -> language.text("upper arm", "上臂"); "FOREARM" -> language.text("forearm", "前臂"); "HAND" -> language.text("hand", "手")
        "HIP" -> language.text("hip", "髋"); "THIGH" -> language.text("thigh", "大腿"); "SHIN" -> language.text("shin", "小腿"); else -> language.text("foot", "脚")
    }
    return (side + part).replaceFirstChar(Char::uppercase)
}
