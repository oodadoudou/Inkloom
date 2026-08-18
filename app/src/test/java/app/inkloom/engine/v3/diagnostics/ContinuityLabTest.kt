package app.inkloom.engine.v3.diagnostics

import app.inkloom.document.InkStroke
import app.inkloom.document.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Pins the deterministic brush-continuity contract for the two default families:
 * - the default G Pen (polyline) is geometrically continuous with dense input (overlapping
 *   segments) -- its beaded look is a coverage/compositing issue, not a geometry gap;
 * - sparse fast input produces a real GEOMETRIC_GAP;
 * - the HB Pencil (stamped graphite) is geometrically overlapping at its authored spacing;
 * - a legitimate pressure ramp is never classified as RADIUS_PUMPING.
 */
class ContinuityLabTest {
    private val viewportWidth = 1600f
    private val viewportHeight = 1200f

    private fun gPen(points: List<StrokePoint>) = InkStroke(
        id = "system-g-pen",
        brushId = "system-g-pen",
        points = points,
        width = 6f,
        spacing = .045f,
        sizePressureEnabled = true,
        sizePressureMinimum = .06f,
    )

    private fun hbPencil(points: List<StrokePoint>) = InkStroke(
        id = "system-hb-pencil",
        brushId = "system-hb-pencil",
        points = points,
        width = 7f,
        spacing = .26f,
        flow = .68f,
        sizePressureEnabled = true,
        sizePressureMinimum = .3f,
    )

    private fun denseLine(pressure: Float = .6f) = (0..300).map { index ->
        StrokePoint(.1f + index * .002f, .5f, pressure = pressure, timeMs = (index * 2L).toLong())
    }

    private fun sparseLine() = (0..20).map { index ->
        StrokePoint(.1f + index * .04f, .5f, pressure = .6f, timeMs = (index * 8L).toLong())
    }

    private fun pressureRampLine() = (0..300).map { index ->
        StrokePoint(.1f + index * .002f, .5f, pressure = index / 300f, timeMs = (index * 2L).toLong())
    }

    @Test
    fun denseGPenIsGeometricallyContinuous() {
        val report = ContinuityLab.analyze(gPen(denseLine()), viewportWidth, viewportHeight)
        assertEquals(ContinuityLab.EmissionModel.POLYLINE, report.emissionModel)
        assertTrue("Dense pen input must overlap, not gap (gapRatioMax=${report.gapRatioMax})", report.gapRatioMax < 1f)
        assertFalse("Dense pen must not report GEOMETRIC_GAP", ContinuityLab.Failure.GEOMETRIC_GAP in report.failures)
    }

    @Test
    fun sparseFastPenInputProducesGeometricGap() {
        val report = ContinuityLab.analyze(gPen(sparseLine()), viewportWidth, viewportHeight)
        assertTrue("Sparse input must produce GEOMETRIC_GAP (max=${report.gapRatioMax})", ContinuityLab.Failure.GEOMETRIC_GAP in report.failures)
        assertTrue(report.geometricGapRatio > 0f)
    }

    @Test
    fun hbPencilStampsOverlapAtAuthoredSpacing() {
        val report = ContinuityLab.analyze(hbPencil(denseLine()), viewportWidth, viewportHeight)
        assertEquals(ContinuityLab.EmissionModel.STAMPED, report.emissionModel)
        assertTrue("HB Pencil spacing must overlap stamps (mean gapRatio=${report.gapRatioMean})", report.gapRatioMean < 1f)
        assertFalse("HB Pencil must not report GEOMETRIC_GAP", ContinuityLab.Failure.GEOMETRIC_GAP in report.failures)
    }

    @Test
    fun constantInputDoesNotProduceRadiusPumping() {
        val report = ContinuityLab.analyze(gPen(denseLine(pressure = .6f)), viewportWidth, viewportHeight)
        assertTrue(report.constantInput)
        assertFalse("Constant input must not report RADIUS_PUMPING (cv=${report.radiusCv})", ContinuityLab.Failure.RADIUS_PUMPING in report.failures)
    }

    @Test
    fun pressureRampIsNeverClassifiedAsRadiusPumping() {
        val report = ContinuityLab.analyze(gPen(pressureRampLine()), viewportWidth, viewportHeight)
        assertFalse(report.constantInput)
        assertFalse("A legitimate pressure ramp must never be RADIUS_PUMPING", ContinuityLab.Failure.RADIUS_PUMPING in report.failures)
    }

    @Test
    fun labRunsAcrossTheFullSizeSweep() {
        listOf(1f, 2f, 4f, 8f, 16f, 32f, 64f, 128f, 256f, 512f).forEach { size ->
            val stroke = gPen(denseLine()).copy(width = size)
            val report = ContinuityLab.analyze(stroke, viewportWidth, viewportHeight)
            assertTrue("Size $size must produce deposits", report.depositCount > 1)
            assertTrue("Size $size radius must be positive", report.radiusMeanPx > 0f)
        }
    }

    @Test
    fun curveAndSharpTurnReportedForGeometry() {
        // Arc-length-dense sampling so path steps stay well under the actual dab diameter.
        val curve = (0..1000).map { index ->
            val t = index / 1000f
            StrokePoint(.1f + t * .8f, .5f + .18f * sin(t * (Math.PI * 2).toFloat()), pressure = .6f, timeMs = (index * 2L).toLong())
        }
        val report = ContinuityLab.analyze(gPen(curve), viewportWidth, viewportHeight)
        assertTrue(
            "A dense curve must remain geometrically continuous (max gapRatio=${report.gapRatioMax})",
            report.gapRatioMax < 1f,
        )
    }

    @Test
    fun pressureRampKeepsUniformGapRatioForStampedActualRadiusSpacing() {
        // For the STAMPED path, spacing is diameter * factor (BrushProgram.spacingFunction uses
        // the pressure-aware size.evaluate), so dab gap must track the ACTUAL rendered radius and
        // gap/diameter stays uniform across pressure -- never 'low pressure sparse, high dense'.
        val stroke = hbPencil(pressureRampLine())
        val deposits = ContinuityLab.deposits(stroke, viewportWidth, viewportHeight)
        val ratios = deposits.filter { it.gapToNextPx >= 0f }.map { it.gapRatio }
        assertTrue("Pressure ramp must produce several stamped deposits", ratios.size > 20)
        val mean = ratios.average()
        val cv = ratios.map { (it - mean) * (it - mean) }.average().toFloat().let { kotlin.math.sqrt(it) } / mean
        assertTrue(
            "Stamped actual-radius spacing must keep gap/diameter uniform across pressure (cv=$cv)",
            cv < .25f,
        )
        assertTrue("Stamped deposits must overlap (mean gapRatio=$mean)", mean < 1f)
    }
}
