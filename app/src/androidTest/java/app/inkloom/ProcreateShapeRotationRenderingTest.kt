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
import app.inkloom.document.StrokeInputKind
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateShapeRotationRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun touchRotationFollowsThePathAndMatchesLiveExport() {
        withAsymmetricTip { tip ->
            val fixed = verticalStroke(tip)
            val following = fixed.copy(inputKind = StrokeInputKind.TOUCH, shapeTouchRotation = 1f)
            val fixedBitmap = renderExport(fixed)
            val followingBitmap = renderExport(following)
            val live = renderLive(following, followingBitmap.height)
            try {
                assertNotEquals("Touch Rotation did not rotate asymmetric stamps with the path", signature(fixedBitmap), signature(followingBitmap))
                assertTrue("Following a vertical path should narrow the horizontal stamp extent", darkWidth(followingBitmap) < darkWidth(fixedBitmap) / 2)
                val delta = maximumRedDifference(followingBitmap, live)
                assertTrue("Live/export Touch Rotation diverged: max red delta=$delta", delta <= 3)
            } finally {
                fixedBitmap.recycle()
                followingBitmap.recycle()
                live.recycle()
            }
        }
    }

    @Test
    fun bakedOrientToScreenAdjustmentRotatesTheAuthoredShape() {
        withAsymmetricTip { tip ->
            val canvasAligned = singleStamp(tip)
            val screenAligned = canvasAligned.copy(shapeScreenRotationDegrees = -90f)
            val normal = renderExport(canvasAligned)
            val adjusted = renderExport(screenAligned)
            try {
                assertTrue("Unadjusted shape should retain its wide authored axis", darkWidth(normal) > darkHeight(normal))
                assertTrue("Inverse viewport rotation should turn the shape into screen alignment", darkHeight(adjusted) > darkWidth(adjusted))
            } finally {
                normal.recycle()
                adjusted.recycle()
            }
        }
    }

    private fun verticalStroke(tip: File) = InkStroke(
        points = listOf(StrokePoint(.5f, .25f, timeMs = 1), StrokePoint(.5f, .75f, timeMs = 2)),
        width = 150f,
        flow = 1f,
        tipAssetPath = tip.absolutePath,
        tipAssetPaths = listOf(tip.absolutePath),
        spacing = .3f,
        sizePressureEnabled = false,
        renderSeed = 811,
    )

    private fun singleStamp(tip: File) = verticalStroke(tip).copy(
        points = listOf(StrokePoint(.5f, .5f, timeMs = 1)),
    )

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(), strokes = listOf(stroke)),
        400,
    )

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var bitmap: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = Color.WHITE
                setDocumentStrokes(listOf(stroke), "shape-rotation-render")
                layout(0, 0, 400, height)
            }
            bitmap = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return bitmap
    }

    private inline fun withAsymmetricTip(block: (File) -> Unit) {
        val tip = File(context.cacheDir, "shape-rotation-${System.nanoTime()}.png")
        Bitmap.createBitmap(64, 24, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawRoundRect(1f, 3f, 63f, 21f, 6f, 6f, Paint().apply {
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

    private fun darkWidth(bitmap: Bitmap): Int = darkBounds(bitmap).let { it[2] - it[0] + 1 }
    private fun darkHeight(bitmap: Bitmap): Int = darkBounds(bitmap).let { it[3] - it[1] + 1 }

    private fun darkBounds(bitmap: Bitmap): IntArray {
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.red(bitmap.getPixel(x, y)) < 200) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        return intArrayOf(left, top, right.coerceAtLeast(left), bottom.coerceAtLeast(top))
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
