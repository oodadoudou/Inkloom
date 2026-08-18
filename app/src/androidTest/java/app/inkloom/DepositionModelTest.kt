package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audits Opacity / Flow / Coverage separation in the deposition model per the Track 3 spec.
 *
 * A stamped cluster of overlapping dabs at a fixed position is rendered as ONE stroke; center
 * alpha measures how the deposit accumulates. Expected contract:
 *  - Opacity caps the whole stroke's final strength;
 *  - Flow controls per-dab deposit, so overlapping dabs build up to the opacity ceiling;
 *  - the (opacity=.25, flow=1) and (opacity=1, flow=.25) combos must produce measurably different
 *    accumulation, never aliases of one alpha multiplier.
 */
@RunWith(AndroidJUnit4::class)
class DepositionModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val width = 400
    private val height = 400

    @Test
    fun opacityAndFlowProduceDistinctIntraStrokeAccumulation() {
        withCircularTip { tip ->
            val capped = clusterCenterAlpha(tip, opacity = .25f, flow = 1f)
            val building = clusterCenterAlpha(tip, opacity = 1f, flow = .25f)
            println("DEPOSIT cluster capped=$capped building=$building")
            // Overlapping dabs within one stroke: flow must build beyond a single pass, while a
            // hard opacity cap must hold the stroke near its ceiling regardless of overlap.
            assertTrue(
                "Flow .25 over overlapping dabs must build above the .25 single-pass level (got $building)",
                building > 80,
            )
            assertTrue(
                "Opacity .25 must hold the stroke near the .25 ceiling even with overlap (got $capped)",
                capped <= 90,
            )
            assertTrue(
                "Opacity and flow must not be aliases (capped=$capped building=$building)",
                kotlin.math.abs(capped - building) > 15,
            )
        }
    }

    @Test
    fun stackingSeparateStrokesBuildsViaNormalCompositingRegardlessOfOpacity() {
        // Separate stroke passes over the same area compose normally (1-(1-a)^N) for BOTH opacity
        // and flow variants: opacity is a per-stroke cap, not a document ceiling. This documents
        // that the per-stroke deposition is distinct from cross-stroke compositing.
        withCircularTip { tip ->
            val single = renderCluster(tip, opacity = .25f, flow = 1f)
            val w = width
            val h = height
            val stacked = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                repeat(8) { canvas.drawBitmap(single, 0f, 0f, null) }
            }
            val alpha = Color.alpha(stacked.getPixel(w / 2, h / 2))
            println("DEPOSIT stacked-capped-8=$alpha")
            assertTrue("8 stacked .25-alpha strokes must build well past the cap (got $alpha)", alpha > 150)
            stacked.recycle()
            single.recycle()
        }
    }

    private fun clusterCenterAlpha(tip: File, opacity: Float, flow: Float): Int {
        val w = width
        val h = height
        val bitmap = renderCluster(tip, opacity, flow)
        val alpha = Color.alpha(bitmap.getPixel(w / 2, h / 2))
        bitmap.recycle()
        return alpha
    }

    private fun renderCluster(tip: File, opacity: Float, flow: Float): Bitmap {
        val w = width
        val h = height
        val stroke = InkStroke(
            id = "deposit-cluster",
            points = listOf(
                StrokePoint(.49f, .5f, pressure = .8f, timeMs = 0L),
                StrokePoint(.5f, .5f, pressure = .8f, timeMs = 2L),
                StrokePoint(.51f, .5f, pressure = .8f, timeMs = 4L),
            ),
            width = 60f,
            opacity = opacity,
            flow = flow,
            sizePressureEnabled = false,
            tipAssetPath = tip.absolutePath,
            tipAssetPaths = listOf(tip.absolutePath),
            colorArgb = 0xFF000000,
        )
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply { layout(0, 0, w, h) }
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.setDocumentStrokes(listOf(stroke), "deposition-cluster")
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun withCircularTip(block: (File) -> Unit) {
        val tip = File(context.cacheDir, "deposition-tip-${System.nanoTime()}.png")
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(24f, 24f, 23f, android.graphics.Paint().apply { color = Color.BLACK; isAntiAlias = true })
            tip.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
        try {
            block(tip)
        } finally {
            tip.delete()
        }
    }
}
