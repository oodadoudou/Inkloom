package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushCursorGeometryTest {
    @Test fun v5StampFootprintMatchesTheRasterDabAspectAndRoundness() {
        val portrait = BrushCursorGeometry.stampFootprint(160f, 40, 100, 1f)
        val landscapeCompressed = BrushCursorGeometry.stampFootprint(160f, 100, 40, .5f)

        assertEquals(64f, portrait.width, .001f)
        assertEquals(160f, portrait.height, .001f)
        assertEquals(160f, landscapeCompressed.width, .001f)
        assertEquals(32f, landscapeCompressed.height, .001f)
    }

    @Test fun cursorPreservesTheDecodedTipAspectRatio() {
        val footprint = BrushCursorGeometry.footprint(
            diameter = 160f,
            tipWidth = 40,
            tipHeight = 100,
            thickness = 1f,
            thicknessVertical = false,
        )

        assertEquals(64f, footprint.width, .001f)
        assertEquals(160f, footprint.height, .001f)
    }

    @Test fun cursorAppliesAuthoredThicknessAlongTheSameAxisAsTheRenderer() {
        val horizontal = BrushCursorGeometry.footprint(100f, 100, 200, .5f, false)
        val vertical = BrushCursorGeometry.footprint(100f, 100, 200, .5f, true)

        assertEquals(25f, horizontal.width, .001f)
        assertEquals(100f, horizontal.height, .001f)
        assertEquals(50f, vertical.width, .001f)
        assertEquals(50f, vertical.height, .001f)
    }

    @Test fun fixedTipDirectionRotatesWithCanvasUnlessAuthoredForScreen() {
        assertEquals(67f, BrushCursorGeometry.fixedScreenRotation(30f, 37f, orientToScreen = false), .001f)
        assertEquals(30f, BrushCursorGeometry.fixedScreenRotation(30f, 37f, orientToScreen = true), .001f)
        assertEquals(-170f, BrushCursorGeometry.fixedScreenRotation(170f, 20f, orientToScreen = false), .001f)
    }

    @Test fun ignoresSubpixelDirectionNoise() {
        assertFalse(BrushCursorGeometry.hasMeaningfulDirection(.1f, -.2f))
        assertTrue(BrushCursorGeometry.hasMeaningfulDirection(.3f, 0f))
    }
}
