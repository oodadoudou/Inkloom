package app.inkloom.document

import org.junit.Test
import org.junit.Assert.*
import java.io.*

class RasterFileCodecTest {
    @Test fun tiffRoundTripPreservesStraightAlphaDpiAndRowOrder() {
        val file=File.createTempFile("tiff-roundtrip", ".tif")
        try {
            val rows=listOf(intArrayOf(0x80ff8000.toInt(),0),intArrayOf(0xff112233.toInt(),-1))
            file.outputStream().use { TiffRasterCodec.write(it,2,2,144f) { y,row -> rows[y].copyInto(row) } }
            val image=TiffRasterCodec.read(file).single()
            assertEquals(144f,image.dpi,.001f); assertArrayEquals(rows.flatMap { it.toList() }.toIntArray(),image.pixels)
            RandomAccessFile(file,"rw").use { it.seek(2); it.writeShort(43) }
            assertTrue(runCatching { TiffRasterCodec.read(file) }.isFailure)
        } finally { file.delete() }
    }
    @Test fun psdRasterGroupsClippingMaskAndUnicodeRoundTrip() {
        val file=File.createTempFile("psd-roundtrip", ".psd")
        try {
            val layers=listOf(
                PsdRasterCodec.Layer("漫画組",blend="pass",opacity=128,section=1),
                PsdRasterCodec.Layer("线稿",top=1,left=1,width=2,height=1,blend="mul ",clipped=true,
                    pixels=intArrayOf(0x80ff0000.toInt(),0xff00ff00.toInt()),mask=byteArrayOf(0, -1),maskWidth=2,maskHeight=1),
                PsdRasterCodec.Layer("</Layer group>",section=3))
            file.outputStream().use { PsdRasterCodec.write(it,4,3,layers) { _,row -> row.fill(0) } }
            val image=PsdRasterCodec.read(file)
            assertEquals(4,image.width); assertEquals(3,image.height); assertEquals("漫画組",image.layers[0].name)
            assertEquals("pass",image.layers[0].blend); assertEquals(128,image.layers[0].opacity)
            assertEquals("线稿",image.layers[1].name); assertTrue(image.layers[1].clipped)
            assertArrayEquals(layers[1].pixels,image.layers[1].pixels); assertArrayEquals(layers[1].mask,image.layers[1].mask)
            assertEquals(3,image.layers[2].section)
        } finally { file.delete() }
    }
    @Test fun psdRejectsWrongColorModeAndTruncatedData() {
        val file=File.createTempFile("invalid-psd", ".psd")
        try {
            file.outputStream().use { PsdRasterCodec.write(it,2,2,listOf(PsdRasterCodec.Layer("x",width=1,height=1,pixels=intArrayOf(-1)))) { _,row -> row.fill(-1) } }
            RandomAccessFile(file,"rw").use { it.seek(24); it.writeShort(4) }
            assertTrue(runCatching { PsdRasterCodec.read(file) }.isFailure)
            RandomAccessFile(file,"rw").use { it.setLength(30) }
            assertTrue(runCatching { PsdRasterCodec.read(file) }.isFailure)
        } finally { file.delete() }
    }
}
