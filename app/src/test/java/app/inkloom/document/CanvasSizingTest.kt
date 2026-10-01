package app.inkloom.document

import app.inkloom.engine.v5.tools.PixelSurfaceResampling
import app.inkloom.engine.v5.surface.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream

class CanvasSizingTest {
    @Test fun physicalUnitsAndDpiResolveToPixelsAndValidateBudgets() {
        assertEquals(300 to 600, CanvasSizeRequest(25.4f, 50.8f, 300f, CanvasUnit.MILLIMETERS).pixels())
        assertEquals(300 to 600, CanvasSizeRequest(2.54f, 5.08f, 300f, CanvasUnit.CENTIMETERS).pixels())
        assertEquals(25.4f, CanvasSizing.fromPixels(300f, CanvasUnit.MILLIMETERS, 300f), .001f)
        assertThrows(IllegalArgumentException::class.java) { CanvasSizeRequest(9000f, 100f, 300f).pixels() }
        assertEquals(0f to 0f, CanvasSizing.anchorOffset(CanvasAnchor.TOP_LEFT, 100f, 100f, 200f, 300f))
        assertEquals(50f to 100f, CanvasSizing.anchorOffset(CanvasAnchor.CENTER, 100f, 100f, 200f, 300f))
        assertEquals(100f to 200f, CanvasSizing.anchorOffset(CanvasAnchor.BOTTOM_RIGHT, 100f, 100f, 200f, 300f))
    }

    @Test fun sparseCanvasShiftAndCropPreserveIntegerPixelsWithoutFullBitmap() {
        val source = PixelTileSurface(64, 64, 64)
        source.beginWrite("ink").use { it.setPixel(10, 20, 0x80402010.toInt()); it.commit().close() }
        val shifted = PixelSurfaceResampling.resample(source.snapshot(), 128, 96, 1f, 1f, 32f, 16f)
        assertEquals(source.pixelAt(10, 20), shifted.pixelAt(42, 36))
        assertEquals(0, shifted.pixelAt(10, 20))
        val crop = PixelSurfaceResampling.resample(shifted.snapshot(), 64, 64, 1f, 1f, -32f, -16f)
        assertEquals(source.pixelAt(10, 20), crop.pixelAt(10, 20))
        val rotated = PixelSurfaceResampling.project(source.snapshot(), 64, 64, turns = 1)
        assertEquals(source.pixelAt(10, 20), rotated.pixelAt(43, 10))
    }

    @Test fun streamingPngHasValidCrcDensityDimensionsAndLosslessRgba() {
        val bytes = ByteArrayOutputStream()
        StreamingPng.write(bytes, 2, 2, 300f) { y, row -> row[0] = if (y == 0) 0x80ff0000.toInt() else 0xff00ff00.toInt(); row[1] = 0 }
        val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
        val signature = ByteArray(8).also(input::readFully)
        assertEquals(137, signature[0].toInt() and 255)
        val compressed = ByteArrayOutputStream(); var end = false
        while (!end) {
            val size = input.readInt(); val typeBytes = ByteArray(4).also(input::readFully)
            val type = String(typeBytes, Charsets.US_ASCII); val data = ByteArray(size).also(input::readFully)
            val crc = input.readInt(); val expected = CRC32().also { it.update(typeBytes); it.update(data) }.value.toInt()
            assertEquals(expected, crc)
            if (type == "IHDR") { val header = DataInputStream(ByteArrayInputStream(data)); assertEquals(2, header.readInt()); assertEquals(2, header.readInt()) }
            if (type == "IDAT") compressed.write(data)
            if (type == "pHYs") assertEquals(11811, DataInputStream(ByteArrayInputStream(data)).readInt())
            end = type == "IEND"
        }
        val pixels = InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).readBytes()
        assertArrayEquals(byteArrayOf(0, -1, 0, 0, -128, 0, 0, 0, 0, 0, 0, -1, 0, -1, 0, 0, 0, 0), pixels)
    }
}
