package app.inkloom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.inkloom.settings.RasterFilterPreset
import kotlin.math.roundToInt

/** Filter names by preset kind index, which saved presets store. */
internal fun rasterFilterNames(tr: (String, String) -> String) = listOf(
    tr("Gaussian blur", "高斯模糊"), tr("Unsharp mask", "USM 锐化"), tr("Denoise", "降噪"), tr("Remove stray pixels", "清理杂点"),
    tr("Correct line width", "修正线宽"), tr("Wave distortion", "波形变形"), tr("Motion blur", "动感模糊"), tr("Radial blur", "径向模糊"),
    tr("Lens blur", "镜头模糊"), tr("Mosaic", "马赛克"), tr("Noise", "噪点"), tr("Chromatic aberration", "色差"),
    tr("Convert to drawing color", "换为绘画色"), tr("Brightness to opacity", "亮度转透明度"),
    tr("Sharpen", "锐化"), tr("Sharpen more", "强锐化"),
    tr("Blur", "模糊"), tr("Strong blur", "强模糊"), tr("Smoothing", "平滑"), tr("Perlin noise", "柏林噪声"),
    tr("Pinch", "捏合"), tr("Curved surface", "曲面投影"), tr("Wave", "波形"), tr("Twirl", "漩涡"),
    tr("Convert to panorama", "环视转换"), tr("Geometric distortion", "畸变补偿"), tr("Polar coordinates", "极坐标转换"),
    tr("ZigZag", "锯齿变形"), tr("Fish-eye lens", "鱼眼镜头"),
    tr("Brightness / Contrast", "亮度／对比度"), tr("Hue / Saturation / Luminosity", "色相／饱和度／明度"), tr("Levels", "色阶"),
    tr("Curves", "曲线"), tr("Color Balance", "色彩平衡"), tr("Invert", "反转"), tr("Posterize", "色调分离"),
    tr("Threshold", "二值化"), tr("Gradient Map", "渐变映射"))

/** CSP's Filter menu groups, each listing preset kind indices. Headers never repeat a filter name. */
internal fun rasterFilterGroups(tr: (String, String) -> String) = listOf(
    tr("BLUR", "模糊类") to listOf(16, 17, 18, 0, 6, 7, 8),
    tr("SHARPEN", "锐化类") to listOf(14, 15, 1),
    tr("NOISE & RENDER", "噪点与绘制") to listOf(10, 19, 2, 9, 11),
    tr("DISTORT", "变形类") to listOf(20, 21, 22, 5, 23, 24, 25, 26, 27, 28),
    tr("LINE ART", "线稿") to listOf(3, 4),
    tr("TONAL CORRECTION", "色调补偿") to (RasterFilterPreset.FIRST_TONAL_KIND..RasterFilterPreset.LAST_KIND).toList(),
    tr("COLOR", "颜色") to listOf(12, 13))

