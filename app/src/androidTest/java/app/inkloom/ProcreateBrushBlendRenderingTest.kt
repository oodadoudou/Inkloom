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
import app.inkloom.document.ProcreateBrushBlend
import app.inkloom.document.StrokePoint
import app.inkloom.ui.InkCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcreateBrushBlendRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val paper = Color.rgb(128, 160, 192)

    @Test fun wholeStrokeMultiplyCompositesOnceAgainstThePageAndLiveMatchesExport() {
        val normal = renderExport(stroke(ProcreateBrushBlend.NORMAL))
        val multiply = renderExport(stroke(ProcreateBrushBlend.MULTIPLY))
        val live = renderLive(stroke(ProcreateBrushBlend.MULTIPLY), multiply.height)
        try {
            val x = 200
            val y = multiply.height / 2
            val normalPixel = normal.getPixel(x, y)
            val multiplyPixel = multiply.getPixel(x, y)
            assertTrue("normal=${normalPixel.toUInt().toString(16)} multiply=${multiplyPixel.toUInt().toString(16)}", Color.red(multiplyPixel) < Color.red(normalPixel))
            assertTrue("normal=${normalPixel.toUInt().toString(16)} multiply=${multiplyPixel.toUInt().toString(16)}", Color.green(multiplyPixel) < Color.green(normalPixel))
            assertEquals(multiplyPixel, live.getPixel(x, y))
        } finally {
            normal.recycle(); multiply.recycle(); live.recycle()
        }
    }

    @Test fun burntEdgeModeAffectsOnlyTheOuterPigmentBand() {
        val normal = renderExport(stroke(ProcreateBrushBlend.NORMAL, ProcreateBrushBlend.NORMAL, edge = true))
        val multiply = renderExport(stroke(ProcreateBrushBlend.NORMAL, ProcreateBrushBlend.MULTIPLY, edge = true))
        val live = renderLive(stroke(ProcreateBrushBlend.NORMAL, ProcreateBrushBlend.MULTIPLY, edge = true), multiply.height)
        try {
            val centerX = 200
            val centerY = normal.height / 2
            assertEquals(normal.getPixel(centerX, centerY), multiply.getPixel(centerX, centerY))
            val edgeX = centerX + 25
            val normalEdge = normal.getPixel(edgeX, centerY)
            val multiplyEdge = multiply.getPixel(edgeX, centerY)
            assertTrue("normalEdge=${normalEdge.toUInt().toString(16)} multiplyEdge=${multiplyEdge.toUInt().toString(16)}", Color.red(multiplyEdge) < Color.red(normalEdge))
            assertEquals(multiplyEdge, live.getPixel(edgeX, centerY))
        } finally {
            normal.recycle(); multiply.recycle(); live.recycle()
        }
    }

    private fun stroke(blend: Int, burnt: Int = ProcreateBrushBlend.MULTIPLY, edge: Boolean = false) = InkStroke(
        points = listOf(StrokePoint(.5f, .5f)),
        colorArgb = 0xFFFF8040,
        width = 160f,
        flow = 1f,
        opacity = 1f,
        sizePressureEnabled = false,
        procreateBrushBlendMode = blend,
        procreateBurntEdgeBlendMode = burnt,
        waterEdgeEnabled = edge,
        waterEdgeRadius = if (edge) 40f else 0f,
        waterEdgeOpacity = if (edge) 1f else 0f,
        waterEdgeDarkness = 0f,
    )

    private fun renderExport(stroke: InkStroke): Bitmap = DocumentAssetService(context).renderPageBitmap(
        InkPage(background = PageBackground.Paper(paper.toUInt().toLong()), strokes = listOf(stroke)),
        400,
    )

    private fun renderLive(stroke: InkStroke, height: Int): Bitmap {
        lateinit var result: Bitmap
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = InkCanvasView(context).apply {
                pagePaperColor = paper
                setDocumentStrokes(listOf(stroke), "brush-blend-live")
                layout(0, 0, 400, height)
            }
            result = Bitmap.createBitmap(400, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        return result
    }
}
