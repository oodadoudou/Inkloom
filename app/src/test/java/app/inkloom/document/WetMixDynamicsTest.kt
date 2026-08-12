package app.inkloom.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WetMixDynamicsTest {
    @Test fun dryBrushKeepsFullAlpha() {
        assertEquals(1f, WetMixDynamics.alphaMultiplier(false, 1f, 0f), .0001f)
    }

    @Test fun paintDensityAndAmountOfPaintStrengthenDrawingColor() {
        val unloaded = WetMixDynamics.alphaMultiplier(true, 0f, 0f)
        val loaded = WetMixDynamics.alphaMultiplier(true, 0f, 1f)
        val mixed = WetMixDynamics.alphaMultiplier(true, 1f, 1f)

        assertTrue(loaded > unloaded)
        assertTrue(mixed > loaded)
        assertEquals(.9f, mixed, .0001f)
    }

    @Test fun importedPressureDynamicsScaleColorAndPaintMixPerPoint() {
        val stroke = InkStroke(
            points = emptyList(),
            watercolor = true,
            colorMix = .8f,
            colorMixPressureEnabled = true,
            colorMixPressureMinimum = .25f,
            paintMix = .6f,
            paintMixPressureEnabled = true,
            paintMixPressureMinimum = .5f,
        )

        val light = StrokePoint(0f, 0f, pressure = 0f)
        val heavy = StrokePoint(0f, 0f, pressure = 1f)
        assertEquals(.2f, WetMixDynamics.colorMix(stroke, light), .0001f)
        assertEquals(.3f, WetMixDynamics.paintMix(stroke, light), .0001f)
        assertEquals(.8f, WetMixDynamics.colorMix(stroke, heavy), .0001f)
        assertEquals(.6f, WetMixDynamics.paintMix(stroke, heavy), .0001f)
        assertTrue(WetMixDynamics.alphaMultiplier(stroke, light) < WetMixDynamics.alphaMultiplier(stroke, heavy))
    }

    @Test fun procreateChargeDepletesByPhysicalTravelAndRechargesOnNewStroke() {
        val lowCharge = InkStroke(points = emptyList(), width = 40f, procreateWetMix = ProcreateWetMix(dilution = .8f, charge = .1f, attack = .8f))
        val highCharge = lowCharge.copy(procreateWetMix = lowCharge.procreateWetMix.copy(charge = .9f))
        val start = StrokePoint(.1f, .5f, pathDistance = 0f)
        val travelled = StrokePoint(.8f, .5f, pathDistance = .2f)

        assertEquals(1f, WetMixDynamics.reservoir(lowCharge, start), .0001f)
        assertTrue(WetMixDynamics.reservoir(lowCharge, travelled) < WetMixDynamics.reservoir(highCharge, travelled))
        assertTrue(WetMixDynamics.alphaMultiplier(lowCharge, travelled) < WetMixDynamics.alphaMultiplier(highCharge, travelled))
    }

    @Test fun wetnessAndBlurJitterAreDeterministicForAStamp() {
        val stroke = InkStroke(
            id = "deterministic-wet",
            renderSeed = 72,
            points = emptyList(),
            width = 32f,
            spacing = .1f,
            procreateWetMix = ProcreateWetMix(dilution = .45f, wetnessJitter = .4f, blur = .2f, blurJitter = .6f),
        )
        val point = StrokePoint(.4f, .5f, pathDistance = .12f)
        assertEquals(WetMixDynamics.effectiveDilution(stroke, point), WetMixDynamics.effectiveDilution(stroke, point), 0f)
        assertEquals(WetMixDynamics.blurAmount(stroke, point), WetMixDynamics.blurAmount(stroke, point), 0f)
        assertTrue(WetMixDynamics.blurAmount(stroke, point) in .2f..0.8f)
    }

    @Test fun attackUsesPressureToControlAdheringShape() {
        val stroke = InkStroke(points = emptyList(), procreateWetMix = ProcreateWetMix(attack = 1f, charge = 1f))
        assertTrue(WetMixDynamics.sizeMultiplier(stroke, StrokePoint(0f, 0f, pressure = 0f)) < WetMixDynamics.sizeMultiplier(stroke, StrokePoint(0f, 0f, pressure = 1f)))
    }
}
