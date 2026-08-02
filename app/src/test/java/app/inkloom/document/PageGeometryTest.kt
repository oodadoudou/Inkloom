package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {
    @Test fun clockwiseRotationSwapsPageAndTransformsContent() {
        val stroke = InkStroke(points = listOf(StrokePoint(.2f, .3f, orientation = 0f)))
        val image = PageElement.Image(x = .1f, y = .2f, width = 300f, height = 400f, assetPath = "/tmp/image")
        val rotated = PageGeometry.rotateClockwise(InkPage(width = 1000f, height = 2000f, strokes = listOf(stroke), elements = listOf(image)))

        assertEquals(2000f, rotated.width)
        assertEquals(1000f, rotated.height)
        assertEquals(1, rotated.backgroundRotationQuarterTurns)
        assertEquals(.7f, rotated.strokes.single().points.single().x, .0001f)
        assertEquals(.2f, rotated.strokes.single().points.single().y, .0001f)
        val rotatedImage = rotated.elements.single() as PageElement.Image
        assertEquals(.6f, rotatedImage.x, .0001f)
        assertEquals(.1f, rotatedImage.y, .0001f)
        assertEquals(400f, rotatedImage.width)
        assertEquals(300f, rotatedImage.height)
        assertEquals(90f, rotatedImage.rotationDegrees)
    }

    @Test fun fourRotationsReturnToOriginalGeometry() {
        val original = InkPage(
            width = 900f,
            height = 1400f,
            strokes = listOf(InkStroke(points = listOf(StrokePoint(.23f, .71f)))),
            elements = listOf(PageElement.Text(x = .14f, y = .22f, width = 320f, height = 90f, text = "Test")),
        )
        val restored = generateSequence(original, PageGeometry::rotateClockwise).drop(4).first()

        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(0, restored.backgroundRotationQuarterTurns)
        assertEquals(original.strokes.single().points.single().x, restored.strokes.single().points.single().x, .0001f)
        assertEquals(original.strokes.single().points.single().y, restored.strokes.single().points.single().y, .0001f)
        val originalText = original.elements.single() as PageElement.Text
        val restoredText = restored.elements.single() as PageElement.Text
        assertEquals(originalText.x, restoredText.x, .0001f)
        assertEquals(originalText.y, restoredText.y, .0001f)
        assertEquals(originalText.width, restoredText.width)
        assertEquals(originalText.height, restoredText.height)
    }
}
