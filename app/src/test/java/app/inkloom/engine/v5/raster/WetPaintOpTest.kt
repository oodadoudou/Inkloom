package app.inkloom.engine.v5.raster

import app.inkloom.engine.v5.material.BrushTipMask
import app.inkloom.engine.v5.coordinates.DocumentPoint
import app.inkloom.engine.v5.input.InputPhase
import app.inkloom.engine.v5.input.InputSample
import app.inkloom.engine.v5.input.InputSampleBatch
import app.inkloom.engine.v5.input.InputTool
import app.inkloom.engine.v5.stroke.DirectStrokeSession
import app.inkloom.engine.v5.surface.PixelRect
import app.inkloom.engine.v5.surface.PixelTileKey
import app.inkloom.engine.v5.surface.PixelTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WetPaintOpTest {
    @Test fun `wet opacity and flow use their own pressure curves`() {
        val response = app.inkloom.engine.v5.material.PressureResponse(true, .2f,
            listOf(0f to 0f, .5f to .8f, 1f to 1f))
        fun alpha(pressure: Float, opacity: Boolean, flow: Boolean): Int {
            val surface = PixelTileSurface(96, 96)
            val paint = WetPaintOp(surface, WetPaintDefinition(40f, 0xff2050a0.toInt(),
                charge = 1f, attack = .6f, dilution = 0f,
                opacityPressure = if (opacity) response else app.inkloom.engine.v5.material.PressureResponse.CONSTANT,
                flowPressure = if (flow) response else app.inkloom.engine.v5.material.PressureResponse.CONSTANT))
                .begin(PaintOpContext(43L, surface.revision, "wet-pressure")) as WetPaintOp.Session
            val stroke = DirectStrokeSession(43L, 20f)
            paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 48f, 48f).copy(pressure = pressure)))
            paint.finish(stroke.finish(sample(1, InputPhase.UP, 48f, 48f).copy(pressure = pressure)))
            paint.publish().record.close()
            return surface.pixelAt(48, 48) ushr 24
        }
        val fixed = alpha(.2f, false, false)
        assertEquals(fixed, alpha(.9f, false, false))
        val opacity = alpha(.2f, true, false)
        val flow = alpha(.2f, false, true)
        assertTrue(opacity in 1 until fixed)
        assertEquals(opacity, flow)
        assertTrue(alpha(.2f, true, true) < opacity)
        assertTrue(alpha(.8f, true, false) > opacity)
        assertTrue(alpha(.8f, false, true) > flow)
    }

    @Test fun `empty grain never invents pigment on first contact`() {
        val surface = PixelTileSurface(160, 160)
        val emptyGrain = BrushTipMask(4, 4, ByteArray(16))
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(
                diameterPixels = 48f,
                colorArgb = 0xff2040a0.toInt(),
                charge = 1f,
                attack = 1f,
                dilution = 0f,
                tips = listOf(BrushTipMask.solidCircle(16)),
                grain = emptyGrain,
                grainDensity = 1f,
                spacingRatio = 2f,
            ),
        ).begin(PaintOpContext(38L, surface.revision, "wet-empty-grain-tap")) as WetPaintOp.Session
        val stroke = DirectStrokeSession(38L, 24f)

        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 80f, 80f)))
        paint.finish(stroke.finish(sample(1, InputPhase.UP, 84f, 80f)))
        val published = paint.publish()

        assertEquals("Zero authored coverage must remain zero", 0, published.record.changedTileCount)
        assertEquals(0, surface.pixelAt(80, 80))
        published.record.close()
    }

    @Test fun textureGrainModulatesWetPigmentInsideTheTip() {
        val surface = PixelTileSurface(160, 160)
        val stripes = BrushTipMask(
            4,
            4,
            ByteArray(16) { index -> if (index % 4 < 2) 0xff.toByte() else 0 },
        )
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(
                diameterPixels = 64f,
                colorArgb = 0xff2040a0.toInt(),
                charge = 1f,
                attack = 1f,
                dilution = 0f,
                grain = stripes,
                grainDensity = 1f,
                grainScalePercent = 100f,
            ),
        ).begin(PaintOpContext(39L, surface.revision, "wet-texture-grain")) as WetPaintOp.Session
        val stroke = DirectStrokeSession(39L, 24f)

        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 80f, 80f)))
        paint.finish(stroke.finish(sample(1, InputPhase.UP, 80f, 80f)))
        paint.publish().record.close()

        val row = (52..108).map { x -> surface.pixelAt(x, 80) ushr 24 }
        assertTrue("Wet grain must retain opaque material bands", row.maxOrNull()!! > 0)
        assertTrue("Wet grain must cut visible gaps inside the same tip", row.count { it == 0 } > 4)
    }

    @Test fun texturedWetTipControlsPigmentCoverage() {
        val surface = PixelTileSurface(160, 160)
        val leftHalf = BrushTipMask(
            4,
            4,
            ByteArray(16) { index -> if (index % 4 < 2) 0xff.toByte() else 0 },
        )
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(
                diameterPixels = 48f,
                colorArgb = 0xff2040a0.toInt(),
                charge = 1f,
                attack = 1f,
                dilution = 0f,
                tips = listOf(leftHalf),
                spacingRatio = .25f,
            ),
        ).begin(PaintOpContext(40L, surface.revision, "wet-textured-tip")) as WetPaintOp.Session
        val stroke = DirectStrokeSession(40L, 24f)

        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 80f, 80f)))
        paint.finish(stroke.finish(sample(1, InputPhase.UP, 80f, 80f)))
        paint.publish().record.close()

        assertTrue("Opaque half of wet tip must deposit pigment", surface.pixelAt(68, 80) ushr 24 > 0)
        assertEquals("Transparent half of wet tip must remain empty", 0, surface.pixelAt(92, 80) ushr 24)
    }

    @Test fun `picked pigment is carried along the live path`() {
        val surface = PixelTileSurface(256, 128, 64)
        surface.beginWrite("red source").use { tx ->
            tx.editTile(PixelTileKey(0, 0), PixelRect(16, 48, 48, 64)) { pixels, stride ->
                for (y in 48 until 64) for (x in 16 until 48) pixels[y * stride + x] = 0xffff0000.toInt()
            }
            tx.commit().close()
        }
        val stroke = DirectStrokeSession(41L, 8f)
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(28f, 0xff000000.toInt(), dilution = .9f, charge = .1f, pull = 1f),
        ).begin(PaintOpContext(41L, surface.revision, "wet-pull")) as WetPaintOp.Session

        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 32f, 56f)))
        paint.apply(stroke.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE, 180f, 56f)))))

        val preview = paint.previewSnapshot()
        assertTrue((preview.pixelAt(150, 56) ushr 24) > 0)
        assertTrue((preview.pixelAt(150, 56) ushr 16 and 0xff) > (preview.pixelAt(150, 56) and 0xff))
        paint.cancel()
        assertEquals(0, surface.pixelAt(150, 56))
    }

    @Test fun `terminal batch publishes the exact preview without replay`() {
        val surface = PixelTileSurface(256, 128, 64)
        val stroke = DirectStrokeSession(42L, 8f)
        val paint = WetPaintOp(
            surface,
            WetPaintDefinition(24f, 0xff2040a0.toInt(), charge = .8f, attack = .8f),
        ).begin(PaintOpContext(42L, surface.revision, "wet-deposit")) as WetPaintOp.Session
        paint.apply(stroke.begin(sample(0, InputPhase.DOWN, 20f, 64f)))
        paint.apply(stroke.append(InputSampleBatch.owned(listOf(sample(1, InputPhase.MOVE, 180f, 64f)))))
        val beforeUp = paint.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 32, 220, 96))
        paint.finish(stroke.finish(sample(2, InputPhase.UP, 180f, 64f)))
        val afterUp = paint.previewSnapshot().copyPremultipliedPixels(PixelRect(0, 32, 220, 96))
        assertTrue(beforeUp.contentEquals(afterUp))
        val published = paint.publish()
        assertTrue(afterUp.contentEquals(surface.snapshot().copyPremultipliedPixels(PixelRect(0, 32, 220, 96))))
        published.record.close()
    }

    private fun sample(sequence: Long, phase: InputPhase, x: Float, y: Float) = InputSample(
        sequence = sequence,
        phase = phase,
        tool = InputTool.STYLUS,
        point = DocumentPoint(x, y),
        eventTimeNanos = sequence,
        enqueuedTimeNanos = sequence,
        pressure = 1f,
        tiltRadians = 0f,
        orientationRadians = 0f,
    )
}
