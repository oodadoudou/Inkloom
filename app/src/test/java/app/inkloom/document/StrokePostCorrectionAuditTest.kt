package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Audits every post-correction mode and pins the pen-up geometry-continuity contract:
 *
 * Default painting must never perform a visibly destructive geometry rewrite at pen-up. The
 * production path supplies a [StrokePostCorrection.maxDisplacementPx] bound, and this test proves:
 *  - with the bound, no point moves more than the bound and the known 37->2 collapse is gone;
 *  - without the bound, the old destructive behavior is pinned as the hazard baseline (so the
 *    bound is proven to change behavior, not silently ignored);
 *  - the screen-space bound helper keeps the visible jump constant across zoom.
 */
class StrokePostCorrectionAuditTest {
    private val pageWidth = 1600f
    private val pageHeight = 2263f
    private val viewWidthPx = 800

    /** The exact zigzag that previously collapsed 37 points -> 2. */
    private fun zigzag() = InkStroke(
        id = "zigzag",
        points = (0..36).map { index ->
            StrokePoint(
                .1f + index * .02f,
                .5f + if (index % 2 == 0) .028f else -.014f,
                pressure = .6f,
                timeMs = (index * 5L).toLong(),
            )
        },
        width = 24f,
        renderSeed = 99,
    )

    private fun rough() = InkStroke(
        id = "rough",
        width = 12f,
        points = listOf(
            StrokePoint(.1f, .5f, velocity = .2f),
            StrokePoint(.3f, .65f, velocity = .2f),
            StrokePoint(.5f, .35f, velocity = .2f),
            StrokePoint(.7f, .64f, velocity = .2f),
            StrokePoint(.9f, .5f, velocity = .2f),
        ),
    )

    private fun wobbly() = InkStroke(
        id = "wobbly",
        width = 8f,
        points = (0..60).map { index ->
            val t = index / 60f
            StrokePoint(.1f + t * .8f, .5f + .012f * sin(t * 14f), pressure = .6f, timeMs = (index * 4L).toLong())
        },
    )

    private val productionBound = StrokePostCorrection.screenBoundMaxDisplacement(
        StrokePostCorrection.POST_CORRECTION_MAX_SCREEN_PX,
        pageWidth,
        viewWidthPx,
        viewportScale = 1f,
    )

    @Test
    fun zeroCorrectionIsAnExactNoOpInEveryMode() {
        listOf(zigzag(), rough(), wobbly()).forEach { stroke ->
            val report = StrokePostCorrection.audit(stroke, amount = 0f, pageWidth = pageWidth, pageHeight = pageHeight)
            assertEquals(stroke.points.size, report.outputPoints)
            assertEquals(0f, report.maxDisplacementPx, .0001f)
        }
    }

    @Test
    fun unboundedPipelinePinsTheDestructiveCollapseBaseline() {
        val report = StrokePostCorrection.audit(zigzag(), amount = .9f, pageWidth = pageWidth, pageHeight = pageHeight)
        assertTrue("Baseline: unbounded SIMPLIFY previously collapsed 37 -> 2 (got ${report.outputPoints})", report.destructive)
        assertEquals(2, report.outputPoints)
    }

    @Test
    fun boundedPipelineNeverCollapsesTheKnownZigzag() {
        val report = StrokePostCorrection.audit(
            zigzag(),
            amount = .9f,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            maxDisplacementPx = productionBound,
        )
        assertFalse("Bounded pipeline must not collapse the zigzag (output=${report.outputPoints})", report.destructive)
        assertTrue("Bounded pipeline must keep meaningful geometry (output=${report.outputPoints})", report.outputPoints >= 4)
    }

    @Test
    fun boundedPipelineKeepsEveryPointWithinTheDisplacementBound() {
        listOf(zigzag(), rough(), wobbly()).forEach { stroke ->
            val report = StrokePostCorrection.audit(
                stroke,
                amount = .9f,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                maxDisplacementPx = productionBound,
            )
            assertTrue(
                "${stroke.id}: max displacement ${report.maxDisplacementPx}px exceeded bound ${productionBound}px",
                report.maxDisplacementPx <= productionBound + .75f,
            )
        }
    }

    @Test
    fun screenBoundHelperKeepsTheVisibleJumpConstantAcrossZoom() {
        listOf(1f, 2f, 8f, 16f).forEach { zoom ->
            val bound = StrokePostCorrection.screenBoundMaxDisplacement(
                StrokePostCorrection.POST_CORRECTION_MAX_SCREEN_PX,
                pageWidth,
                viewWidthPx,
                zoom,
            )
            val visiblePx = bound * (viewWidthPx * zoom / pageWidth)
            assertTrue("Zoom $zoom: visible jump $visiblePx px must stay near 2.5 px", abs(visiblePx - StrokePostCorrection.POST_CORRECTION_MAX_SCREEN_PX) < .01f)
        }
    }

    @Test
    fun reportMeasuresPointsLengthAndBoundingBox() {
        val report = StrokePostCorrection.audit(
            rough(),
            amount = .9f,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            maxDisplacementPx = productionBound,
        )
        assertTrue(report.inputLengthPx > 0f)
        assertTrue(report.outputLengthPx > 0f)
        assertTrue(report.boundingBoxDeltaPx >= 0f)
        assertEquals(rough().points.size, report.inputPoints)
    }
}
