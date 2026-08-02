package app.inkloom.importer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SutStartingEndingTargetDecoderTest {
    @Test
    fun decodesEnabledTargetsAndMinimums() {
        val blob = ByteBuffer.allocate(12 + 2 * 12).order(ByteOrder.BIG_ENDIAN)
            .putInt(12).putInt(2).putInt(12)
            .putInt(1001).putInt(1).putInt(25)
            .putInt(1021).putInt(0).putInt(80)
            .array()
        val targets = SutStartingEndingTargetDecoder.decode(blob)
        assertEquals(2, targets.size)
        assertTrue(targets[0].enabled)
        assertEquals(.25f, targets[0].minimumPercent, .0001f)
        assertFalse(targets[1].enabled)
    }

    @Test
    fun rejectsInvalidRecordLayout() {
        val blob = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN).putInt(8).putInt(20).putInt(12).array()
        assertTrue(SutStartingEndingTargetDecoder.decode(blob).isEmpty())
    }
}
