package app.inkloom.document

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class BoundedInputTest {
    @Test fun exactLimitAndShortReadsKeepEveryByte() {
        val bytes = ByteArray(32_000) { it.toByte() }
        val stream = object : ByteArrayInputStream(bytes) {
            override fun read(buffer: ByteArray, offset: Int, length: Int) = super.read(buffer, offset, minOf(length, 3))
        }
        assertArrayEquals(bytes, stream.readBoundedBytes(bytes.size, "Too large"))
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf()).readBoundedBytes(0, "Too large"))
    }
    @Test fun overflowAndZeroLengthReadsCannotBypassTheBound() {
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(9)).readBoundedBytes(8, "Too large") }.exceptionOrNull() is IllegalArgumentException)
        val stream = object : InputStream() {
            var remaining = 9
            override fun read() = if (remaining-- > 0) 37 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int) = 0
        }
        assertTrue(runCatching { stream.readBoundedBytes(8, "Too large") }.exceptionOrNull() is IllegalArgumentException)
    }
}
