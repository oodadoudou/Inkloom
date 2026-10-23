package app.inkloom.ui

import app.inkloom.document.PageElement
import app.inkloom.document.VectorFrame
import app.inkloom.document.VectorLayerContent
import app.inkloom.document.VectorStroke
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.tools.SelectionAffineTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TransformedVectorsTest {
    private val frame = VectorFrame(200, 100, pixelsPerUnit = 2f)
    private val line = VectorStroke(id = "a", points = listOf(.1f, .5f, 1f, .3f, .5f, 1f), colorArgb = -1, diameter = 10f)
    private val other = VectorStroke(id = "b", points = listOf(.6f, .2f, 1f, .7f, .2f, 1f), colorArgb = -1, diameter = 4f)

    @Test fun selectedLinesMoveAndScaleTheirWidth() {
        val content = VectorLayerContent(listOf(line, other))
        val doubled = SelectionAffineTransform(centerX = 40f, centerY = 50f, scaleX = 2f, scaleY = 2f, translateX = 10f)
        val moved = TransformedVectors.apply(content, setOf("a"), frame, doubled)
        val a = moved.strokes[0]
        // (20 − 40) × 2 + 40 + 10 = 10 px; (60 − 40) × 2 + 40 + 10 = 90 px.
        assertEquals(.05f, a.x(0), 1e-4f); assertEquals(.45f, a.x(1), 1e-4f); assertEquals(.5f, a.y(0), 1e-4f)
        assertEquals(20f, a.diameter, 1e-3f)
        assertSame("unselected lines are untouched", other, moved.strokes[1])
    }

    @Test fun pathBoundsUseTheCentreLineOnly() {
        assertEquals(PixelRect(20, 50, 60, 51), TransformedVectors.pathBounds(VectorLayerContent(listOf(line)), setOf("a"), frame))
    }

    @Test fun textFramesIncludeRotation() {
        val text = PageElement.Text(x = 10f, y = 10f, width = 20f, height = 10f, rotationDegrees = 90f, text = "Hi")
        assertEquals(PixelRect(15, 5, 25, 25), TransformedVectors.frameBounds(listOf(text), 1f, 1f))
    }
}
