package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageCropTransformTest {
    @Test
    fun projectAndUnprojectPreserveStrokeCoordinates() {
        val crop = PageCrop(.2f, .1f, .8f, .9f)
        val original = InkStroke(points = listOf(StrokePoint(.35f, .5f), StrokePoint(.75f, .8f)))
        val projected = PageCropTransform.project(InkPage(crop = crop, strokes = listOf(original))).strokes.single()
        val restored = PageCropTransform.unproject(projected, crop)

        assertEquals(.25f, projected.points.first().x, .0001f)
        assertEquals(.5f, projected.points.first().y, .0001f)
        assertEquals(original.points.first().x, restored.points.first().x, .0001f)
        assertEquals(original.points.last().y, restored.points.last().y, .0001f)
    }

    @Test
    fun projectPreservesPhysicalObjectSizeAndChangesVisiblePageSize() {
        val image = PageElement.Image(x = .25f, y = .2f, width = 400f, height = 300f, assetPath = "asset")
        val projected = PageCropTransform.project(InkPage(width = 1600f, height = 2000f, crop = PageCrop(.2f, .1f, .7f, .9f), elements = listOf(image)))
        val result = projected.elements.single() as PageElement.Image

        assertEquals(800f, projected.width, .001f)
        assertEquals(1600f, projected.height, .001f)
        assertEquals(400f, result.width, .001f)
        assertEquals(.1f, result.x, .0001f)
        assertEquals(.125f, result.y, .0001f)
    }

    @Test
    fun clockwiseRotationMapsCropEdges() {
        val rotated = PageCropTransform.rotateClockwise(PageCrop(.1f, .2f, .7f, .8f))

        assertEquals(.2f, rotated.left, .0001f)
        assertEquals(.1f, rotated.top, .0001f)
        assertEquals(.8f, rotated.right, .0001f)
        assertEquals(.7f, rotated.bottom, .0001f)
    }

    @Test
    fun invalidCropIsSanitizedToMinimumArea() {
        val crop = PageCropTransform.normalized(PageCrop(.99f, -.4f, .2f, .01f))

        assertEquals(.95f, crop.left, .0001f)
        assertEquals(0f, crop.top, .0001f)
        assertEquals(1f, crop.right, .0001f)
        assertEquals(.05f, crop.bottom, .0001f)
    }
}
