package app.inkloom.engine.v3.storage

import app.inkloom.engine.v3.surface.MaskTileSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskTileArchiveCodecTest {
    @Test fun sparseImplicitDefaultAndDeviationsRoundTripWithoutPageMaterialization() {
        val source = MaskTileSurface(8192, 8192, tileSize = 128, initialDefaultAlpha = 255)
        source.beginWrite("holes").run {
            setAlpha(13, 17, 0)
            setAlpha(4097, 2049, 96)
            commit().close()
        }

        val encoded = MaskTileArchiveCodec.encode(source.snapshot())
        val restored = MaskTileArchiveCodec.decode(encoded)

        assertEquals(255, restored.defaultAlpha)
        assertEquals(2, restored.tileCount)
        assertEquals(0, restored.alphaAt(13, 17))
        assertEquals(96, restored.alphaAt(4097, 2049))
        assertEquals(255, restored.alphaAt(7000, 7000))
        assertTrue(encoded.size < 4_096)
    }

    @Test fun decoderRejectsTrailingAndTruncatedData() {
        val source = MaskTileSurface(256, 256)
        val valid = MaskTileArchiveCodec.encode(source.snapshot())

        runCatching { MaskTileArchiveCodec.decode(valid + 1) }
            .onSuccess { error("Trailing bytes must be rejected") }
        runCatching { MaskTileArchiveCodec.decode(valid.copyOf(valid.size - 1)) }
            .onSuccess { error("Truncated bytes must be rejected") }
    }
}
