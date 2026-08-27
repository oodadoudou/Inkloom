package app.inkloom.engine.v3.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import app.inkloom.document.CspBrightnessCorrection
import app.inkloom.document.CspPigmentMixingMode
import app.inkloom.document.InkStroke
import app.inkloom.document.ProcreateRendering
import app.inkloom.document.WetMixDynamics

/** One bounded native pigment-composite pass for an ordered wet-stroke increment. */
object RasterWetPaintMixer {
    fun composite(
        base: Bitmap,
        paintPatch: Bitmap,
        left: Int,
        top: Int,
        strokes: List<InkStroke>,
        contentWidth: Int = paintPatch.width,
        contentHeight: Int = paintPatch.height,
    ): Boolean {
        if (base.isRecycled || paintPatch.isRecycled || strokes.isEmpty()) return false
        if (contentWidth !in 1..paintPatch.width || contentHeight !in 1..paintPatch.height) return false
        val right = (left + contentWidth).coerceAtMost(base.width)
        val bottom = (top + contentHeight).coerceAtMost(base.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return false
        var amountTotal = 0.0
        var pointCount = 0
        strokes.forEach { stroke -> stroke.points.forEach { point ->
            amountTotal += if (stroke.procreateWetMix.isDisabled()) {
                WetMixDynamics.colorMix(stroke, point).toDouble()
            } else {
                val wet = stroke.procreateWetMix
                ((1f - WetMixDynamics.effectiveDilution(stroke, point)) *
                    (.25f + .75f * WetMixDynamics.reservoir(stroke, point)) *
                    (.4f + .6f * wet.attack.coerceIn(0f, 1f))).coerceIn(0f, 1f).toDouble()
            }
            pointCount++
        } }
        val amountOfPaint = (amountTotal / pointCount.coerceAtLeast(1)).takeIf(Double::isFinite)
            ?.toFloat()?.coerceIn(0f, 1f) ?: 1f
        val recursiveStrength = strokes.map { ProcreateRendering.wetMixStrength(it.procreateRenderingMode) }
            .average().takeIf(Double::isFinite)?.toFloat()?.coerceIn(0f, 1.35f) ?: 0f
        val representative = strokes.first()
        val hasLocalPigment = strokes.any { stroke -> stroke.points.any { it.pigmentArgb != null } }
        val freshRatio = if (hasLocalPigment) 1f else {
            (amountOfPaint / (1f + recursiveStrength * (1f - amountOfPaint))).coerceIn(0f, 1f)
        }
        val sourceStrength = 1f + recursiveStrength * .12f
        return runCatching {
            NativeRasterOps.compositePigment(
                base = base,
                fresh = paintPatch,
                left = left,
                top = top,
                width = width,
                height = height,
                freshRatio = freshRatio,
                sourceStrength = sourceStrength,
                localPigment = hasLocalPigment,
                perceptual = CspPigmentMixingMode.normalize(representative.cspPigmentMixingMode) == CspPigmentMixingMode.PERCEPTUAL,
                brightnessCorrection = (CspBrightnessCorrection.normalize(representative.cspBrightnessCorrection)
                    .takeIf { it > 0 } ?: CspBrightnessCorrection.NEUTRAL) - CspBrightnessCorrection.NEUTRAL.toFloat(),
            )
        }.getOrElse {
            Canvas(base).drawBitmap(
                paintPatch,
                android.graphics.Rect(0, 0, width, height),
                android.graphics.Rect(left, top, left + width, top + height),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                },
            )
            true
        }
    }
}
