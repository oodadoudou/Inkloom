package app.inkloom.document

import app.inkloom.engine.v5.tools.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StructuredDeformationTest {
    private val text = PageElement.Text(id = "text", x = .2f, y = .3f, width = 20f, height = 12f,
        rotationDegrees = 20f, text = "Editable", textSize = 8f)

    @Test fun projectiveGeometryRetainsTextAndSurvivesSerialization() {
        val mapping = SelectionProjectiveMapping(0f, 0f, 100f, 100f,
            listOf(0f to 0f, 90f to 10f, 100f to 100f, 10f to 90f))
        val result = StructuredElementTransform.map(text, mapping, 100, 100, 100f, 100f) as PageElement.Text
        assertEquals(text.text, result.text); assertEquals(text.id, result.id)
        assertNotNull(result.deformation)
        val source = text.contentPoint(.5f, .5f, 100f, 100f)
        val expected = mapping.forward(source.x, source.y)
        val actual = result.contentPoint(.5f, .5f, 100f, 100f)
        assertEquals(expected.first, actual.x, .001f); assertEquals(expected.second, actual.y, .001f)
        val restored = Json.decodeFromString<PageElement>(Json.encodeToString<PageElement>(result))
        assertEquals(result, restored)
        assertEquals("Changed", (restored as PageElement.Text).copy(text = "Changed").text)
    }

    @Test fun repeatedWarpAndAffineTransformsComposeWithoutReplacingImageAssets() {
        val image = PageElement.Image(x = .2f, y = .2f, width = 40f, height = 40f, assetPath = "original.png")
        val nodes = (0..3).flatMap { y -> (0..3).map { x -> x * 100f / 3 to (y * 100f / 3 + if (x == 1 && y == 1) 8f else 0f) } }
        val mapping = SelectionMeshMapping(0f, 0f, 100f, 100f, nodes)
        val warped = StructuredElementTransform.map(image, mapping, 100, 100, 100f, 100f)
        val move = SelectionAffineTransform(50f, 50f, 1f, 1f, 0f, 4f, 3f)
        val moved = StructuredElementTransform.map(warped, move, 100, 100, 100f, 100f) as PageElement.Image
        assertEquals(image.assetPath, moved.assetPath)
        val before = warped.contentPoint(.5f, .5f, 100f, 100f)
        val after = moved.contentPoint(.5f, .5f, 100f, 100f)
        assertEquals(before.x + 4, after.x, .001f); assertEquals(before.y + 3, after.y, .001f)
        assertTrue(moved.displayBounds(100f, 100f).all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test fun mirrorAndRotatedNonuniformScaleRetainCorrectGeometry() {
        val mirror = SelectionAffineTransform(50f, 50f, -1f, 1f, 0f, 0f, 0f)
        val result = StructuredElementTransform.map(text, mirror, 100, 100, 100f, 100f)
        for ((u, v) in listOf(0f to 0f, 1f to 1f, .5f to .5f)) {
            val before = text.contentPoint(u, v, 100f, 100f)
            val after = result.contentPoint(u, v, 100f, 100f)
            assertEquals(100 - before.x, after.x, .001f); assertEquals(before.y, after.y, .001f)
        }
    }
}
