package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateGrainRotationRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun movingGrainCanFollowStrokeAndMatchesDocumentExport() {
        val tip = File(context.cacheDir, "grain-rotation-tip-${System.nanoTime()}.png")
        val grain = File(context.cacheDir, "grain-rotation-texture-${System.nanoTime()}.png")
        writeCircularTip(tip)
        writeDirectionalGrain(grain)
        try {
            val base = InkStroke(
                points = listOf(
                    StrokePoint(.5f, .2f, timeMs = 1),
                    StrokePoint(.5f, .8f, timeMs = 2),
                ),
                width = 140f,
                tipAssetPath = tip.absolutePath,
                tipAssetPaths = listOf(tip.absolutePath),
                grainAssetPath = grain.absolutePath,
                grainDensity = 1f,
                grainScale = 100f,
                grainMovement = 0f,
                grainZoom = 0f,
                spacing = .12f,
                sizePressureEnabled = false,
                renderSeed = 727,
            )
            val fixed = renderExport(base)
            val followsStroke = base.copy(grainStrokeRotation = 1f)
            val rotated = renderExport(followsStroke)
            val live = renderLive(followsStroke, rotated.height)
            try {
                assertNotEquals("Following stroke direction did not rotate the grain", signature(fixed), signature(rotated))
                val maximumDifference = maximumRedDifference(rotated, live)
                assertTrue("Live/export grain rotation diverged: max red delta=$maximumDifference", maximumDifference <= 3)
            } finally {
                fixed.recycle(); rotated.recycle(); live.recycle()
            }
        } finally {
            tip.delete(); grain.delete()
        }
    }

    private fun writeCircularTip(file: File) {
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawCircle(24f, 24f, 23f, Paint().apply { color = Color.BLACK; isAntiAlias = true })
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private fun writeDirectionalGrain(file: File) {
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
            val pixels = IntArray(48 * 48) { index ->
                val x = index % 48
                if (x % 12 < 4) Color.BLACK else Color.WHITE
            }
            bitmap.setPixels(pixels, 0, 48, 0, 0, 48, 48)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
        400,
    )

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var result: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "grain-rotation-render")
                layout(0, 0, 400, height)
            }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in 0 until first.height) for (x in 0 until first.width) {
            maximum = maxOf(maximum, kotlin.math.abs(Color.red(first.getPixel(x, y)) - Color.red(second.getPixel(x, y))))
        }
        return maximum
    }
}
