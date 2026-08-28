package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Test

class PageCropTransformTest {
    @Test fun `crop retains authoritative sparse layer mask reference`() {
        val reference = MaskTileSurfaceRef("/tmp/mask.v5", "mask", 100, 200, 64, 1, 0, 1)
        val mask = LayerMask(baseVisible = false, surface = reference)
        val projected = PageCropTransform.project(InkPage(
            crop = PageCrop(.2f, .1f, .8f, .7f),
            layers = defaultInkLayers().map { if (it.id == LayerIds.INK) it.copy(mask = mask) else it },
        ))

        assertEquals(reference, projected.layers.first { it.id == LayerIds.INK }.mask?.surface)
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
    fun cropProjectionKeepsFreeformLocalGeometry() {
        val path = listOf(ShapeVertex(0f, .5f), ShapeVertex(.5f, 0f), ShapeVertex(1f, 1f))
        val original = PageElement.Shape(x = .3f, y = .25f, width = 420f, height = 310f, kind = ShapeKind.FREEFORM, path = path, maskAssetPath = "/private/mask.fill-mask.v5")
        val crop = PageCrop(.2f, .1f, .8f, .9f)

        val projected = PageCropTransform.project(InkPage(crop = crop, elements = listOf(original))).elements.single() as PageElement.Shape
        val restored = PageCropTransform.unproject(projected, crop) as PageElement.Shape

        assertEquals(path, projected.path)
        assertEquals(original.x, restored.x, .0001f)
        assertEquals(original.y, restored.y, .0001f)
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.maskAssetPath, restored.maskAssetPath)
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

    @Test
    fun pdfSearchBoundsFollowTheVisibleCropAndClipAtItsEdges() {
        val projected = PageCropTransform.project(
            PdfTextBounds(.1f, .25f, .5f, .55f),
            PageCrop(.2f, .1f, .8f, .7f),
        )!!

        assertEquals(0f, projected.left, .0001f)
        assertEquals(.25f, projected.top, .0001f)
        assertEquals(.5f, projected.right, .0001f)
        assertEquals(.75f, projected.bottom, .0001f)
    }

    @Test fun cropProjectsPdfGotoLinksAndDropsInvisibleSources() {
        val visible = PdfGotoLink(listOf(PdfTextBounds(.1f, .25f, .5f, .55f)), "target")
        val hidden = PdfGotoLink(listOf(PdfTextBounds(.82f, .8f, .9f, .9f)), "target")

        val links = PageCropTransform.project(InkPage(crop = PageCrop(.2f, .1f, .8f, .7f), pdfGotoLinks = listOf(visible, hidden))).pdfGotoLinks

        assertEquals(1, links.size)
        assertEquals(0f, links.single().bounds.single().left, .0001f)
        assertEquals(.75f, links.single().bounds.single().bottom, .0001f)
    }
}
