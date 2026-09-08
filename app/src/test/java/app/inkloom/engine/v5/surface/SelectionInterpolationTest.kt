package app.inkloom.engine.v5.surface

import app.inkloom.engine.v5.tools.*
import org.junit.Assert.*
import org.junit.Test

class SelectionInterpolationTest {
    private fun mask(alpha: Int = 255) = MaskTileSurface(256, 128, 64).apply {
        beginWrite("selection").use { it.setAlpha(32, 32, alpha); it.commit().close() }
    }.snapshot()

    @Test fun everySamplerLeavesIdentityAndCancelledSoftSelectionsByteExact() {
        for (format in PixelTileFormat.entries) for (interpolation in SelectionInterpolation.entries) {
            val surface = PixelTileSurface(256, 128, 64, pixelFormat = format)
            surface.beginWrite("semitransparent source").use { write ->
                if (format.isHighPrecision) write.setLinearPixel(32, 32, LinearRgba(2.5f, -.2f, .1234f, .63f))
                else write.setPixel(32, 32, 0x80704020.toInt())
                write.commit().close()
            }
            val original = surface.snapshot()
            RasterSelectionTransformSession(surface, mask(123)).use { session ->
                session.update(SelectionAffineTransform(32f, 32f), interpolation)
                if (format.isHighPrecision) assertEquals(original.packedF16At(32, 32), surface.snapshot().packedF16At(32, 32))
                else assertEquals(original.pixelAt(32, 32), surface.pixelAt(32, 32))
                session.update(SelectionAffineTransform(32f, 32f, scaleX = 1.7f, rotationDegrees = 27f, translateX = 80f), interpolation)
                session.cancel()
            }
            val bounds = PixelRect(0, 0, 256, 128)
            if (format.isHighPrecision) assertArrayEquals(original.copyPremultipliedF16(bounds), surface.snapshot().copyPremultipliedF16(bounds))
            else assertArrayEquals(original.copyPremultipliedPixels(bounds), surface.snapshot().copyPremultipliedPixels(bounds))
        }
    }

    @Test fun bilinearFiltersColorAndMaskAcrossATileBoundaryWithoutWhiteFringes() {
        val surface = PixelTileSurface(256, 128, 64)
        surface.beginWrite("red").use { it.setPixel(32, 32, 0xffff0000.toInt()); it.commit().close() }
        RasterSelectionTransformSession(surface, mask()).use { session ->
            val result = session.update(SelectionAffineTransform(32f, 32f, translateX = 95.5f), SelectionInterpolation.BILINEAR)
            assertEquals(0x80800000.toInt(), surface.pixelAt(127, 32))
            assertEquals(0x80800000.toInt(), surface.pixelAt(128, 32))
            assertEquals(128, result.selection.alphaAt(127, 32))
            assertEquals(128, result.selection.alphaAt(128, 32))
            assertEquals(0, surface.pixelAt(32, 32))
        }
    }

    @Test fun extendedF16ColorSurvivesAllSamplersAndOneUndoRedo() {
        for (interpolation in SelectionInterpolation.entries) {
            val surface = PixelTileSurface(256, 128, 64, pixelFormat = PixelTileFormat.PREMULTIPLIED_LINEAR_EXTENDED_SRGB_F16)
            surface.beginWrite("HDR source").use { it.setLinearPixel(32, 32, LinearRgba(3.1f, -.25f, .1234f, .7f)); it.commit().close() }
            val original = surface.snapshot().packedF16At(32, 32)
            RasterSelectionTransformSession(surface, mask()).use { session ->
                session.update(SelectionAffineTransform(32f, 32f, translateX = 95f), interpolation)
                assertEquals(original, surface.snapshot().packedF16At(127, 32))
                val result = requireNotNull(session.commit())
                val record = requireNotNull(result.transactionRecord)
                surface.apply(record, PixelTileSurface.HistoryDirection.UNDO)
                assertEquals(original, surface.snapshot().packedF16At(32, 32))
                surface.apply(record, PixelTileSurface.HistoryDirection.REDO)
                assertEquals(original, surface.snapshot().packedF16At(127, 32))
                record.close()
            }
        }
    }

    @Test fun bicubicKeepsPremultipliedChannelsBoundedAndDiffersFromBilinear() {
        val surface = PixelTileSurface(256, 128, 64)
        surface.beginWrite("red").use { it.setPixel(32, 32, 0xffff0000.toInt()); it.commit().close() }
        RasterSelectionTransformSession(surface, mask()).use { session ->
            val transform = SelectionAffineTransform(32f, 32f, translateX = 50.25f)
            session.update(transform, SelectionInterpolation.BILINEAR)
            val bilinear = surface.pixelAt(82, 32)
            session.update(transform, SelectionInterpolation.BICUBIC)
            assertNotEquals(bilinear, surface.pixelAt(82, 32))
            for (x in 78..87) {
                val pixel = surface.pixelAt(x, 32)
                assertEquals(pixel ushr 24, pixel ushr 16 and 255)
                assertEquals(0, pixel and 65535)
            }
        }
    }
}
