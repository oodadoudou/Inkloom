package app.inkloom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.inkloom.document.DocumentAssetService
import app.inkloom.document.InkPage
import app.inkloom.document.InkStroke
import app.inkloom.document.PageBackground
import app.inkloom.document.ProcreateTaper
import app.inkloom.document.StrokeInputKind
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateTaperRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun pressureTaperChangesSizeAndOpacityAndMatchesLiveExport() {
        withCircularTip { tip ->
            val stroke = taperedStroke(tip).copy(
                procreateTaper = ProcreateTaper(
                    pressureStartLength = .3f,
                    pressureEndLength = .3f,
                    pressureSize = .8f,
                    pressureOpacity = .75f,
                    pressureTip = .55f,
                    pressureResponse = .4f,
                ),
                inputKind = StrokeInputKind.STYLUS,
            )
            val export = renderExport(stroke)
            val live = renderLive(stroke, export.height)
            try {
                val edgeX = (export.width * .1f).toInt()
                val centerX = export.width / 2
                assertTrue("Pressure taper did not narrow the stroke start", visibleHeight(export, edgeX) < visibleHeight(export, centerX))
                assertTrue("Pressure opacity taper did not fade the stroke start", averageRed(export, edgeX) > averageRed(export, centerX) + 25)
                val maximumDifference = maximumRedDifference(export, live)
                assertTrue("Live/export pressure taper diverged: max red delta=$maximumDifference", maximumDifference <= 3)
            } finally {
                export.recycle()
                live.recycle()
            }
        }
    }

    @Test
    fun touchAndStylusUseIndependentTaperPrograms() {
        withCircularTip { tip ->
            val taper = ProcreateTaper(
                pressureStartLength = .4f,
                pressureSize = .9f,
                pressureTip = .2f,
                touchStartLength = .4f,
                touchSize = .15f,
                touchTip = .8f,
            )
            val stylus = renderExport(taperedStroke(tip).copy(procreateTaper = taper, inputKind = StrokeInputKind.STYLUS))
            val touch = renderExport(taperedStroke(tip).copy(procreateTaper = taper, inputKind = StrokeInputKind.TOUCH))
            try {
                val edgeX = (stylus.width * .1f).toInt()
                assertTrue("Pressure and Touch taper programs rendered the same start size", visibleHeight(stylus, edgeX) < visibleHeight(touch, edgeX))
                assertNotEquals("Pressure and Touch taper output must remain independent", signature(stylus), signature(touch))
            } finally {
                stylus.recycle()
                touch.recycle()
            }
        }
    }

    private fun taperedStroke(tip: File) = InkStroke(
        points = (0..20).map { index ->
            StrokePoint(.1f + index * .04f, .5f, pressure = .65f, timeMs = index.toLong())
        },
        width = 100f,
        flow = 1f,
        tipAssetPath = tip.absolutePath,
        tipAssetPaths = listOf(tip.absolutePath),
        spacing = .08f,
        sizePressureEnabled = false,
        renderSeed = 787,
    )

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
        400,
    )

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var result: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "procreate-taper-render")
                layout(0, 0, 400, height)
            }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }

    private inline fun withCircularTip(block: (File) -> Unit) {
        val tip = File(context.cacheDir, "procreate-taper-${System.nanoTime()}.png")
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

    private fun visibleHeight(bitmap: Bitmap, x: Int): Int {
        var minimum = bitmap.height
        var maximum = -1
        for (y in bitmap.indicesY()) {
            if (Color.red(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y)) < 250) {
                minimum = minOf(minimum, y)
                maximum = maxOf(maximum, y)
            }
        }
        return if (maximum < minimum) 0 else maximum - minimum + 1
    }

    private fun averageRed(bitmap: Bitmap, x: Int): Int {
        val center = bitmap.height / 2
        var sum = 0L
        var count = 0
        for (sampleX in x - 2..x + 2) for (y in center - 3..center + 3) {
            sum += Color.red(bitmap.getPixel(sampleX.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)))
            count++
        }
        return (sum / count).toInt()
    }

    private fun signature(bitmap: Bitmap): Int = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }.contentHashCode()

    private fun maximumRedDifference(first: Bitmap, second: Bitmap): Int {
        var maximum = 0
        for (y in first.indicesY()) for (x in 0 until first.width) {
            maximum = maxOf(maximum, kotlin.math.abs(Color.red(first.getPixel(x, y)) - Color.red(second.getPixel(x, y))))
        }
        return maximum
    }

    private fun Bitmap.indicesY(): IntRange = 0 until height
}
