package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportMirrorTest {
    private val width = 800f
    private val height = 600f

    @Test fun aMirroredViewShowsThePageLeftSideOnTheRight() {
        val plain = ViewportTransform.pageToView(100f, 300f, width, height, ViewportState(1f, 0f, 0f))
        val mirrored = ViewportTransform.pageToView(100f, 300f, width, height, ViewportState(1f, 0f, 0f, mirrored = true))
        assertEquals(100f, plain.first, .001f)
        assertEquals(700f, mirrored.first, .001f)
        assertEquals(plain.second, mirrored.second, .001f)
    }

    @Test fun mappingRoundTripsWithZoomPanAndRotation() {
        listOf(false, true).forEach { mirror ->
            listOf(0f, 37f, 90f, -135f).forEach { rotation ->
                val viewport = ViewportState(2.25f, 84f, -57f, rotation, mirror)
                listOf(0f to 0f, 123.5f to 456.25f, width to height).forEach { (x, y) ->
                    val view = ViewportTransform.pageToView(x, y, width, height, viewport)
                    val back = ViewportTransform.viewToPage(view.first, view.second, width, height, viewport)
                    assertEquals("x mirror=$mirror rotation=$rotation", x, back.first, .01f)
                    assertEquals("y mirror=$mirror rotation=$rotation", y, back.second, .01f)
                }
            }
        }
    }

    @Test fun sanitizingAndPanningKeepTheMirror() {
        val viewport = ViewportState(1f, 0f, 0f, 0f, mirrored = true)
        assertTrue(ViewportTransform.sanitize(viewport).mirrored)
        assertTrue(ViewportTransform.panByScreen(viewport, 10f, 5f).mirrored)
        assertTrue(ViewportTransform.zoomAroundScreen(viewport, 2f, 10f, 10f).mirrored)
    }
}
