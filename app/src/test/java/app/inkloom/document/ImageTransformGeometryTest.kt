package app.inkloom.document

import org.junit.Assert.*
import org.junit.Test

class ImageTransformGeometryTest {
    private val image = PageElement.Image(x = .2f, y = .3f, width = 320f, height = 170f,
        rotationDegrees = 37f, assetPath = "original.png")

    @Test fun everyRotatedHandleKeepsItsOppositeAnchor() {
        ImageTransformMode.entries.forEach { mode ->
            ImageTransformHandle.entries.filter { it !in listOf(ImageTransformHandle.MOVE, ImageTransformHandle.ROTATE) }.forEach { handle ->
                val anchor = ImageTransformGeometry.point(image, 1200f, 1600f, 1f - handle.fx, 1f - handle.fy)
                val start = ImageTransformGeometry.point(image, 1200f, 1600f, handle.fx, handle.fy)
                val result = ImageTransformGeometry.drag(image, 1200f, 1600f, handle, start.first, start.second,
                    start.first + 81f, start.second + 42f, mode)
                val after = ImageTransformGeometry.point(result, 1200f, 1600f, 1f - handle.fx, 1f - handle.fy)
                assertEquals("$mode $handle x", anchor.first, after.first, .001f)
                assertEquals("$mode $handle y", anchor.second, after.second, .001f)
                if (mode == ImageTransformMode.UNIFORM) assertEquals(image.width / image.height, result.width / result.height, .0001f)
                assertEquals(image.assetPath, result.assetPath)
                assertEquals(image.crop, result.crop)
            }
        }
    }

    @Test fun movementUsesBothPageAxesAndRotationKeepsCenter() {
        val moved = ImageTransformGeometry.drag(image, 1200f, 1600f, ImageTransformHandle.MOVE,
            100f, 200f, 220f, 520f, ImageTransformMode.FREEFORM)
        assertEquals(.3f, moved.x, .0001f); assertEquals(.5f, moved.y, .0001f)
        val center = ImageTransformGeometry.point(image, 1200f, 1600f, .5f, .5f)
        val rotated = ImageTransformGeometry.drag(image, 1200f, 1600f, ImageTransformHandle.ROTATE,
            center.first + 100f, center.second, center.first, center.second + 100f, ImageTransformMode.FREEFORM)
        assertEquals(127f, rotated.rotationDegrees, .001f)
        assertEquals(image.x, rotated.x); assertEquals(image.y, rotated.y)
    }
}
