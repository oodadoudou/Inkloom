package app.inkloom.engine.v5.surface

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveStrokeDeltaTileSurfaceTest {
    @Test fun `active source pixels never copy or mutate the committed backdrop`() {
        val document = PixelTileSurface(128, 64, 64, surfaceId = "document")
        document.beginWrite("old ink").run {
            editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels[10 * 64 + 10] = 0xff203040.toInt() }
            commit().close()
        }
        val before = document.snapshot()
        val beforeBytes = before.copyPremultipliedPixels(PixelRect(0, 0, 128, 64))
        val active = ActiveStrokeDeltaTileSurface(document, "new ink", ActiveStrokeDeltaBlend.SOURCE_OVER)

        active.editTile(PixelTileKey(0, 0), PixelRect(38, 18, 43, 23)) { pixels, _ ->
            pixels[20 * 64 + 40] = 0x80100000.toInt()
        }
        val delta = active.previewSnapshot()

        assertEquals("The active surface must not contain old committed ink", 0, delta.pixelAt(10, 10))
        assertNotEquals(0, delta.pixelAt(40, 20))
        assertArrayEquals(beforeBytes, document.snapshot().copyPremultipliedPixels(PixelRect(0, 0, 128, 64)))
        assertEquals(before.revision, document.revision)

        val published = active.publish()
        assertEquals(0xff203040.toInt(), document.pixelAt(10, 10))
        assertNotEquals(0, document.pixelAt(40, 20))
        assertTrue(published.deltaInfo.all { it.dirtyLocalRect.left >= 38 })
        published.close()
    }

    @Test fun `destination out coverage is merged only during publication`() {
        val document = PixelTileSurface(64, 64, 64)
        document.beginWrite("fill").run {
            editTile(PixelTileKey(0, 0), PixelRect(0, 0, 64, 64)) { pixels, _ -> pixels.fill(0xff102030.toInt()) }
            commit().close()
        }
        val active = ActiveStrokeDeltaTileSurface(document, "erase", ActiveStrokeDeltaBlend.DESTINATION_OUT)
        active.editTile(PixelTileKey(0, 0), PixelRect(20, 20, 21, 21)) { pixels, _ ->
            pixels[20 * 64 + 20] = -1
        }

        assertEquals(0xff102030.toInt(), document.pixelAt(20, 20))
        assertEquals(-1, active.previewSnapshot().pixelAt(20, 20))
        val published = active.publish()
        assertEquals(0, document.pixelAt(20, 20))
        assertEquals(0xff102030.toInt(), document.pixelAt(19, 20))
        published.close()
    }
}
