package app.inkloom.engine.v2.render

import android.graphics.Bitmap
import android.graphics.Color
import app.inkloom.document.InkStroke
import app.inkloom.document.CspPigmentMixer
import app.inkloom.document.ProcreateRendering
import app.inkloom.document.WetMixDynamics

/** Pixel-level wet paint compositing. Existing layer pigment participates in the deposited color;
 * amount of paint controls fresh-vs-existing pigment and density of paint controls deposition. */
object RasterWetPaintMixer {
    fun composite(base: Bitmap, paintPatch: Bitmap, left: Int, top: Int, strokes: List<InkStroke>): Boolean {
        if (base.isRecycled || paintPatch.isRecycled || strokes.isEmpty()) return false
        val right = (left + paintPatch.width).coerceAtMost(base.width)
        val bottom = (top + paintPatch.height).coerceAtMost(base.height)
        val width = right - left; val height = bottom - top
        if (width <= 0 || height <= 0) return false
        var amountTotal = 0.0
        var pointCount = 0
        strokes.forEach { stroke -> stroke.points.forEach { point ->
            amountTotal += if (stroke.procreateWetMix.isDisabled()) {
                WetMixDynamics.colorMix(stroke, point).toDouble()
            } else {
                val wet = stroke.procreateWetMix
                val freshPigment = ((1f - WetMixDynamics.effectiveDilution(stroke, point)) *
                    (.25f + .75f * WetMixDynamics.reservoir(stroke, point)) * (.4f + .6f * wet.attack.coerceIn(0f, 1f))).coerceIn(0f, 1f)
                freshPigment.toDouble()
            }
            pointCount += 1
        } }
        val recursiveStrength = strokes.map { ProcreateRendering.wetMixStrength(it.procreateRenderingMode) }
            .average().takeIf(Double::isFinite)?.toFloat()?.coerceIn(0f, 1.35f) ?: 0f
        val amountOfPaint = (amountTotal / pointCount.coerceAtLeast(1)).takeIf(Double::isFinite)?.toFloat()?.coerceIn(0f, 1f) ?: 1f
        val representative = strokes.first()
        val hasLocalPigment = strokes.any { stroke -> stroke.points.any { it.pigmentArgb != null } }
        val basePixels = IntArray(width * height)
        val paintPixels = IntArray(width * height)
        base.getPixels(basePixels, 0, width, left, top, width, height)
        paintPatch.getPixels(paintPixels, 0, paintPatch.width, 0, 0, width, height)
        var changed = false
        for (index in basePixels.indices) {
            val paint = paintPixels[index]
            val patchAlpha = paint ushr 24
            if (patchAlpha == 0) continue
            val under = basePixels[index]
            val underAlpha = under ushr 24
            val freshRatio = if (underAlpha == 0) 1f else (amountOfPaint / (1f + recursiveStrength * (1f - amountOfPaint))).coerceIn(0f, 1f)
            val underRed = (under ushr 16) and 0xFF; val underGreen = (under ushr 8) and 0xFF; val underBlue = under and 0xFF
            val mixed = if (hasLocalPigment || underAlpha == 0) paint else CspPigmentMixer.mixArgb(
                under,
                paint,
                freshRatio,
                representative.cspPigmentMixingMode,
                representative.cspBrightnessCorrection,
            )
            val mixedRed = (mixed ushr 16) and 0xFF; val mixedGreen = (mixed ushr 8) and 0xFF; val mixedBlue = mixed and 0xFF
            // Density is already evaluated per point by EngineV2BrushEvaluator. Applying it again
            // here made low-density CSP brushes quadratically faint and diverged from replay.
            val sourceAlpha = (patchAlpha / 255f * (1f + recursiveStrength * .12f)).coerceIn(0f, 1f)
            val destinationAlpha = underAlpha / 255f
            val outputAlpha = sourceAlpha + destinationAlpha * (1f - sourceAlpha)
            if (outputAlpha <= .0001f) continue
            fun channel(source: Int, destination: Int): Int = ((source * sourceAlpha + destination * destinationAlpha * (1f - sourceAlpha)) / outputAlpha + .5f).toInt().coerceIn(0, 255)
            val output = ((outputAlpha * 255f + .5f).toInt().coerceIn(0, 255) shl 24) or
                (channel(mixedRed, underRed) shl 16) or (channel(mixedGreen, underGreen) shl 8) or channel(mixedBlue, underBlue)
            if (output != under) { basePixels[index] = output; changed = true }
        }
        if (changed) base.setPixels(basePixels, 0, width, left, top, width, height)
        return changed
    }
}
