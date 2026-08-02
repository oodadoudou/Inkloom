package app.inkloom.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SutEffectorDecoderTest {
    @Test fun decodesBigEndianHeaderAndMultipleBezierPointLists() {
        val buffer = ByteBuffer.allocate(44 + 12 + 48 + 12 + 32).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(44)
        buffer.putInt(0x1f0)
        buffer.putInt(SutEffector.PRESSURE or SutEffector.TILT or SutEffector.VELOCITY or SutEffector.RANDOM)
        buffer.putInt(25)
        buffer.putInt(35)
        buffer.putInt(45)
        buffer.putInt(55)
        buffer.putInt(0)
        buffer.putInt(60)
        buffer.putInt(44)
        buffer.putInt(500)
        putCurve(buffer, listOf(0.0 to 0.0, .55 to .3, 1.0 to 1.0))
        putCurve(buffer, listOf(0.0 to 1.0, 1.0 to 0.0))

        val decoded = SutEffectorDecoder.decode(buffer.array())!!
        assertEquals(0x1f0, decoded.formatFlags)
        assertEquals(.25f, decoded.pressureMinimum, .0001f)
        assertEquals(.35f, decoded.tiltMinimum, .0001f)
        assertEquals(.45f, decoded.velocityMinimum, .0001f)
        assertEquals(.55f, decoded.randomMinimum, .0001f)
        assertEquals(5f, decoded.velocityScale, .0001f)
        assertEquals(true, decoded.pressureEnabled)
        assertEquals(true, decoded.tiltEnabled)
        assertEquals(true, decoded.velocityEnabled)
        assertEquals(true, decoded.randomEnabled)
        assertEquals(2, decoded.curves.size)
        assertEquals(.55f, decoded.curves.first()[1].x, .0001f)
        assertEquals(.3f, decoded.curves.first()[1].y, .0001f)
        assertEquals(0f, decoded.curves.last().last().y, .0001f)
    }

    @Test fun rejectsTruncatedOrUnknownStructures() {
        assertNull(SutEffectorDecoder.decode(ByteArray(20)))
        val invalid = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN).putInt(12).array()
        assertNull(SutEffectorDecoder.decode(invalid))
    }

    @Test fun retainedCurvesDoNotEnableDisabledSources() {
        val buffer = ByteBuffer.allocate(44 + 12 + 32).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(44)
        buffer.putInt(0xf0)
        buffer.putInt(0)
        repeat(5) { buffer.putInt(0) }
        buffer.putInt(44)
        buffer.putInt(0)
        buffer.putInt(100)
        putCurve(buffer, listOf(0.0 to 0.0, 1.0 to 1.0))

        val decoded = SutEffectorDecoder.decode(buffer.array())!!
        assertEquals(2, decoded.pressureCurve.size)
        assertEquals(false, decoded.pressureEnabled)
        assertEquals(false, decoded.velocityEnabled)
    }

    private fun putCurve(buffer: ByteBuffer, points: List<Pair<Double, Double>>) {
        buffer.putInt(12)
        buffer.putInt(points.size)
        buffer.putInt(16)
        points.forEach { (x, y) -> buffer.putDouble(x); buffer.putDouble(y) }
    }
}
