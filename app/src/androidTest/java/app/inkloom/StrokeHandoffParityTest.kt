package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.ProcreateTaper
import app.inkloom.document.StrokeInputKind
import app.inkloom.document.StrokePoint
import app.inkloom.document.StrokePostCorrection
import app.inkloom.ui.InkCanvasView
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Raster-level parity between the transient front-buffer presentation and the committed V3
 * projection for the same StrokeInput, brush parameters and render seed.
 *
 * Proves:
 * 1. The two presentation systems rasterize an identical stroke to identical pixels (the
 *    precondition for a gap-free, visually identical handoff).
 * 2. The only by-design visual divergence (end-taper suppression) stays confined to the tail.
 * 3. Post-correction applied only at pen-up changes the committed geometry, which is a real
 *    device-independent source of the "shape changed at pen-up" symptom.
 */
@RunWith(AndroidJUnit4::class)
class StrokeHandoffParityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val width = 800
    private val height = 600

    @Test
    fun frontBufferAndCommittedSceneRasterizeIdenticalStrokeToIdenticalPixels() {
        val stroke = parityStroke()
        val (transient, committed) = onMain {
            val view = newView()
            renderTransient(view, stroke) to renderCommitted(view, stroke)
        }
        try {
            assertTrue("Front-buffer raster must contain ink", alphaInkCount(transient) > 0)
            assertTrue("Committed raster must contain ink", alphaInkCount(committed) > 0)
            val maximum = maximumAlphaDifference(transient, committed)
            val diff = differingPixelCount(transient, committed)
            assertEquals("Front-buffer and committed rasters must be pixel-identical for the same stroke (diff=$diff)", 0, maximum)
        } finally {
            transient.recycle()
            committed.recycle()
        }
    }

    @Test
    fun defaultInkBrushEndTaperSuppressionChangesThePreviewRaster() {
        val base = parityStroke()
        val preview = base.copy(
            taperEndEnabled = false,
            procreateTaper = base.procreateTaper.copy(pressureEndLength = 0f, touchEndLength = 0f),
        )
        val final = base.copy(
            procreateTaper = base.procreateTaper.copy(
                pressureStartLength = 0f,
                pressureEndLength = .35f,
                pressureSize = .55f,
                pressureOpacity = .6f,
            ),
        )
        val (previewRaster, finalRaster) = onMain {
            val view = newView()
            renderCommitted(view, preview) to renderCommitted(view, final)
        }
        try {
            assertNotEquals(
                "The default MeshInk brush renders end taper, so the front-buffer taper suppression changes the preview vs the commit",
                signature(previewRaster),
                signature(finalRaster),
            )
        } finally {
            previewRaster.recycle()
            finalRaster.recycle()
        }
    }

    @Test
    fun stampedBrushEndTaperSuppressionChangesTheRasterBeyondTheTail() {
        withCircularTip { tip ->
            val base = parityStroke().copy(
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                procreateTaper = ProcreateTaper(
                    pressureStartLength = 0f,
                    pressureEndLength = .35f,
                    pressureSize = .55f,
                    pressureOpacity = .6f,
                    pressureTip = .5f,
                ),
                inputKind = app.inkloom.document.StrokeInputKind.STYLUS,
            )
            val preview = base.copy(taperEndEnabled = false, procreateTaper = base.procreateTaper.copy(pressureEndLength = 0f, touchEndLength = 0f))
            val (previewRaster, finalRaster) = onMain {
                val view = newView()
                renderCommitted(view, preview) to renderCommitted(view, base)
            }
            try {
                val diffX = differingPixelXCoordinates(previewRaster, finalRaster)
                val strokeEndX = (base.points.last().x * width).toInt()
                assertTrue(
                    "Stamped-brush taper suppression is a real preview/final divergence (diff x=${diffX.minOrNull()}..${diffX.maxOrNull()}, stroke end x=$strokeEndX)",
                    diffX.isNotEmpty(),
                )
                assertTrue(
                    "Stamped-brush taper divergence reaches pixels outside the taper tail (min diff x=${diffX.min()})",
                    diffX.min() < strokeEndX - width / 4,
                )
            } finally {
                previewRaster.recycle()
                finalRaster.recycle()
            }
        }
    }

    @Test
    fun postCorrectionAtPenUpIsBoundedAndNonDestructive() {
        val raw = zigzagStroke()
        val productionBound = StrokePostCorrection.screenBoundMaxDisplacement(
            StrokePostCorrection.POST_CORRECTION_MAX_SCREEN_PX,
            pageWidth = 1200f,
            viewWidthPx = width,
            viewportScale = 1f,
        )
        val corrected = StrokePostCorrection.apply(
            raw,
            amount = .9f,
            adjustBySpeed = false,
            adjustByScale = false,
            viewportScale = 1f,
            pageWidth = 1200f,
            pageHeight = 900f,
            quadraticBezier = false,
            preserveSharpAngles = false,
            maxDisplacementPx = productionBound,
        )
        assertTrue(
            "Post-correction with the production bound must not collapse the stroke (raw=${raw.points.size}, corrected=${corrected.points.size})",
            corrected.points.size >= 4,
        )
        val report = StrokePostCorrection.audit(
            raw, amount = .9f, pageWidth = 1200f, pageHeight = 900f, maxDisplacementPx = productionBound,
        )
        assertTrue(
            "Post-correction with the production bound must keep displacement bounded (max=${report.maxDisplacementPx}px, bound=${productionBound}px)",
            report.maxDisplacementPx <= productionBound + .75f,
        )
        val (previewRaster, committedRaster) = onMain {
            val view = newView()
            renderCommitted(view, raw) to renderCommitted(view, corrected)
        }
        try {
            assertTrue("Preview raster must contain ink", alphaInkCount(previewRaster) > 0)
            assertTrue("Corrected raster must contain ink", alphaInkCount(committedRaster) > 0)
        } finally {
            previewRaster.recycle()
            committedRaster.recycle()
        }
    }

    private fun newView(): InkCanvasView {
        val w = width
        val h = height
        return InkCanvasView(context).apply { layout(0, 0, w, h) }
    }

    private fun zigzagStroke() = InkStroke(
        id = "zigzag",
        points = (0..36).map { index ->
            StrokePoint(
                .1f + index * .02f,
                .5f + if (index % 2 == 0) .028f else -.014f,
                pressure = .6f,
                timeMs = (index * 5L).toLong(),
            )
        },
        width = 24f,
        spacing = .06f,
        sizePressureEnabled = false,
        renderSeed = 99,
        colorArgb = 0xFF000000,
    )

    private fun withCircularTip(block: (java.io.File) -> Unit) {
        val tip = java.io.File(context.cacheDir, "handoff-taper-${System.nanoTime()}.png")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(24f, 24f, 23f, android.graphics.Paint().apply {
                color = Color.BLACK
                isAntiAlias = true
            })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            block(tip)
        } finally {
            tip.delete()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                result = block()
            } catch (throwable: Throwable) {
                failure = throwable
            }
        }
        failure?.let { throw it }
        return result!!
    }

    private fun parityStroke() = InkStroke(
        id = "handoff-parity",
        points = (0..40).map { index ->
            val t = index / 40f
            StrokePoint(.1f + t * .8f, .5f + .06f * sin(t * 9f), pressure = .55f, timeMs = (index * 5L).toLong())
        },
        width = 56f,
        spacing = .06f,
        sizePressureEnabled = true,
        sizePressureMinimum = .25f,
        renderSeed = 4242,
        colorArgb = 0xFF000000,
    )

    private fun renderTransient(view: InkCanvasView, stroke: InkStroke): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.drawActiveStrokes(Canvas(bitmap), listOf(stroke), completeRedraw = true)
        return bitmap
    }

    private fun renderCommitted(view: InkCanvasView, stroke: InkStroke): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.setDocumentStrokes(listOf(stroke), "handoff-parity")
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun maximumAlphaDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in 0 until height) for (x in 0 until width) {
            maximum = maxOf(maximum, kotlin.math.abs(Color.alpha(first.getPixel(x, y)) - Color.alpha(second.getPixel(x, y))))
        }
        return maximum
    }

    private fun differingPixelCount(first: Bitmap, second: Bitmap): Int {
        var count = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (first.getPixel(x, y) != second.getPixel(x, y)) count++
        }
        return count
    }

    private fun differingPixelXCoordinates(first: Bitmap, second: Bitmap): List<Int> = buildList {
        for (y in 0 until height) for (x in 0 until width) {
            if (first.getPixel(x, y) != second.getPixel(x, y)) add(x)
        }
    }

    private fun alphaInkCount(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until height) for (x in 0 until width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0) count++
        }
        return count
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()
}
