package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasGuidesTest {
    @Test fun `stroke constraint chooses the nearest eligible grid axis`() {
        val state = CanvasGuideState(gridSnapEnabled = true, gridSpacing = 100f)
        val constraint = CanvasGuides.constraintAt(state, .103f, .248f, 1_000f, 1_000f, 1_000f, 1_000f, 12f)

        assertEquals(CanvasGuideAxis.X, constraint?.axis)
        assertEquals(CanvasGuideSource.GRID, constraint?.source)
        assertEquals(.1f, constraint?.value ?: 0f, .0001f)
        val constrained = constraint!!.apply(.8f, .9f)
        assertEquals(.1f, constrained.x, .0001f)
        assertEquals(.9f, constrained.y, .0001f)
    }

    @Test fun `point snapping can use grid and alignment guides independently per axis`() {
        val state = CanvasGuideState(gridSnapEnabled = true, gridSpacing = 100f, alignmentGuidesEnabled = true, verticalGuideX = .37f, horizontalGuideY = .62f)
        val snapped = CanvasGuides.snapPoint(state, .365f, .598f, 1_000f, 1_000f, 1_000f, 1_000f, 25f)

        assertEquals(.37f, snapped.x, .0001f)
        assertEquals(.6f, snapped.y, .0001f)
    }

    @Test fun `far points remain free and malformed settings are bounded`() {
        val malformed = CanvasGuideState(true, Float.NaN, true, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        val normalized = malformed.normalized()
        assertEquals(64f, normalized.gridSpacing, .0001f)
        assertEquals(.5f, normalized.verticalGuideX, .0001f)
        assertEquals(.5f, normalized.horizontalGuideY, .0001f)
        assertNull(CanvasGuides.constraintAt(normalized, .23f, .23f, 1_000f, 1_000f, 1_000f, 1_000f, 8f))
    }

    @Test fun `alignment guides hit test and move in normalized space`() {
        val state = CanvasGuideState(alignmentGuidesEnabled = true, verticalGuideX = .3f, horizontalGuideY = .7f)
        assertEquals(CanvasGuideGestureMode.VERTICAL, CanvasGuides.hitTest(state, 305f, 100f, 1_000f, 1_000f, 12f))
        assertEquals(CanvasGuideGestureMode.HORIZONTAL, CanvasGuides.hitTest(state, 800f, 694f, 1_000f, 1_000f, 12f))
        assertEquals(.9f, CanvasGuides.move(state, CanvasGuideGestureMode.VERTICAL, 900f, 200f, 1_000f, 1_000f).verticalGuideX, .0001f)
        assertEquals(.1f, CanvasGuides.move(state, CanvasGuideGestureMode.HORIZONTAL, 500f, 100f, 1_000f, 1_000f).horizontalGuideY, .0001f)
    }

    @Test fun `selection bounds align their nearest edge center or corner`() {
        val state = CanvasGuideState(alignmentGuidesEnabled = true, verticalGuideX = .5f, horizontalGuideY = .5f)
        val translation = CanvasGuides.translationForBounds(state, .2f, .21f, .49f, .41f, 1_000f, 1_000f, 1_000f, 1_000f, 12f)

        assertEquals(.01f, translation.x, .0001f)
        assertEquals(0f, translation.y, .0001f)
    }
}