@Composable
internal fun RasterFilterKindMenuItems(tr: (String, String) -> String, onKind: (Int) -> Unit) {
    val names = rasterFilterNames(tr)
    rasterFilterGroups(tr).forEach { (group, kinds) ->
        Text(group, Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        kinds.forEach { kind -> StudioMenuItem(text = { Text(names[kind]) }, onClick = { onKind(kind) }) }
    }
}

/** Settings for the fixed blurs, Perlin noise and the Distort family (kinds 16 and later), as CSP property rows. */
@Composable
internal fun RasterFilterExtraControls(state: RasterFilterPreset, onState: (RasterFilterPreset) -> Unit, tr: (String, String) -> String) {
    val note = MaterialTheme.typography.bodySmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun center() {
        StudioSliderRow(tr("Center X", "中心 X"), state.centerX * 100f, 0f..100f, { onState(state.copy(centerX = it / 100f)) }, unit = "%")
        StudioSliderRow(tr("Center Y", "中心 Y"), state.centerY * 100f, 0f..100f, { onState(state.copy(centerY = it / 100f)) }, unit = "%")
    }
    @Composable fun area() {
        StudioSegmented(listOf(tr("Entire selection", "整个选区"), tr("Specify size", "指定范围")), if (state.radius == 0f) 0 else 1,
            { onState(state.copy(radius = if (it == 0) 0f else .5f)) }, label = tr("Area", "范围"))
        if (state.radius > 0f) {
            StudioSliderRow(tr("Radius", "半径"), state.radius * 100f, 5f..100f, { onState(state.copy(radius = it / 100f)) }, unit = "%")
            StudioSliderRow(tr("Shape", "形状"), state.shape, -100f..100f, { onState(state.copy(shape = it)) })
        }
    }
    @Composable fun distortion(label: String = tr("Strength", "强度"), range: ClosedFloatingPointRange<Float> = -100f..100f) =
        StudioSliderRow(label, state.distortion, range, { onState(state.copy(distortion = it.roundToInt().toFloat())) })
    @Composable fun waves() = StudioSliderRow(tr("Number of waves", "波数"), state.count.toFloat(), 1f..20f, { onState(state.copy(count = it.roundToInt())) })
    Column {
        when (state.kind) {
            16, 17, 18 -> Text(tr("Fixed strength, as in Clip Studio Paint.", "固定强度，与 Clip Studio Paint 相同。"), style = note, color = muted)
            19 -> {
                StudioSliderRow(tr("Scale", "缩放"), state.noiseScale, 2f..500f, { onState(state.copy(noiseScale = it)) }, unit = "px")
                StudioSliderRow(tr("Amplitude", "振幅"), state.noiseAmplitude * 100f, 0f..100f, { onState(state.copy(noiseAmplitude = it / 100f)) }, unit = "%")
                StudioSliderRow(tr("Attenuation", "衰减"), state.attenuation * 100f, 0f..100f, { onState(state.copy(attenuation = it / 100f)) }, unit = "%")
                StudioSliderRow(tr("Octaves", "八度"), state.octaves.toFloat(), 1f..8f, { onState(state.copy(octaves = it.roundToInt())) })
                StudioButton(tr("New noise", "重新生成噪点"), { onState(state.copy(seed = state.seed + 1)) })
                Text(tr("Replaces the selected pixels with grey noise.", "以灰度噪声替换所选像素。"), Modifier.padding(top = 4.dp), style = note, color = muted)
            }
            20 -> { distortion(); area(); center() }
            21 -> {
                StudioSegmented(listOf(tr("Cylinder", "圆柱"), tr("Sphere", "球面")), state.mode.coerceAtMost(1), { onState(state.copy(mode = it)) }, label = tr("Surface", "曲面"))
                distortion(); area(); center()
            }
            22 -> {
                StudioSegmented(listOf(tr("Sine", "正弦"), tr("Triangle", "三角"), tr("Rectangular", "矩形")), state.mode, { onState(state.copy(mode = it)) }, label = tr("Shape", "形状"))
                waves()
                StudioSliderRow(tr("Wavelength min.", "最小波长"), state.wavelengthMin, 2f..1000f, { onState(state.copy(wavelengthMin = it, wavelengthMax = maxOf(it, state.wavelengthMax))) }, unit = "px")
                StudioSliderRow(tr("Wavelength max.", "最大波长"), state.wavelengthMax, 2f..1000f, { onState(state.copy(wavelengthMax = it, wavelengthMin = minOf(it, state.wavelengthMin))) }, unit = "px")
                StudioSliderRow(tr("Amplitude min.", "最小振幅"), state.amplitudeMin, 0f..500f, { onState(state.copy(amplitudeMin = it, amplitudeMax = maxOf(it, state.amplitudeMax))) }, unit = "px")
                StudioSliderRow(tr("Amplitude max.", "最大振幅"), state.amplitudeMax, 0f..500f, { onState(state.copy(amplitudeMax = it, amplitudeMin = minOf(it, state.amplitudeMin))) }, unit = "px")
                StudioSliderRow(tr("Horizontal ratio", "水平比例"), state.horizontalRatio, 0f..100f, { onState(state.copy(horizontalRatio = it)) }, unit = "%")
                StudioSliderRow(tr("Vertical ratio", "垂直比例"), state.verticalRatio, 0f..100f, { onState(state.copy(verticalRatio = it)) }, unit = "%")
                StudioSegmented(listOf(tr("Repeat edge", "重复边缘"), tr("Wrap around", "折回")), if (state.wrap) 1 else 0, { onState(state.copy(wrap = it == 1)) }, label = tr("Undefined areas", "未定义区域"))
                StudioButton(tr("Regenerate", "重新生成"), { onState(state.copy(seed = state.seed + 1)) })
            }
            23 -> {
                StudioSliderRow(tr("Twist", "扭转"), state.distortion * 3.6f, -360f..360f, { onState(state.copy(distortion = (it / 3.6f).roundToInt().toFloat().coerceIn(-100f, 100f))) }, unit = "°")
                StudioSliderRow(tr("Tension", "张力"), state.tension, 0f..100f, { onState(state.copy(tension = it)) })
                area(); center()
            }
            24 -> { StudioSliderRow(tr("Angle", "角度"), state.angle, -180f..180f, { onState(state.copy(angle = it)) }, unit = "°"); center() }
            25 -> {
                distortion(tr("Distortion", "畸变"))
                StudioSliderRow(tr("Scale ratio", "缩放比例"), state.scale, 10f..400f, { onState(state.copy(scale = it)) }, unit = "%")
                center()
            }
            26 -> StudioSegmented(listOf(tr("Rect. to polar", "直角→极坐标"), tr("Polar to rect.", "极坐标→直角"), tr("Sphere", "球面反射")),
                state.mode, { onState(state.copy(mode = it)) }, label = tr("Method", "方式"))
            27 -> {
                StudioSliderRow(tr("Angle", "角度"), state.angle, -180f..180f, { onState(state.copy(angle = it)) }, unit = "°")
                StudioSliderRow(tr("Wave height", "波高"), state.amplitudeMax, 0f..500f, { onState(state.copy(amplitudeMax = it, amplitudeMin = minOf(it, state.amplitudeMin))) }, unit = "px")
                waves()
            }
            28 -> {
                distortion(tr("Distortion", "畸变"), 0f..100f); area(); center()
                Text(tr("Outside the lens becomes transparent.", "镜头以外的区域变为透明。"), Modifier.padding(top = 4.dp), style = note, color = muted)
            }
        }
    }
}
